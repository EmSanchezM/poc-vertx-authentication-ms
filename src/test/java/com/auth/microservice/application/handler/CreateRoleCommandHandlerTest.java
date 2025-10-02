package com.auth.microservice.application.handler;

import com.auth.microservice.application.command.CreateRoleCommand;
import com.auth.microservice.application.result.RoleCreationResult;
import com.auth.microservice.domain.event.RoleCreatedEvent;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class CreateRoleCommandHandlerTest {

    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private PermissionRepository permissionRepository;
    
    @Mock
    private EventPublisher eventPublisher;

    private CreateRoleCommandHandler handler;
    private Role testRole;
    private Permission testPermission;

    @BeforeEach
    void setUp() {
        handler = new CreateRoleCommandHandler(roleRepository, permissionRepository, eventPublisher);
        
        testRole = new Role("ADMIN", "Administrator role");
        testPermission = new Permission("USER_READ", "users", "read", "Read user data");
    }

    @Test
    void shouldCreateRoleSuccessfully(VertxTestContext testContext) {
        // Given
        CreateRoleCommand command = new CreateRoleCommand(
            "admin-user-id",
            "ADMIN",
            "Administrator role",
            Set.of(),
            "192.168.1.1",
            "Test-Agent"
        );

        when(roleRepository.existsByName("ADMIN")).thenReturn(Future.succeededFuture(false));
        when(roleRepository.save(any(Role.class))).thenReturn(Future.succeededFuture(testRole));
        when(eventPublisher.publish(any(RoleCreatedEvent.class))).thenReturn(Future.succeededFuture());

        // When
        Future<RoleCreationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(creationResult -> testContext.verify(() -> {
            assertTrue(creationResult.isSuccess());
            assertNotNull(creationResult.getRole());
            assertEquals("ADMIN", creationResult.getRole().getName());
            assertEquals("Administrator role", creationResult.getRole().getDescription());
            
            verify(roleRepository).existsByName("ADMIN");
            verify(roleRepository).save(any(Role.class));
            verify(eventPublisher).publish(any(RoleCreatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void shouldFailWhenRoleAlreadyExists(VertxTestContext testContext) {
        // Given
        CreateRoleCommand command = new CreateRoleCommand(
            "admin-user-id",
            "ADMIN",
            "Administrator role",
            Set.of(),
            "192.168.1.1",
            "Test-Agent"
        );

        when(roleRepository.existsByName("ADMIN")).thenReturn(Future.succeededFuture(true));

        // When
        Future<RoleCreationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(creationResult -> testContext.verify(() -> {
            assertFalse(creationResult.isSuccess());
            assertEquals("Role already exists", creationResult.getErrorMessage());
            
            verify(roleRepository).existsByName("ADMIN");
            verify(roleRepository, never()).save(any(Role.class));
            verify(eventPublisher, never()).publish(any(RoleCreatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void shouldCreateRoleWithPermissions(VertxTestContext testContext) {
        // Given
        CreateRoleCommand command = new CreateRoleCommand(
            "admin-user-id",
            "ADMIN",
            "Administrator role",
            Set.of("USER_READ"),
            "192.168.1.1",
            "Test-Agent"
        );

        when(roleRepository.existsByName("ADMIN")).thenReturn(Future.succeededFuture(false));
        when(permissionRepository.findByName("USER_READ")).thenReturn(Future.succeededFuture(Optional.of(testPermission)));
        when(roleRepository.save(any(Role.class))).thenReturn(Future.succeededFuture(testRole));
        when(eventPublisher.publish(any(RoleCreatedEvent.class))).thenReturn(Future.succeededFuture());

        // When
        Future<RoleCreationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(creationResult -> testContext.verify(() -> {
            assertTrue(creationResult.isSuccess());
            assertNotNull(creationResult.getRole());
            assertEquals("ADMIN", creationResult.getRole().getName());
            
            verify(roleRepository).existsByName("ADMIN");
            verify(permissionRepository).findByName("USER_READ");
            verify(roleRepository).save(any(Role.class));
            verify(eventPublisher).publish(any(RoleCreatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void shouldHandleInvalidRoleName(VertxTestContext testContext) {
        // Given
        CreateRoleCommand command = new CreateRoleCommand(
            "admin-user-id",
            "", // Invalid empty name
            "Administrator role",
            Set.of(),
            "192.168.1.1",
            "Test-Agent"
        );

        when(roleRepository.existsByName("")).thenReturn(Future.succeededFuture(false));

        // When
        Future<RoleCreationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(creationResult -> testContext.verify(() -> {
            assertFalse(creationResult.isSuccess());
            assertNotNull(creationResult.getErrorMessage());
            assertTrue(creationResult.getErrorMessage().contains("Role name cannot be null or empty"));
            
            testContext.completeNow();
        })));
    }
}