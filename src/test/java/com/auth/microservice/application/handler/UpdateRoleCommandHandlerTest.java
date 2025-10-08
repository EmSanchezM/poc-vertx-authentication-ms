package com.auth.microservice.application.handler;

import com.auth.microservice.application.command.UpdateRoleCommand;
import com.auth.microservice.application.result.RoleUpdateResult;
import com.auth.microservice.domain.event.RoleUpdatedEvent;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.port.EventPublisher;
import com.auth.microservice.domain.port.RoleRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class UpdateRoleCommandHandlerTest {

    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private EventPublisher eventPublisher;

    private UpdateRoleCommandHandler handler;
    private Role testRole;
    private UUID testRoleId;

    @BeforeEach
    void setUp() {
        handler = new UpdateRoleCommandHandler(roleRepository, eventPublisher);
        
        testRoleId = UUID.randomUUID();
        testRole = new Role(testRoleId, "ADMIN", "Administrator role", OffsetDateTime.now());
    }

    @Test
    void shouldUpdateRoleSuccessfully(VertxTestContext testContext) {
        // Given
        UpdateRoleCommand command = new UpdateRoleCommand(
            "admin-user-id",
            testRoleId,
            "Updated administrator role",
            "192.168.1.1",
            "Test-Agent"
        );

        Role updatedRole = new Role(testRoleId, "ADMIN", "Updated administrator role", OffsetDateTime.now());

        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.of(testRole)));
        when(roleRepository.save(any(Role.class)))
            .thenReturn(Future.succeededFuture(updatedRole));
        when(eventPublisher.publish(any(RoleUpdatedEvent.class)))
            .thenReturn(Future.succeededFuture());

        // When
        Future<RoleUpdateResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(updateResult -> testContext.verify(() -> {
            assertTrue(updateResult.isSuccess());
            assertNotNull(updateResult.getRole());
            assertEquals("ADMIN", updateResult.getRole().getName());
            assertEquals("Updated administrator role", updateResult.getRole().getDescription());
            
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verify(roleRepository).save(any(Role.class));
            verify(eventPublisher).publish(any(RoleUpdatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void shouldFailWhenRoleNotFound(VertxTestContext testContext) {
        // Given
        UpdateRoleCommand command = new UpdateRoleCommand(
            "admin-user-id",
            testRoleId,
            "Updated administrator role",
            "192.168.1.1",
            "Test-Agent"
        );

        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.empty()));

        // When
        Future<RoleUpdateResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(updateResult -> testContext.verify(() -> {
            assertFalse(updateResult.isSuccess());
            assertEquals("Role not found", updateResult.getErrorMessage());
            
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verify(roleRepository, never()).save(any(Role.class));
            verify(eventPublisher, never()).publish(any(RoleUpdatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void shouldUpdateRoleWithNullDescription(VertxTestContext testContext) {
        // Given
        UpdateRoleCommand command = new UpdateRoleCommand(
            "admin-user-id",
            testRoleId,
            null, // Null description should be allowed
            "192.168.1.1",
            "Test-Agent"
        );

        Role updatedRole = new Role(testRoleId, "ADMIN", null, OffsetDateTime.now());

        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.of(testRole)));
        when(roleRepository.save(any(Role.class)))
            .thenReturn(Future.succeededFuture(updatedRole));
        when(eventPublisher.publish(any(RoleUpdatedEvent.class)))
            .thenReturn(Future.succeededFuture());

        // When
        Future<RoleUpdateResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(updateResult -> testContext.verify(() -> {
            assertTrue(updateResult.isSuccess());
            assertNotNull(updateResult.getRole());
            assertEquals("ADMIN", updateResult.getRole().getName());
            assertEquals("", updateResult.getRole().getDescription());
            
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verify(roleRepository).save(any(Role.class));
            verify(eventPublisher).publish(any(RoleUpdatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void shouldHandleRepositoryError(VertxTestContext testContext) {
        // Given
        UpdateRoleCommand command = new UpdateRoleCommand(
            "admin-user-id",
            testRoleId,
            "Updated administrator role",
            "192.168.1.1",
            "Test-Agent"
        );

        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.failedFuture(new RuntimeException("Database error")));

        // When
        Future<RoleUpdateResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.failing(throwable -> testContext.verify(() -> {
            assertNotNull(throwable);
            assertTrue(throwable.getMessage().contains("Role update failed"));
            
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verify(roleRepository, never()).save(any(Role.class));
            verify(eventPublisher, never()).publish(any(RoleUpdatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void shouldContinueWhenEventPublishingFails(VertxTestContext testContext) {
        // Given
        UpdateRoleCommand command = new UpdateRoleCommand(
            "admin-user-id",
            testRoleId,
            "Updated administrator role",
            "192.168.1.1",
            "Test-Agent"
        );

        Role updatedRole = new Role(testRoleId, "ADMIN", "Updated administrator role", OffsetDateTime.now());

        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.of(testRole)));
        when(roleRepository.save(any(Role.class)))
            .thenReturn(Future.succeededFuture(updatedRole));
        when(eventPublisher.publish(any(RoleUpdatedEvent.class)))
            .thenReturn(Future.failedFuture(new RuntimeException("Event publishing failed")));

        // When
        Future<RoleUpdateResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(updateResult -> testContext.verify(() -> {
            assertTrue(updateResult.isSuccess());
            assertNotNull(updateResult.getRole());
            assertEquals("ADMIN", updateResult.getRole().getName());
            assertEquals("Updated administrator role", updateResult.getRole().getDescription());
            
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verify(roleRepository).save(any(Role.class));
            verify(eventPublisher).publish(any(RoleUpdatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void shouldHandleInvalidArgumentException(VertxTestContext testContext) {
        // Given
        UpdateRoleCommand command = new UpdateRoleCommand(
            "admin-user-id",
            testRoleId,
            "Updated administrator role",
            "192.168.1.1",
            "Test-Agent"
        );

        when(roleRepository.findByIdWithPermissions(testRoleId))
            .thenReturn(Future.succeededFuture(Optional.of(testRole)));
        when(roleRepository.save(any(Role.class)))
            .thenReturn(Future.failedFuture(new IllegalArgumentException("Invalid role data")));

        // When
        Future<RoleUpdateResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(updateResult -> testContext.verify(() -> {
            assertFalse(updateResult.isSuccess());
            assertEquals("Invalid role data", updateResult.getErrorMessage());
            
            verify(roleRepository).findByIdWithPermissions(testRoleId);
            verify(roleRepository).save(any(Role.class));
            verify(eventPublisher, never()).publish(any(RoleUpdatedEvent.class));
            
            testContext.completeNow();
        })));
    }
}