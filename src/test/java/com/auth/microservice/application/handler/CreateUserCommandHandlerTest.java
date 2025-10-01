package com.auth.microservice.application.handler;

import com.auth.microservice.application.command.CreateUserCommand;
import com.auth.microservice.application.result.UserCreationResult;
import com.auth.microservice.domain.event.UserCreatedEvent;
import com.auth.microservice.domain.exception.UserAlreadyExistsException;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.EventPublisher;
import com.auth.microservice.domain.port.RoleRepository;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.domain.service.PasswordService;
import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class CreateUserCommandHandlerTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private PasswordService passwordService;
    
    @Mock
    private EventPublisher eventPublisher;

    private CreateUserCommandHandler handler;
    private User testUser;
    private Role testRole;

    @BeforeEach
    void setUp() {
        handler = new CreateUserCommandHandler(userRepository, roleRepository, passwordService, eventPublisher);
        
        testUser = new User(
            "testuser",
            new Email("test@example.com"),
            "hashedPassword",
            "John",
            "Doe"
        );
        
        testRole = new Role(UUID.randomUUID(), "USER", "Standard user role", LocalDateTime.now());
        Permission testPermission = new Permission(UUID.randomUUID(), "READ_PROFILE", "users", "read", "Read user profile");
        testRole.addPermission(testPermission);
    }

    @Test
    void handle_CreateUserCommand_Success(VertxTestContext testContext) {
        // Given
        CreateUserCommand command = new CreateUserCommand(
            "admin-user-id",
            "testuser",
            "test@example.com",
            "John",
            "Doe",
            Set.of(),
            true,
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.existsByEmail(any(Email.class)))
            .thenReturn(Future.succeededFuture(false));
        when(userRepository.existsByUsername(anyString()))
            .thenReturn(Future.succeededFuture(false));
        when(passwordService.generateTemporaryPassword())
            .thenReturn("TempPass123!");
        when(passwordService.hashPassword(anyString()))
            .thenReturn("hashedPassword");
        when(userRepository.save(any(User.class)))
            .thenReturn(Future.succeededFuture(testUser));
        when(eventPublisher.publish(any(UserCreatedEvent.class)))
            .thenReturn(Future.succeededFuture());

        // When
        Future<UserCreationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(creationResult -> testContext.verify(() -> {
            assertTrue(creationResult.isSuccess());
            assertNotNull(creationResult.getUser());
            assertEquals("testuser", creationResult.getUser().getUsername());
            
            verify(userRepository).existsByEmail(any(Email.class));
            verify(userRepository).existsByUsername("testuser");
            verify(passwordService).generateTemporaryPassword();
            verify(passwordService).hashPassword("TempPass123!");
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publish(any(UserCreatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_CreateUserCommand_WithRoles_Success(VertxTestContext testContext) {
        // Given
        CreateUserCommand command = new CreateUserCommand(
            "admin-user-id",
            "testuser",
            "test@example.com",
            "John",
            "Doe",
            Set.of("USER"),
            true,
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.existsByEmail(any(Email.class)))
            .thenReturn(Future.succeededFuture(false));
        when(userRepository.existsByUsername(anyString()))
            .thenReturn(Future.succeededFuture(false));
        when(passwordService.generateTemporaryPassword())
            .thenReturn("TempPass123!");
        when(passwordService.hashPassword(anyString()))
            .thenReturn("hashedPassword");
        when(roleRepository.findByNameWithPermissions("USER"))
            .thenReturn(Future.succeededFuture(Optional.of(testRole)));
        when(userRepository.save(any(User.class)))
            .thenReturn(Future.succeededFuture(testUser));
        when(eventPublisher.publish(any(UserCreatedEvent.class)))
            .thenReturn(Future.succeededFuture());

        // When
        Future<UserCreationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(creationResult -> testContext.verify(() -> {
            assertTrue(creationResult.isSuccess());
            assertNotNull(creationResult.getUser());
            
            verify(userRepository).existsByEmail(any(Email.class));
            verify(userRepository).existsByUsername("testuser");
            verify(passwordService).generateTemporaryPassword();
            verify(passwordService).hashPassword("TempPass123!");
            verify(roleRepository).findByNameWithPermissions("USER");
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publish(any(UserCreatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_CreateUserCommand_InactiveUser_Success(VertxTestContext testContext) {
        // Given
        CreateUserCommand command = new CreateUserCommand(
            "admin-user-id",
            "testuser",
            "test@example.com",
            "John",
            "Doe",
            Set.of(),
            false, // inactive user
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.existsByEmail(any(Email.class)))
            .thenReturn(Future.succeededFuture(false));
        when(userRepository.existsByUsername(anyString()))
            .thenReturn(Future.succeededFuture(false));
        when(passwordService.generateTemporaryPassword())
            .thenReturn("TempPass123!");
        when(passwordService.hashPassword(anyString()))
            .thenReturn("hashedPassword");
        when(userRepository.save(any(User.class)))
            .thenReturn(Future.succeededFuture(testUser));
        when(eventPublisher.publish(any(UserCreatedEvent.class)))
            .thenReturn(Future.succeededFuture());

        // When
        Future<UserCreationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(creationResult -> testContext.verify(() -> {
            assertTrue(creationResult.isSuccess());
            assertNotNull(creationResult.getUser());
            
            verify(userRepository).existsByEmail(any(Email.class));
            verify(userRepository).existsByUsername("testuser");
            verify(passwordService).generateTemporaryPassword();
            verify(passwordService).hashPassword("TempPass123!");
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publish(any(UserCreatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_CreateUserCommand_EmailExists(VertxTestContext testContext) {
        // Given
        CreateUserCommand command = new CreateUserCommand(
            "admin-user-id",
            "testuser",
            "test@example.com",
            "John",
            "Doe",
            Set.of(),
            true,
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.existsByEmail(any(Email.class)))
            .thenReturn(Future.succeededFuture(true));

        // When
        Future<UserCreationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(creationResult -> testContext.verify(() -> {
            assertFalse(creationResult.isSuccess());
            assertEquals("Email already exists", creationResult.getErrorMessage());
            assertNull(creationResult.getUser());
            
            verify(userRepository).existsByEmail(any(Email.class));
            verifyNoMoreInteractions(userRepository);
            verifyNoInteractions(passwordService);
            verifyNoInteractions(roleRepository);
            verifyNoInteractions(eventPublisher);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_CreateUserCommand_UsernameExists(VertxTestContext testContext) {
        // Given
        CreateUserCommand command = new CreateUserCommand(
            "admin-user-id",
            "testuser",
            "test@example.com",
            "John",
            "Doe",
            Set.of(),
            true,
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.existsByEmail(any(Email.class)))
            .thenReturn(Future.succeededFuture(false));
        when(userRepository.existsByUsername(anyString()))
            .thenReturn(Future.succeededFuture(true));

        // When
        Future<UserCreationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(creationResult -> testContext.verify(() -> {
            assertFalse(creationResult.isSuccess());
            assertEquals("Username already exists", creationResult.getErrorMessage());
            assertNull(creationResult.getUser());
            
            verify(userRepository).existsByEmail(any(Email.class));
            verify(userRepository).existsByUsername("testuser");
            verifyNoMoreInteractions(userRepository);
            verifyNoInteractions(passwordService);
            verifyNoInteractions(roleRepository);
            verifyNoInteractions(eventPublisher);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_CreateUserCommand_EventPublishingFails_StillSucceeds(VertxTestContext testContext) {
        // Given
        CreateUserCommand command = new CreateUserCommand(
            "admin-user-id",
            "testuser",
            "test@example.com",
            "John",
            "Doe",
            Set.of(),
            true,
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.existsByEmail(any(Email.class)))
            .thenReturn(Future.succeededFuture(false));
        when(userRepository.existsByUsername(anyString()))
            .thenReturn(Future.succeededFuture(false));
        when(passwordService.generateTemporaryPassword())
            .thenReturn("TempPass123!");
        when(passwordService.hashPassword(anyString()))
            .thenReturn("hashedPassword");
        when(userRepository.save(any(User.class)))
            .thenReturn(Future.succeededFuture(testUser));
        when(eventPublisher.publish(any(UserCreatedEvent.class)))
            .thenReturn(Future.failedFuture(new RuntimeException("Event publishing failed")));

        // When
        Future<UserCreationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(creationResult -> testContext.verify(() -> {
            assertTrue(creationResult.isSuccess());
            assertNotNull(creationResult.getUser());
            
            verify(userRepository).existsByEmail(any(Email.class));
            verify(userRepository).existsByUsername("testuser");
            verify(passwordService).generateTemporaryPassword();
            verify(passwordService).hashPassword("TempPass123!");
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publish(any(UserCreatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_CreateUserCommand_InvalidEmailFormat(VertxTestContext testContext) {
        // Given
        CreateUserCommand command = new CreateUserCommand(
            "admin-user-id",
            "testuser",
            "invalid-email",
            "John",
            "Doe",
            Set.of(),
            true,
            "192.168.1.1",
            "Mozilla/5.0"
        );

        // When
        Future<UserCreationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.failing(throwable -> testContext.verify(() -> {
            assertTrue(throwable instanceof UserAlreadyExistsException);
            assertEquals("User creation failed", throwable.getMessage());
            assertTrue(throwable.getCause() instanceof IllegalArgumentException);
            
            verifyNoInteractions(userRepository);
            verifyNoInteractions(passwordService);
            verifyNoInteractions(roleRepository);
            verifyNoInteractions(eventPublisher);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_CreateUserCommand_RepositoryFailure(VertxTestContext testContext) {
        // Given
        CreateUserCommand command = new CreateUserCommand(
            "admin-user-id",
            "testuser",
            "test@example.com",
            "John",
            "Doe",
            Set.of(),
            true,
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.existsByEmail(any(Email.class)))
            .thenReturn(Future.failedFuture(new RuntimeException("Database error")));

        // When
        Future<UserCreationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.failing(throwable -> testContext.verify(() -> {
            assertTrue(throwable instanceof UserAlreadyExistsException);
            assertEquals("User creation failed", throwable.getMessage());
            
            verify(userRepository).existsByEmail(any(Email.class));
            verifyNoMoreInteractions(userRepository);
            verifyNoInteractions(passwordService);
            verifyNoInteractions(roleRepository);
            verifyNoInteractions(eventPublisher);
            
            testContext.completeNow();
        })));
    }

    @Test
    void getCommandType_ReturnsCorrectType() {
        // When
        Class<CreateUserCommand> commandType = handler.getCommandType();

        // Then
        assertEquals(CreateUserCommand.class, commandType);
    }
}