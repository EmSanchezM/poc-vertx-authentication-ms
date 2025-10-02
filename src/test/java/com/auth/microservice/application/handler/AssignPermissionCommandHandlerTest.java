package com.auth.microservice.application.handler;

import com.auth.microservice.application.command.AssignPermissionCommand;
import com.auth.microservice.application.result.PermissionAssignmentResult;
import com.auth.microservice.domain.event.PermissionsAssignedEvent;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.port.EventPublisher;
import com.auth.microservice.domain.port.PermissionRepository;
import com.auth.microservice.domain.port.RoleRepository;
import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class AssignPermissionCommandHandlerTest {

    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private PermissionRepository permissionRepository;
    
    @Mock
    private EventPublisher eventPublisher;

    private AssignPermissionCommandHandler handler;
    private Role testRole;
    private Permission testPermission1;
    private Permission testPermission2;
    private UUID testRoleId;

    @BeforeEach
    void setUp() {
        handler = new AssignPermissionCommandHandler(roleRepository, permissionRepository, eventPublisher);
        
        testRoleId = UUID.randomUUID();
        testRole = new Role(testRoleId, "ADMIN", "Administrator role", java.time.LocalDateTime.now());
        
        testPermission1 = new Permission("USER_READ", "users", "read", "Read user data");
        testPermission2 = new Permission("USER_WRITE", "users", "write", "Write user data");
    }

    @Test
    void shouldAssignPermissionsSuccessfully(VertxTestContext testContext) {
        // Given
        AssignPermissionCommand command = new AssignPermissionCommand(
            "admin-user-id",
            testRoleId,
            Set.of("USER_READ", "USER_WRITE"),
            false,
            "192.168.1.1",
            "Test-Agent"
        );

        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.of(testRole)));
        when(permissionRepository.findByName("USER_READ"))
            .thenReturn(Future.succeededFuture(Optional.of(testPermission1)));
        when(permissionRepository.findByName("USER_WRITE"))
            .thenReturn(Future.succeededFuture(Optional.of(testPermission2)));
        when(roleRepository.save(any(Role.class)))
            .thenReturn(Future.succeededFuture(testRole));
        when(eventPublisher.publish(any(PermissionsAssignedEvent.class)))
            .thenReturn(Future.succeededFuture());

        // When
        Future<PermissionAssignmentResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(assignmentResult -> testContext.verify(() -> {
            assertTrue(assignmentResult.isSuccess());
            assertNotNull(assignmentResult.getRole());
            assertEquals(2, assignmentResult.getAssignedCount());
            assertEquals(0, assignmentResult.getSkippedCount());
            
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verify(permissionRepository).findByName("USER_READ");
            verify(permissionRepository).findByName("USER_WRITE");
            verify(roleRepository).save(any(Role.class));
            verify(eventPublisher).publish(any(PermissionsAssignedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void shouldFailWhenRoleNotFound(VertxTestContext testContext) {
        // Given
        AssignPermissionCommand command = new AssignPermissionCommand(
            "admin-user-id",
            testRoleId,
            Set.of("USER_READ"),
            false,
            "192.168.1.1",
            "Test-Agent"
        );

        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.empty()));

        // When
        Future<PermissionAssignmentResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(assignmentResult -> testContext.verify(() -> {
            assertFalse(assignmentResult.isSuccess());
            assertEquals("Role not found", assignmentResult.getErrorMessage());
            
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verify(permissionRepository, never()).findByName(anyString());
            verify(roleRepository, never()).save(any(Role.class));
            verify(eventPublisher, never()).publish(any(PermissionsAssignedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void shouldReplaceExistingPermissions(VertxTestContext testContext) {
        // Given
        testRole.addPermission(testPermission1); // Role already has USER_READ
        
        AssignPermissionCommand command = new AssignPermissionCommand(
            "admin-user-id",
            testRoleId,
            Set.of("USER_WRITE"),
            true, // Replace existing
            "192.168.1.1",
            "Test-Agent"
        );

        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.of(testRole)));
        when(permissionRepository.findByName("USER_WRITE"))
            .thenReturn(Future.succeededFuture(Optional.of(testPermission2)));
        when(roleRepository.save(any(Role.class)))
            .thenReturn(Future.succeededFuture(testRole));
        when(eventPublisher.publish(any(PermissionsAssignedEvent.class)))
            .thenReturn(Future.succeededFuture());

        // When
        Future<PermissionAssignmentResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(assignmentResult -> testContext.verify(() -> {
            assertTrue(assignmentResult.isSuccess());
            assertNotNull(assignmentResult.getRole());
            assertEquals(1, assignmentResult.getAssignedCount());
            assertEquals(0, assignmentResult.getSkippedCount());
            
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verify(permissionRepository).findByName("USER_WRITE");
            verify(roleRepository).save(any(Role.class));
            verify(eventPublisher).publish(any(PermissionsAssignedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void shouldSkipNonExistentPermissions(VertxTestContext testContext) {
        // Given
        AssignPermissionCommand command = new AssignPermissionCommand(
            "admin-user-id",
            testRoleId,
            Set.of("USER_READ", "NON_EXISTENT"),
            false,
            "192.168.1.1",
            "Test-Agent"
        );

        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.of(testRole)));
        when(permissionRepository.findByName("USER_READ"))
            .thenReturn(Future.succeededFuture(Optional.of(testPermission1)));
        when(permissionRepository.findByName("NON_EXISTENT"))
            .thenReturn(Future.succeededFuture(Optional.empty()));
        when(roleRepository.save(any(Role.class)))
            .thenReturn(Future.succeededFuture(testRole));
        when(eventPublisher.publish(any(PermissionsAssignedEvent.class)))
            .thenReturn(Future.succeededFuture());

        // When
        Future<PermissionAssignmentResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(assignmentResult -> testContext.verify(() -> {
            assertTrue(assignmentResult.isSuccess());
            assertNotNull(assignmentResult.getRole());
            assertEquals(1, assignmentResult.getAssignedCount());
            assertEquals(1, assignmentResult.getSkippedCount());
            
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verify(permissionRepository).findByName("USER_READ");
            verify(permissionRepository).findByName("NON_EXISTENT");
            verify(roleRepository).save(any(Role.class));
            verify(eventPublisher).publish(any(PermissionsAssignedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void shouldContinueWhenEventPublishingFails(VertxTestContext testContext) {
        // Given
        AssignPermissionCommand command = new AssignPermissionCommand(
            "admin-user-id",
            testRoleId,
            Set.of("USER_READ"),
            false,
            "192.168.1.1",
            "Test-Agent"
        );

        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.of(testRole)));
        when(permissionRepository.findByName("USER_READ"))
            .thenReturn(Future.succeededFuture(Optional.of(testPermission1)));
        when(roleRepository.save(any(Role.class)))
            .thenReturn(Future.succeededFuture(testRole));
        when(eventPublisher.publish(any(PermissionsAssignedEvent.class)))
            .thenReturn(Future.failedFuture(new RuntimeException("Event publishing failed")));

        // When
        Future<PermissionAssignmentResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(assignmentResult -> testContext.verify(() -> {
            assertTrue(assignmentResult.isSuccess());
            assertNotNull(assignmentResult.getRole());
            assertEquals(1, assignmentResult.getAssignedCount());
            assertEquals(0, assignmentResult.getSkippedCount());
            
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verify(permissionRepository).findByName("USER_READ");
            verify(roleRepository).save(any(Role.class));
            verify(eventPublisher).publish(any(PermissionsAssignedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void shouldHandleRepositoryError(VertxTestContext testContext) {
        // Given
        AssignPermissionCommand command = new AssignPermissionCommand(
            "admin-user-id",
            testRoleId,
            Set.of("USER_READ"),
            false,
            "192.168.1.1",
            "Test-Agent"
        );

        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.failedFuture(new RuntimeException("Database error")));

        // When
        Future<PermissionAssignmentResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(assignmentResult -> testContext.verify(() -> {
            assertFalse(assignmentResult.isSuccess());
            assertTrue(assignmentResult.getErrorMessage().contains("Permission assignment failed"));
            
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verify(permissionRepository, never()).findByName(anyString());
            verify(roleRepository, never()).save(any(Role.class));
            verify(eventPublisher, never()).publish(any(PermissionsAssignedEvent.class));
            
            testContext.completeNow();
        })));
    }
}