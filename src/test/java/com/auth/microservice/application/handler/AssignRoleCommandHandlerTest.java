package com.auth.microservice.application.handler;

import com.auth.microservice.application.command.AssignRoleCommand;
import com.auth.microservice.application.result.RoleAssignmentResult;
import com.auth.microservice.domain.event.RoleAssignedEvent;
import com.auth.microservice.domain.exception.RoleNotFoundException;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.EventPublisher;
import com.auth.microservice.domain.port.RoleRepository;
import com.auth.microservice.domain.port.UserRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class AssignRoleCommandHandlerTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private EventPublisher eventPublisher;

    private AssignRoleCommandHandler handler;
    private User testUser;
    private Role testRole;
    private UUID testUserId;
    private UUID testRoleId;

    @BeforeEach
    void setUp() {
        handler = new AssignRoleCommandHandler(userRepository, roleRepository, eventPublisher);
        
        testUserId = UUID.randomUUID();
        testRoleId = UUID.randomUUID();
        
        testUser = new User(
            testUserId,
            "testuser",
            new Email("test@example.com"),
            "hashedPassword",
            "John",
            "Doe",
            true,
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now().minusDays(1)
        );
        
        testRole = new Role(testRoleId, "ADMIN", "Administrator role", LocalDateTime.now());
        Permission testPermission = new Permission(UUID.randomUUID(), "MANAGE_USERS", "users", "manage", "Manage users");
        testRole.addPermission(testPermission);
    }

    @Test
    void handle_AssignRoleCommand_Success(VertxTestContext testContext) {
        // Given
        AssignRoleCommand command = new AssignRoleCommand(
            "executor-user-id",
            testUserId.toString(),
            testRoleId.toString(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.findByIdWithRoles(testUserId))
            .thenReturn(Future.succeededFuture(Optional.of(testUser)));
        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.of(testRole)));
        when(userRepository.save(any(User.class)))
            .thenReturn(Future.succeededFuture(testUser));
        when(eventPublisher.publish(any(RoleAssignedEvent.class)))
            .thenReturn(Future.succeededFuture());

        // When
        Future<RoleAssignmentResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(assignmentResult -> testContext.verify(() -> {
            assertTrue(assignmentResult.isSuccess());
            assertNotNull(assignmentResult.getUser());
            assertTrue(assignmentResult.getUser().getRoles().contains(testRole));
            
            verify(userRepository).findByIdWithRoles(testUserId);
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publish(any(RoleAssignedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_AssignRoleCommand_UserAlreadyHasRole_Success(VertxTestContext testContext) {
        // Given
        testUser.addRole(testRole); // User already has the role
        
        AssignRoleCommand command = new AssignRoleCommand(
            "executor-user-id",
            testUserId.toString(),
            testRoleId.toString(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.findByIdWithRoles(testUserId))
            .thenReturn(Future.succeededFuture(Optional.of(testUser)));
        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.of(testRole)));

        // When
        Future<RoleAssignmentResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(assignmentResult -> testContext.verify(() -> {
            assertTrue(assignmentResult.isSuccess());
            assertNotNull(assignmentResult.getUser());
            assertTrue(assignmentResult.getUser().getRoles().contains(testRole));
            
            verify(userRepository).findByIdWithRoles(testUserId);
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verifyNoMoreInteractions(userRepository); // save should not be called
            verifyNoInteractions(eventPublisher); // no event should be published
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_AssignRoleCommand_UserNotFound(VertxTestContext testContext) {
        // Given
        AssignRoleCommand command = new AssignRoleCommand(
            "executor-user-id",
            testUserId.toString(),
            testRoleId.toString(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.findByIdWithRoles(testUserId))
            .thenReturn(Future.succeededFuture(Optional.empty()));
        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.of(testRole)));

        // When
        Future<RoleAssignmentResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(assignmentResult -> testContext.verify(() -> {
            assertFalse(assignmentResult.isSuccess());
            assertEquals("User not found", assignmentResult.getErrorMessage());
            assertNull(assignmentResult.getUser());
            
            verify(userRepository).findByIdWithRoles(testUserId);
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verifyNoMoreInteractions(userRepository);
            verifyNoInteractions(eventPublisher);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_AssignRoleCommand_RoleNotFound(VertxTestContext testContext) {
        // Given
        AssignRoleCommand command = new AssignRoleCommand(
            "executor-user-id",
            testUserId.toString(),
            testRoleId.toString(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.findByIdWithRoles(testUserId))
            .thenReturn(Future.succeededFuture(Optional.of(testUser)));
        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.empty()));

        // When
        Future<RoleAssignmentResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(assignmentResult -> testContext.verify(() -> {
            assertFalse(assignmentResult.isSuccess());
            assertEquals("Role not found", assignmentResult.getErrorMessage());
            assertNull(assignmentResult.getUser());
            
            verify(userRepository).findByIdWithRoles(testUserId);
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verifyNoMoreInteractions(userRepository);
            verifyNoInteractions(eventPublisher);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_AssignRoleCommand_EventPublishingFails_StillSucceeds(VertxTestContext testContext) {
        // Given
        AssignRoleCommand command = new AssignRoleCommand(
            "executor-user-id",
            testUserId.toString(),
            testRoleId.toString(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.findByIdWithRoles(testUserId))
            .thenReturn(Future.succeededFuture(Optional.of(testUser)));
        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.of(testRole)));
        when(userRepository.save(any(User.class)))
            .thenReturn(Future.succeededFuture(testUser));
        when(eventPublisher.publish(any(RoleAssignedEvent.class)))
            .thenReturn(Future.failedFuture(new RuntimeException("Event publishing failed")));

        // When
        Future<RoleAssignmentResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(assignmentResult -> testContext.verify(() -> {
            assertTrue(assignmentResult.isSuccess());
            assertNotNull(assignmentResult.getUser());
            
            verify(userRepository).findByIdWithRoles(testUserId);
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publish(any(RoleAssignedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_AssignRoleCommand_InvalidUserIdFormat(VertxTestContext testContext) {
        // Given
        AssignRoleCommand command = new AssignRoleCommand(
            "executor-user-id",
            "invalid-uuid",
            testRoleId.toString(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        // When
        Future<RoleAssignmentResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.failing(throwable -> testContext.verify(() -> {
            assertTrue(throwable instanceof RoleNotFoundException);
            assertEquals("Role assignment failed", throwable.getMessage());
            assertTrue(throwable.getCause() instanceof IllegalArgumentException);
            
            verifyNoInteractions(userRepository);
            verifyNoInteractions(roleRepository);
            verifyNoInteractions(eventPublisher);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_AssignRoleCommand_InvalidRoleIdFormat(VertxTestContext testContext) {
        // Given
        AssignRoleCommand command = new AssignRoleCommand(
            "executor-user-id",
            testUserId.toString(),
            "invalid-uuid",
            "192.168.1.1",
            "Mozilla/5.0"
        );

        // When
        Future<RoleAssignmentResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.failing(throwable -> testContext.verify(() -> {
            assertTrue(throwable instanceof RoleNotFoundException);
            assertEquals("Role assignment failed", throwable.getMessage());
            assertTrue(throwable.getCause() instanceof IllegalArgumentException);
            
            verifyNoInteractions(userRepository);
            verifyNoInteractions(roleRepository);
            verifyNoInteractions(eventPublisher);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_AssignRoleCommand_RepositoryFailure(VertxTestContext testContext) {
        // Given
        AssignRoleCommand command = new AssignRoleCommand(
            "executor-user-id",
            testUserId.toString(),
            testRoleId.toString(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.findByIdWithRoles(testUserId))
            .thenReturn(Future.failedFuture(new RuntimeException("Database error")));
        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.of(testRole)));

        // When
        Future<RoleAssignmentResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.failing(throwable -> testContext.verify(() -> {
            assertTrue(throwable instanceof RoleNotFoundException);
            assertEquals("Role assignment failed", throwable.getMessage());
            
            verify(userRepository).findByIdWithRoles(testUserId);
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verifyNoMoreInteractions(userRepository);
            verifyNoInteractions(eventPublisher);
            
            testContext.completeNow();
        })));
    }

    @Test
    void getCommandType_ReturnsCorrectType() {
        // When
        Class<AssignRoleCommand> commandType = handler.getCommandType();

        // Then
        assertEquals(AssignRoleCommand.class, commandType);
    }
}