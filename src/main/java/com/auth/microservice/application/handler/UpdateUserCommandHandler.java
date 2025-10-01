package com.auth.microservice.application.handler;

import com.auth.microservice.common.cqrs.CommandHandler;
import com.auth.microservice.application.command.UpdateUserCommand;
import com.auth.microservice.application.result.UserUpdateResult;
import com.auth.microservice.domain.event.UserUpdatedEvent;
import com.auth.microservice.domain.exception.UserNotFoundException;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.EventPublisher;
import com.auth.microservice.domain.port.UserRepository;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Command handler for user update operations.
 * Handles user profile updates with business validation and audit events.
 */
public class UpdateUserCommandHandler implements CommandHandler<UpdateUserCommand, UserUpdateResult> {

    private static final Logger logger = LoggerFactory.getLogger(UpdateUserCommandHandler.class);

    private final UserRepository userRepository;
    private final EventPublisher eventPublisher;

    public UpdateUserCommandHandler(UserRepository userRepository, EventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Future<UserUpdateResult> handle(UpdateUserCommand command) {
        logger.info("Processing user update for user: {}", command.getTargetUserId());
        
        try {
            UUID targetUserId = UUID.fromString(command.getTargetUserId());
            
            return userRepository.findById(targetUserId)
                .compose(userOpt -> {
                    if (userOpt.isEmpty()) {
                        logger.warn("User update failed: User not found: {}", command.getTargetUserId());
                        return Future.succeededFuture(UserUpdateResult.failure("User not found"));
                    }
                    
                    User user = userOpt.get();
                    Map<String, Object> changedFields = new HashMap<>();
                    
                    // Update fields if provided
                    if (command.getFirstName().isPresent() || command.getLastName().isPresent()) {
                        String newFirstName = command.getFirstName().orElse(user.getFirstName());
                        String newLastName = command.getLastName().orElse(user.getLastName());
                        
                        if (!newFirstName.equals(user.getFirstName())) {
                            changedFields.put("firstName", Map.of("old", user.getFirstName(), "new", newFirstName));
                        }
                        if (!newLastName.equals(user.getLastName())) {
                            changedFields.put("lastName", Map.of("old", user.getLastName(), "new", newLastName));
                        }
                        
                        user.updateProfile(newFirstName, newLastName);
                    }
                    
                    if (command.getIsActive().isPresent()) {
                        boolean newActiveStatus = command.getIsActive().get();
                        if (newActiveStatus != user.isActive()) {
                            changedFields.put("isActive", Map.of("old", user.isActive(), "new", newActiveStatus));
                            if (newActiveStatus) {
                                user.activate();
                            } else {
                                user.deactivate();
                            }
                        }
                    }
                    
                    if (changedFields.isEmpty()) {
                        logger.info("No changes detected for user: {}", command.getTargetUserId());
                        return Future.succeededFuture(UserUpdateResult.success(user));
                    }
                    
                    return userRepository.save(user)
                        .compose(savedUser -> publishUserUpdatedEvent(savedUser, changedFields, command))
                        .compose(savedUser -> {
                            logger.info("User updated successfully: {}", savedUser.getId());
                            return Future.succeededFuture(UserUpdateResult.success(savedUser));
                        });
                })
                .recover(throwable -> {
                    if (throwable instanceof IllegalArgumentException) {
                        logger.warn("User update failed: Invalid input for user: {}", command.getTargetUserId(), throwable);
                        return Future.succeededFuture(UserUpdateResult.failure(throwable.getMessage()));
                    }
                    logger.error("User update error for user: {}", command.getTargetUserId(), throwable);
                    return Future.failedFuture(new UserNotFoundException("User update failed", throwable));
                });
                
        } catch (Exception e) {
            logger.error("User update error for user: {}", command.getTargetUserId(), e);
            return Future.failedFuture(new UserNotFoundException("User update failed", e));
        }
    }

    /**
     * Publishes user updated event for audit purposes.
     */
    private Future<User> publishUserUpdatedEvent(User user, Map<String, Object> changedFields, UpdateUserCommand command) {
        UserUpdatedEvent event = new UserUpdatedEvent(
            user.getId().toString(),
            user.getUsername(),
            user.getEmail().getValue(),
            changedFields,
            command.getUserId(),
            command.getIpAddress(),
            command.getUserAgent()
        );
        
        return eventPublisher.publish(event)
            .compose(v -> Future.succeededFuture(user))
            .recover(throwable -> {
                logger.warn("Failed to publish user updated event for user: {}", user.getId(), throwable);
                return Future.succeededFuture(user); // Don't fail the operation if event publishing fails
            });
    }

    @Override
    public Class<UpdateUserCommand> getCommandType() {
        return UpdateUserCommand.class;
    }
}