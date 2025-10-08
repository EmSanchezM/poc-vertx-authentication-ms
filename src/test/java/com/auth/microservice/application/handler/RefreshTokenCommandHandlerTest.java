package com.auth.microservice.application.handler;

import com.auth.microservice.application.command.RefreshTokenCommand;
import com.auth.microservice.application.result.AuthenticationResult;
import com.auth.microservice.domain.exception.InvalidTokenException;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.domain.port.SessionRepository;
import com.auth.microservice.domain.service.JWTService;
import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class RefreshTokenCommandHandlerTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private JWTService jwtService;
    
    @Mock
    private SessionRepository sessionRepository;

    private RefreshTokenCommandHandler handler;
    private User testUser;
    private Email testEmail;

    @BeforeEach
    void setUp() {
        handler = new RefreshTokenCommandHandler(userRepository, sessionRepository, jwtService);
        
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
    void handle_RefreshTokenCommand_Success(VertxTestContext testContext) {
        // Given
        RefreshTokenCommand command = new RefreshTokenCommand(
            "validRefreshToken",
            "192.168.1.1",
            "Mozilla/5.0"
        );
        
        JWTService.TokenValidationResult validationResult = new JWTService.TokenValidationResult(
            true,
            "Token is valid",
            Map.of("userId", testUser.getId().toString(), "email", testUser.getEmail().getValue())
        );
        
        JWTService.TokenPair newTokenPair = new JWTService.TokenPair(
            "newAccessToken",
            "newRefreshToken",
            OffsetDateTime.now().plusHours(1),
            OffsetDateTime.now().plusDays(7)
        );

        when(jwtService.validateToken("validRefreshToken"))
            .thenReturn(validationResult);
        when(jwtService.extractUserId("validRefreshToken"))
            .thenReturn(Optional.of(testUser.getId().toString()));
        when(jwtService.extractUserEmail("validRefreshToken"))
            .thenReturn(Optional.of(testUser.getEmail().getValue()));
        when(userRepository.findByEmailWithRoles(any(Email.class)))
            .thenReturn(Future.succeededFuture(Optional.of(testUser)));
        when(jwtService.generateTokenPair(anyString(), anyString(), any(Set.class)))
            .thenReturn(newTokenPair);

        // When
        Future<AuthenticationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(authResult -> testContext.verify(() -> {
            assertTrue(authResult.isSuccess());
            assertEquals("Authentication successful", authResult.getMessage());
            assertNotNull(authResult.getUser());
            assertNotNull(authResult.getTokenPair());
            assertEquals(testUser.getId(), authResult.getUser().getId());
            assertEquals("newAccessToken", authResult.getTokenPair().accessToken());
            
            verify(jwtService).validateToken("validRefreshToken");
            verify(jwtService).extractUserId("validRefreshToken");
            verify(jwtService).extractUserEmail("validRefreshToken");
            verify(userRepository).findByEmailWithRoles(any(Email.class));
            verify(jwtService).generateTokenPair(
                eq(testUser.getId().toString()),
                eq(testUser.getEmail().getValue()),
                any(Set.class)
            );
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_RefreshTokenCommand_InvalidToken(VertxTestContext testContext) {
        // Given
        RefreshTokenCommand command = new RefreshTokenCommand(
            "invalidRefreshToken",
            "192.168.1.1",
            "Mozilla/5.0"
        );
        
        JWTService.TokenValidationResult validationResult = new JWTService.TokenValidationResult(
            false,
            "Token is invalid",
            Map.of()
        );

        when(jwtService.validateToken("invalidRefreshToken"))
            .thenReturn(validationResult);

        // When
        Future<AuthenticationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(authResult -> testContext.verify(() -> {
            assertFalse(authResult.isSuccess());
            assertEquals("Invalid refresh token", authResult.getMessage());
            assertNull(authResult.getUser());
            assertNull(authResult.getTokenPair());
            
            verify(jwtService).validateToken("invalidRefreshToken");
            verifyNoMoreInteractions(jwtService);
            verifyNoInteractions(userRepository);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_RefreshTokenCommand_CannotExtractUserInfo(VertxTestContext testContext) {
        // Given
        RefreshTokenCommand command = new RefreshTokenCommand(
            "validRefreshToken",
            "192.168.1.1",
            "Mozilla/5.0"
        );
        
        JWTService.TokenValidationResult validationResult = new JWTService.TokenValidationResult(
            true,
            "Token is valid",
            Map.of()
        );

        when(jwtService.validateToken("validRefreshToken"))
            .thenReturn(validationResult);
        when(jwtService.extractUserId("validRefreshToken"))
            .thenReturn(Optional.empty());
        when(jwtService.extractUserEmail("validRefreshToken"))
            .thenReturn(Optional.empty());

        // When
        Future<AuthenticationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(authResult -> testContext.verify(() -> {
            assertFalse(authResult.isSuccess());
            assertEquals("Invalid token format", authResult.getMessage());
            assertNull(authResult.getUser());
            assertNull(authResult.getTokenPair());
            
            verify(jwtService).validateToken("validRefreshToken");
            verify(jwtService).extractUserId("validRefreshToken");
            verify(jwtService).extractUserEmail("validRefreshToken");
            verifyNoMoreInteractions(jwtService);
            verifyNoInteractions(userRepository);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_RefreshTokenCommand_UserNotFound(VertxTestContext testContext) {
        // Given
        RefreshTokenCommand command = new RefreshTokenCommand(
            "validRefreshToken",
            "192.168.1.1",
            "Mozilla/5.0"
        );
        
        JWTService.TokenValidationResult validationResult = new JWTService.TokenValidationResult(
            true,
            "Token is valid",
            Map.of()
        );

        when(jwtService.validateToken("validRefreshToken"))
            .thenReturn(validationResult);
        when(jwtService.extractUserId("validRefreshToken"))
            .thenReturn(Optional.of(testUser.getId().toString()));
        when(jwtService.extractUserEmail("validRefreshToken"))
            .thenReturn(Optional.of(testUser.getEmail().getValue()));
        when(userRepository.findByEmailWithRoles(any(Email.class)))
            .thenReturn(Future.succeededFuture(Optional.empty()));

        // When
        Future<AuthenticationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(authResult -> testContext.verify(() -> {
            assertFalse(authResult.isSuccess());
            assertEquals("User not found", authResult.getMessage());
            assertNull(authResult.getUser());
            assertNull(authResult.getTokenPair());
            
            verify(jwtService).validateToken("validRefreshToken");
            verify(jwtService).extractUserId("validRefreshToken");
            verify(jwtService).extractUserEmail("validRefreshToken");
            verify(userRepository).findByEmailWithRoles(any(Email.class));
            verifyNoMoreInteractions(jwtService);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_RefreshTokenCommand_InactiveUser(VertxTestContext testContext) {
        // Given
        testUser.deactivate();
        RefreshTokenCommand command = new RefreshTokenCommand(
            "validRefreshToken",
            "192.168.1.1",
            "Mozilla/5.0"
        );
        
        JWTService.TokenValidationResult validationResult = new JWTService.TokenValidationResult(
            true,
            "Token is valid",
            Map.of()
        );

        when(jwtService.validateToken("validRefreshToken"))
            .thenReturn(validationResult);
        when(jwtService.extractUserId("validRefreshToken"))
            .thenReturn(Optional.of(testUser.getId().toString()));
        when(jwtService.extractUserEmail("validRefreshToken"))
            .thenReturn(Optional.of(testUser.getEmail().getValue()));
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
            
            verify(jwtService).validateToken("validRefreshToken");
            verify(jwtService).extractUserId("validRefreshToken");
            verify(jwtService).extractUserEmail("validRefreshToken");
            verify(userRepository).findByEmailWithRoles(any(Email.class));
            verifyNoMoreInteractions(jwtService);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_RefreshTokenCommand_RepositoryFailure(VertxTestContext testContext) {
        // Given
        RefreshTokenCommand command = new RefreshTokenCommand(
            "validRefreshToken",
            "192.168.1.1",
            "Mozilla/5.0"
        );
        
        JWTService.TokenValidationResult validationResult = new JWTService.TokenValidationResult(
            true,
            "Token is valid",
            Map.of()
        );

        when(jwtService.validateToken("validRefreshToken"))
            .thenReturn(validationResult);
        when(jwtService.extractUserId("validRefreshToken"))
            .thenReturn(Optional.of(testUser.getId().toString()));
        when(jwtService.extractUserEmail("validRefreshToken"))
            .thenReturn(Optional.of(testUser.getEmail().getValue()));
        when(userRepository.findByEmailWithRoles(any(Email.class)))
            .thenReturn(Future.failedFuture(new RuntimeException("Database error")));

        // When
        Future<AuthenticationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.failing(throwable -> testContext.verify(() -> {
            assertTrue(throwable instanceof InvalidTokenException);
            assertEquals("Token refresh failed", throwable.getMessage());
            
            verify(jwtService).validateToken("validRefreshToken");
            verify(jwtService).extractUserId("validRefreshToken");
            verify(jwtService).extractUserEmail("validRefreshToken");
            verify(userRepository).findByEmailWithRoles(any(Email.class));
            verifyNoMoreInteractions(jwtService);
            
            testContext.completeNow();
        })));
    }

    @Test
    void getCommandType_ReturnsCorrectType() {
        // When
        Class<RefreshTokenCommand> commandType = handler.getCommandType();

        // Then
        assertEquals(RefreshTokenCommand.class, commandType);
    }
}