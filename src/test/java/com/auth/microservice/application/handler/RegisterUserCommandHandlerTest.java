package com.auth.microservice.application.handler;

import com.auth.microservice.application.command.RegisterUserCommand;
import com.auth.microservice.application.result.RegistrationResult;
import com.auth.microservice.domain.exception.UserAlreadyExistsException;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.RoleRepository;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.domain.service.PasswordService;
import com.auth.microservice.domain.service.UsernameGenerationService;
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
class RegisterUserCommandHandlerTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private PasswordService passwordService;
    
    @Mock
    private UsernameGenerationService usernameGenerationService;

    private RegisterUserCommandHandler handler;
    private User testUser;
    private Role testRole;

    @BeforeEach
    void setUp() {
        handler = new RegisterUserCommandHandler(userRepository, roleRepository, passwordService, usernameGenerationService);
        
        testUser = new User(
            "testuser",
            new Email("test@example.com"),
            "hashedPassword",
            "John",
            "Doe"
        );
        
        testRole = new Role(UUID.randomUUID(), "USER", "Standard user role", OffsetDateTime.now());
        Permission testPermission = new Permission(UUID.randomUUID(), "READ_PROFILE", "users", "read", "Read user profile");
        testRole.addPermission(testPermission);
    }

    @Test
    void handle_RegisterUserCommand_Success(VertxTestContext testContext) {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "testuser",
            "test@example.com",
            "Password123!",
            "John",
            "Doe",
            Set.of(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.existsByEmail(any(Email.class)))
            .thenReturn(Future.succeededFuture(false));
        when(userRepository.existsByUsername(anyString()))
            .thenReturn(Future.succeededFuture(false));
        when(passwordService.validatePasswordStrength(anyString()))
            .thenReturn(new PasswordService.PasswordValidationResult(true, "Password is strong"));
        when(passwordService.hashPassword(anyString()))
            .thenReturn("hashedPassword");
        when(userRepository.save(any(User.class)))
            .thenReturn(Future.succeededFuture(testUser));

        // When
        Future<RegistrationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(regResult -> testContext.verify(() -> {
            assertTrue(regResult.isSuccess());
            assertEquals("User registered successfully", regResult.getMessage());
            assertNotNull(regResult.getUser());
            assertEquals("testuser", regResult.getUser().getUsername());
            
            verify(passwordService).validatePasswordStrength("Password123!");
            verify(userRepository).existsByEmail(any(Email.class));
            verify(userRepository).existsByUsername("testuser");
            verify(passwordService).hashPassword("Password123!");
            verify(userRepository).save(any(User.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_RegisterUserCommand_WithRoles_Success(VertxTestContext testContext) {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "testuser",
            "test@example.com",
            "Password123!",
            "John",
            "Doe",
            Set.of("USER"),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.existsByEmail(any(Email.class)))
            .thenReturn(Future.succeededFuture(false));
        when(userRepository.existsByUsername(anyString()))
            .thenReturn(Future.succeededFuture(false));
        when(passwordService.validatePasswordStrength(anyString()))
            .thenReturn(new PasswordService.PasswordValidationResult(true, "Password is strong"));
        when(passwordService.hashPassword(anyString()))
            .thenReturn("hashedPassword");
        when(roleRepository.findByNameWithPermissions("USER"))
            .thenReturn(Future.succeededFuture(Optional.of(testRole)));
        when(userRepository.save(any(User.class)))
            .thenReturn(Future.succeededFuture(testUser));

        // When
        Future<RegistrationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(regResult -> testContext.verify(() -> {
            assertTrue(regResult.isSuccess());
            assertEquals("User registered successfully", regResult.getMessage());
            assertNotNull(regResult.getUser());
            
            verify(passwordService).validatePasswordStrength("Password123!");
            verify(userRepository).existsByEmail(any(Email.class));
            verify(userRepository).existsByUsername("testuser");
            verify(passwordService).hashPassword("Password123!");
            verify(roleRepository).findByNameWithPermissions("USER");
            verify(userRepository).save(any(User.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_RegisterUserCommand_EmailExists(VertxTestContext testContext) {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "testuser",
            "test@example.com",
            "Password123!",
            "John",
            "Doe",
            Set.of(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(passwordService.validatePasswordStrength(anyString()))
            .thenReturn(new PasswordService.PasswordValidationResult(true, "Password is strong"));
        when(userRepository.existsByEmail(any(Email.class)))
            .thenReturn(Future.succeededFuture(true));

        // When
        Future<RegistrationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(regResult -> testContext.verify(() -> {
            assertFalse(regResult.isSuccess());
            assertEquals("Email already exists", regResult.getMessage());
            assertNull(regResult.getUser());
            
            verify(passwordService).validatePasswordStrength("Password123!");
            verify(userRepository).existsByEmail(any(Email.class));
            verifyNoMoreInteractions(userRepository);
            verifyNoMoreInteractions(passwordService);
            verifyNoInteractions(roleRepository);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_RegisterUserCommand_UsernameExists(VertxTestContext testContext) {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "testuser",
            "test@example.com",
            "Password123!",
            "John",
            "Doe",
            Set.of(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(passwordService.validatePasswordStrength(anyString()))
            .thenReturn(new PasswordService.PasswordValidationResult(true, "Password is strong"));
        when(userRepository.existsByEmail(any(Email.class)))
            .thenReturn(Future.succeededFuture(false));
        when(userRepository.existsByUsername(anyString()))
            .thenReturn(Future.succeededFuture(true));

        // When
        Future<RegistrationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(regResult -> testContext.verify(() -> {
            assertFalse(regResult.isSuccess());
            assertEquals("Username already exists", regResult.getMessage());
            assertNull(regResult.getUser());
            
            verify(passwordService).validatePasswordStrength("Password123!");
            verify(userRepository).existsByEmail(any(Email.class));
            verify(userRepository).existsByUsername("testuser");
            verifyNoMoreInteractions(userRepository);
            verifyNoMoreInteractions(passwordService);
            verifyNoInteractions(roleRepository);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_RegisterUserCommand_WeakPassword(VertxTestContext testContext) {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "testuser",
            "test@example.com",
            "weak",
            "John",
            "Doe",
            Set.of(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(passwordService.validatePasswordStrength(anyString()))
            .thenReturn(new PasswordService.PasswordValidationResult(false, "Password is too weak"));

        // When
        Future<RegistrationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(regResult -> testContext.verify(() -> {
            assertFalse(regResult.isSuccess());
            assertEquals("Password is too weak", regResult.getMessage());
            assertNull(regResult.getUser());
            
            verify(passwordService).validatePasswordStrength("weak");
            verifyNoInteractions(userRepository);
            verifyNoMoreInteractions(passwordService);
            verifyNoInteractions(roleRepository);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_RegisterUserCommand_InvalidEmailFormat(VertxTestContext testContext) {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "testuser",
            "invalid-email",
            "Password123!",
            "John",
            "Doe",
            Set.of(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        // When
        Future<RegistrationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.failing(throwable -> testContext.verify(() -> {
            assertTrue(throwable instanceof UserAlreadyExistsException);
            assertEquals("Registration failed", throwable.getMessage());
            assertTrue(throwable.getCause() instanceof IllegalArgumentException);
            
            verifyNoInteractions(userRepository);
            verifyNoInteractions(passwordService);
            verifyNoInteractions(roleRepository);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_RegisterUserCommand_RepositoryFailure(VertxTestContext testContext) {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "testuser",
            "test@example.com",
            "Password123!",
            "John",
            "Doe",
            Set.of(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(passwordService.validatePasswordStrength(anyString()))
            .thenReturn(new PasswordService.PasswordValidationResult(true, "Password is strong"));
        when(userRepository.existsByEmail(any(Email.class)))
            .thenReturn(Future.failedFuture(new RuntimeException("Database error")));

        // When
        Future<RegistrationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.failing(throwable -> testContext.verify(() -> {
            assertTrue(throwable instanceof UserAlreadyExistsException);
            assertEquals("Registration failed", throwable.getMessage());
            
            verify(passwordService).validatePasswordStrength("Password123!");
            verify(userRepository).existsByEmail(any(Email.class));
            verifyNoMoreInteractions(passwordService);
            verifyNoInteractions(roleRepository);
            
            testContext.completeNow();
        })));
    }

    @Test
    void getCommandType_ReturnsCorrectType() {
        // When
        Class<RegisterUserCommand> commandType = handler.getCommandType();

        // Then
        assertEquals(RegisterUserCommand.class, commandType);
    }
}