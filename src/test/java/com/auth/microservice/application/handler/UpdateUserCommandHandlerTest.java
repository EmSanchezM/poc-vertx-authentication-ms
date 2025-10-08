package com.auth.microservice.application.handler;

import com.auth.microservice.application.command.UpdateUserCommand;
import com.auth.microservice.application.result.UserUpdateResult;
import com.auth.microservice.domain.event.UserUpdatedEvent;
import com.auth.microservice.domain.exception.UserNotFoundException;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.EventPublisher;
import com.auth.microservice.domain.port.UserRepository;
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
class UpdateUserCommandHandlerTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private EventPublisher eventPublisher;

    private UpdateUserCommandHandler handler;
    private User testUser;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        handler = new UpdateUserCommandHandler(userRepository, eventPublisher);
        
        testUserId = UUID.randomUUID();
        testUser = new User(
            testUserId,
            "testuser",
            new Email("test@example.com"),
            "hashedPassword",
            "John",
            "Doe",
            true,
            OffsetDateTime.now().minusDays(1),
            OffsetDateTime.now().minusDays(1)
        );
    }

    @Test
    void handle_UpdateUserCommand_UpdateFirstName_Success(VertxTestContext testContext) {
        // Given
        UpdateUserCommand command = new UpdateUserCommand(
            "executor-user-id",
            testUserId.toString(),
            Optional.of("Jane"),
            Optional.empty(),
            Optional.empty(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.findById(testUserId))
            .thenReturn(Future.succeededFuture(Optional.of(testUser)));
        when(userRepository.save(any(User.class)))
            .thenReturn(Future.succeededFuture(testUser));
        when(eventPublisher.publish(any(UserUpdatedEvent.class)))
            .thenReturn(Future.succeededFuture());

        // When
        Future<UserUpdateResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(updateResult -> testContext.verify(() -> {
            assertTrue(updateResult.isSuccess());
            assertNotNull(updateResult.getUser());
            assertEquals("Jane", updateResult.getUser().getFirstName());
            
            verify(userRepository).findById(testUserId);
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publish(any(UserUpdatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_UpdateUserCommand_UpdateLastName_Success(VertxTestContext testContext) {
        // Given
        UpdateUserCommand command = new UpdateUserCommand(
            "executor-user-id",
            testUserId.toString(),
            Optional.empty(),
            Optional.of("Smith"),
            Optional.empty(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.findById(testUserId))
            .thenReturn(Future.succeededFuture(Optional.of(testUser)));
        when(userRepository.save(any(User.class)))
            .thenReturn(Future.succeededFuture(testUser));
        when(eventPublisher.publish(any(UserUpdatedEvent.class)))
            .thenReturn(Future.succeededFuture());

        // When
        Future<UserUpdateResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(updateResult -> testContext.verify(() -> {
            assertTrue(updateResult.isSuccess());
            assertNotNull(updateResult.getUser());
            assertEquals("Smith", updateResult.getUser().getLastName());
            
            verify(userRepository).findById(testUserId);
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publish(any(UserUpdatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_UpdateUserCommand_UpdateActiveStatus_Success(VertxTestContext testContext) {
        // Given
        UpdateUserCommand command = new UpdateUserCommand(
            "executor-user-id",
            testUserId.toString(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(false),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.findById(testUserId))
            .thenReturn(Future.succeededFuture(Optional.of(testUser)));
        when(userRepository.save(any(User.class)))
            .thenReturn(Future.succeededFuture(testUser));
        when(eventPublisher.publish(any(UserUpdatedEvent.class)))
            .thenReturn(Future.succeededFuture());

        // When
        Future<UserUpdateResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(updateResult -> testContext.verify(() -> {
            assertTrue(updateResult.isSuccess());
            assertNotNull(updateResult.getUser());
            assertFalse(updateResult.getUser().isActive());
            
            verify(userRepository).findById(testUserId);
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publish(any(UserUpdatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_UpdateUserCommand_UpdateMultipleFields_Success(VertxTestContext testContext) {
        // Given
        UpdateUserCommand command = new UpdateUserCommand(
            "executor-user-id",
            testUserId.toString(),
            Optional.of("Jane"),
            Optional.of("Smith"),
            Optional.of(false),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.findById(testUserId))
            .thenReturn(Future.succeededFuture(Optional.of(testUser)));
        when(userRepository.save(any(User.class)))
            .thenReturn(Future.succeededFuture(testUser));
        when(eventPublisher.publish(any(UserUpdatedEvent.class)))
            .thenReturn(Future.succeededFuture());

        // When
        Future<UserUpdateResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(updateResult -> testContext.verify(() -> {
            assertTrue(updateResult.isSuccess());
            assertNotNull(updateResult.getUser());
            assertEquals("Jane", updateResult.getUser().getFirstName());
            assertEquals("Smith", updateResult.getUser().getLastName());
            assertFalse(updateResult.getUser().isActive());
            
            verify(userRepository).findById(testUserId);
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publish(any(UserUpdatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_UpdateUserCommand_NoChanges_Success(VertxTestContext testContext) {
        // Given - command with same values as current user
        UpdateUserCommand command = new UpdateUserCommand(
            "executor-user-id",
            testUserId.toString(),
            Optional.of("John"), // same as current
            Optional.of("Doe"),  // same as current
            Optional.of(true),   // same as current
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.findById(testUserId))
            .thenReturn(Future.succeededFuture(Optional.of(testUser)));

        // When
        Future<UserUpdateResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(updateResult -> testContext.verify(() -> {
            assertTrue(updateResult.isSuccess());
            assertNotNull(updateResult.getUser());
            
            verify(userRepository).findById(testUserId);
            verifyNoMoreInteractions(userRepository); // save should not be called
            verifyNoInteractions(eventPublisher); // no event should be published
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_UpdateUserCommand_UserNotFound(VertxTestContext testContext) {
        // Given
        UpdateUserCommand command = new UpdateUserCommand(
            "executor-user-id",
            testUserId.toString(),
            Optional.of("Jane"),
            Optional.empty(),
            Optional.empty(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.findById(testUserId))
            .thenReturn(Future.succeededFuture(Optional.empty()));

        // When
        Future<UserUpdateResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(updateResult -> testContext.verify(() -> {
            assertFalse(updateResult.isSuccess());
            assertEquals("User not found", updateResult.getErrorMessage());
            assertNull(updateResult.getUser());
            
            verify(userRepository).findById(testUserId);
            verifyNoMoreInteractions(userRepository);
            verifyNoInteractions(eventPublisher);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_UpdateUserCommand_EventPublishingFails_StillSucceeds(VertxTestContext testContext) {
        // Given
        UpdateUserCommand command = new UpdateUserCommand(
            "executor-user-id",
            testUserId.toString(),
            Optional.of("Jane"),
            Optional.empty(),
            Optional.empty(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.findById(testUserId))
            .thenReturn(Future.succeededFuture(Optional.of(testUser)));
        when(userRepository.save(any(User.class)))
            .thenReturn(Future.succeededFuture(testUser));
        when(eventPublisher.publish(any(UserUpdatedEvent.class)))
            .thenReturn(Future.failedFuture(new RuntimeException("Event publishing failed")));

        // When
        Future<UserUpdateResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(updateResult -> testContext.verify(() -> {
            assertTrue(updateResult.isSuccess());
            assertNotNull(updateResult.getUser());
            
            verify(userRepository).findById(testUserId);
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publish(any(UserUpdatedEvent.class));
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_UpdateUserCommand_InvalidUserId(VertxTestContext testContext) {
        // Given
        UpdateUserCommand command = new UpdateUserCommand(
            "executor-user-id",
            "invalid-uuid",
            Optional.of("Jane"),
            Optional.empty(),
            Optional.empty(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        // When
        Future<UserUpdateResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.failing(throwable -> testContext.verify(() -> {
            assertTrue(throwable instanceof UserNotFoundException);
            assertEquals("User update failed", throwable.getMessage());
            assertTrue(throwable.getCause() instanceof IllegalArgumentException);
            
            verifyNoInteractions(userRepository);
            verifyNoInteractions(eventPublisher);
            
            testContext.completeNow();
        })));
    }

    @Test
    void handle_UpdateUserCommand_RepositoryFailure(VertxTestContext testContext) {
        // Given
        UpdateUserCommand command = new UpdateUserCommand(
            "executor-user-id",
            testUserId.toString(),
            Optional.of("Jane"),
            Optional.empty(),
            Optional.empty(),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        when(userRepository.findById(testUserId))
            .thenReturn(Future.failedFuture(new RuntimeException("Database error")));

        // When
        Future<UserUpdateResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.failing(throwable -> testContext.verify(() -> {
            assertTrue(throwable instanceof UserNotFoundException);
            assertEquals("User update failed", throwable.getMessage());
            
            verify(userRepository).findById(testUserId);
            verifyNoMoreInteractions(userRepository);
            verifyNoInteractions(eventPublisher);
            
            testContext.completeNow();
        })));
    }

    @Test
    void getCommandType_ReturnsCorrectType() {
        // When
        Class<UpdateUserCommand> commandType = handler.getCommandType();

        // Then
        assertEquals(UpdateUserCommand.class, commandType);
    }
}