package com.auth.microservice.application.handler;

import com.auth.microservice.application.command.AuthenticateCommand;
import com.auth.microservice.application.result.AuthenticationResult;
import com.auth.microservice.domain.exception.AuthenticationException;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.domain.port.SessionRepository;
import com.auth.microservice.domain.service.JWTService;
import com.auth.microservice.domain.service.PasswordService;
import com.auth.microservice.domain.service.GeoLocationService;
import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class AuthCommandHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionRepository sessionRepository;
    
    @Mock
    private PasswordService passwordService;
    
    @Mock
    private JWTService jwtService;

    @Mock
    private GeoLocationService geoLocationService;

    private AuthCommandHandler handler;
    private User testUser;
    private Email testEmail;

    @BeforeEach
    void setUp() {
        handler = new AuthCommandHandler(userRepository, sessionRepository, passwordService, jwtService, geoLocationService);
        
        testEmail = new Email("test@example.com");
        testUser = new User(
            UUID.randomUUID(),
            "testuser",
            testEmail,
            "hashedPassword",
            "John",
            "Doe",
            true,
            OffsetDateTime.now(),
            OffsetDateTime.now()
        );
        
        // Add a test role with permissions
        Role testRole = new Role(UUID.randomUUID(), "USER", "Standard user role", OffsetDateTime.now());
        Permission testPermission = new Permission(UUID.randomUUID(), "READ_PROFILE", "users", "read", "Read user profile");
        testRole.addPermission(testPermission);
        testUser.addRole(testRole);
    }

    @Test
    void handle_AuthenticateCommand_Success(VertxTestContext testContext) {
        // Given
        AuthenticateCommand command = new AuthenticateCommand(
            "test@example.com", 
            "password123", 
            "192.168.1.1", 
            "Mozilla/5.0"
        );
        
        JWTService.TokenPair tokenPair = new JWTService.TokenPair(
            "accessToken",
            "refreshToken",
            OffsetDateTime.now().plusHours(1),
            OffsetDateTime.now().plusDays(7)
        );

        when(userRepository.findByEmailWithRoles(any(Email.class)))
            .thenReturn(Future.succeededFuture(Optional.of(testUser)));
        when(passwordService.verifyPassword("password123", "hashedPassword"))
            .thenReturn(true);
        when(jwtService.generateTokenPair(anyString(), anyString(), any(Set.class)))
            .thenReturn(tokenPair);
        when(geoLocationService.getCountryByIp(anyString()))
            .thenReturn(Future.succeededFuture("US"));
        when(sessionRepository.save(any()))
            .thenReturn(Future.succeededFuture(null));

        // When
        Future<AuthenticationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(authResult -> testContext.verify(() -> {
            assertTrue(authResult.isSuccess());
            assertEquals("Authentication successful", authResult.getMessage());
            assertNotNull(authResult.getUser());
            assertNotNull(authResult.getTokenPair());
            assertEquals(testUser.getId(), authResult.getUser().getId());
            assertEquals("accessToken", authResult.getTokenPair().accessToken());
            
            verify(userRepository).findByEmailWithRoles(any(Email.class));
            verify(passwordService).verifyPassword("password123", "hashedPassword");
            verify(jwtService).generateTokenPair(
                eq(testUser.getId().toString()),
                eq(testUser.getEmail().getValue()),
                any(Set.class)
            );
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_AuthenticateCommand_UserNotFound(VertxTestContext testContext) {
        // Given
        AuthenticateCommand command = new AuthenticateCommand(
            "nonexistent@example.com", 
            "password123", 
            "192.168.1.1", 
            "Mozilla/5.0"
        );

        when(userRepository.findByEmailWithRoles(any(Email.class)))
            .thenReturn(Future.succeededFuture(Optional.empty()));

        // When
        Future<AuthenticationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(authResult -> testContext.verify(() -> {
            assertFalse(authResult.isSuccess());
            assertEquals("Invalid credentials", authResult.getMessage());
            assertNull(authResult.getUser());
            assertNull(authResult.getTokenPair());
            
            verify(userRepository).findByEmailWithRoles(any(Email.class));
            verifyNoInteractions(passwordService);
            verifyNoInteractions(jwtService);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_AuthenticateCommand_InactiveUser(VertxTestContext testContext) {
        // Given
        testUser.deactivate();
        AuthenticateCommand command = new AuthenticateCommand(
            "test@example.com", 
            "password123", 
            "192.168.1.1", 
            "Mozilla/5.0"
        );

        when(userRepository.findByEmailWithRoles(any(Email.class)))
            .thenReturn(Future.succeededFuture(Optional.of(testUser)));

        // When
        Future<AuthenticationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(authResult -> testContext.verify(() -> {
            assertFalse(authResult.isSuccess());
            assertEquals("Account is inactive", authResult.getMessage());
            assertNull(authResult.getUser());
            assertNull(authResult.getTokenPair());
            
            verify(userRepository).findByEmailWithRoles(any(Email.class));
            verifyNoInteractions(passwordService);
            verifyNoInteractions(jwtService);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_AuthenticateCommand_InvalidPassword(VertxTestContext testContext) {
        // Given
        AuthenticateCommand command = new AuthenticateCommand(
            "test@example.com", 
            "wrongpassword", 
            "192.168.1.1", 
            "Mozilla/5.0"
        );

        when(userRepository.findByEmailWithRoles(any(Email.class)))
            .thenReturn(Future.succeededFuture(Optional.of(testUser)));
        when(passwordService.verifyPassword("wrongpassword", "hashedPassword"))
            .thenReturn(false);

        // When
        Future<AuthenticationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(authResult -> testContext.verify(() -> {
            assertFalse(authResult.isSuccess());
            assertEquals("Invalid credentials", authResult.getMessage());
            assertNull(authResult.getUser());
            assertNull(authResult.getTokenPair());
            
            verify(userRepository).findByEmailWithRoles(any(Email.class));
            verify(passwordService).verifyPassword("wrongpassword", "hashedPassword");
            verifyNoInteractions(jwtService);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_AuthenticateCommand_InvalidEmailFormat(VertxTestContext testContext) {
        // Given
        AuthenticateCommand command = new AuthenticateCommand(
            "invalid-email", 
            "password123", 
            "192.168.1.1", 
            "Mozilla/5.0"
        );

        // When
        Future<AuthenticationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.failing(throwable -> testContext.verify(() -> {
            assertTrue(throwable instanceof AuthenticationException);
            assertEquals("Authentication failed", throwable.getMessage());
            assertTrue(throwable.getCause() instanceof IllegalArgumentException);
            
            verifyNoInteractions(userRepository);
            verifyNoInteractions(passwordService);
            verifyNoInteractions(jwtService);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_AuthenticateCommand_RepositoryFailure(VertxTestContext testContext) {
        // Given
        AuthenticateCommand command = new AuthenticateCommand(
            "test@example.com", 
            "password123", 
            "192.168.1.1", 
            "Mozilla/5.0"
        );

        when(userRepository.findByEmailWithRoles(any(Email.class)))
            .thenReturn(Future.failedFuture(new RuntimeException("Database error")));

        // When
        Future<AuthenticationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.failing(throwable -> testContext.verify(() -> {
            assertTrue(throwable instanceof AuthenticationException);
            assertEquals("Authentication failed", throwable.getMessage());
            
            verify(userRepository).findByEmailWithRoles(any(Email.class));
            verifyNoInteractions(passwordService);
            verifyNoInteractions(jwtService);
            
            testContext.completeNow();
        })));
    }

    @Test
    void getCommandType_ReturnsCorrectType() {
        // When
        Class<AuthenticateCommand> commandType = handler.getCommandType();

        // Then
        assertEquals(AuthenticateCommand.class, commandType);
    }
}