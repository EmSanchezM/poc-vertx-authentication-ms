package com.auth.microservice.application.handler;

import com.auth.microservice.common.cqrs.CommandHandler;
import com.auth.microservice.application.command.AssignRoleCommand;
import com.auth.microservice.application.result.RoleAssignmentResult;
import com.auth.microservice.domain.event.RoleAssignedEvent;
import com.auth.microservice.domain.exception.RoleNotFoundException;
import com.auth.microservice.domain.exception.UserNotFoundException;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.EventPublisher;
import com.auth.microservice.domain.port.RoleRepository;
import com.auth.microservice.domain.port.UserRepository;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * Command handler for role assignment operations.
 * Handles role assignments to users with business validation and audit events.
 */
public class AssignRoleCommandHandler implements CommandHandler<AssignRoleCommand, RoleAssignmentResult> {

    private static final Logger logger = LoggerFactory.getLogger(AssignRoleCommandHandler.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EventPublisher eventPublisher;

    public AssignRoleCommandHandler(UserRepository userRepository, 
                                  RoleRepository roleRepository,
                                  EventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Future<RoleAssignmentResult> handle(AssignRoleCommand command) {
        logger.info("Processing role assignment for user: {} role: {}", command.getTargetUserId(), command.getRoleId());
        
        try {
            UUID targetUserId = UUID.fromString(command.getTargetUserId());
            UUID roleId = UUID.fromString(command.getRoleId());
            
            return Future.all(
                userRepository.findByIdWithRoles(targetUserId),
                roleRepository.findByIdWithPermissions(roleId)
            ).compose(compositeFuture -> {
                Optional<User> userOpt = compositeFuture.resultAt(0);
                Optional<Role> roleOpt = compositeFuture.resultAt(1);
                
                if (userOpt.isEmpty()) {
                    logger.warn("Role assignment failed: User not found: {}", command.getTargetUserId());
                    return Future.succeededFuture(RoleAssignmentResult.failure("User not found"));
                }
                
                if (roleOpt.isEmpty()) {
                    logger.warn("Role assignment failed: Role not found: {}", command.getRoleId());
                    return Future.succeededFuture(RoleAssignmentResult.failure("Role not found"));
                }
                
                User user = userOpt.get();
                Role role = roleOpt.get();
                
                // Check if user already has this role
                if (user.getRoles().contains(role)) {
                    logger.info("User already has role: {} for user: {}", role.getName(), user.getId());
                    return Future.succeededFuture(RoleAssignmentResult.success(user));
                }
                
                user.addRole(role);
                
                return userRepository.save(user)
                    .compose(savedUser -> publishRoleAssignedEvent(savedUser, role, command))
                    .compose(savedUser -> {
                        logger.info("Role assigned successfully: {} to user: {}", role.getName(), savedUser.getId());
                        return Future.succeededFuture(RoleAssignmentResult.success(savedUser));
                    });
            })
            .recover(throwable -> {
                if (throwable instanceof IllegalArgumentException) {
                    logger.warn("Role assignment failed: Invalid input", throwable);
                    return Future.succeededFuture(RoleAssignmentResult.failure(throwable.getMessage()));
                }
                logger.error("Role assignment error for user: {} role: {}", command.getTargetUserId(), command.getRoleId(), throwable);
                return Future.failedFuture(new RoleNotFoundException("Role assignment failed", throwable));
            });
                
        } catch (Exception e) {
            logger.error("Role assignment error for user: {} role: {}", command.getTargetUserId(), command.getRoleId(), e);
            return Future.failedFuture(new RoleNotFoundException("Role assignment failed", e));
        }
    }

    /**
     * Publishes role assigned event for audit purposes.
     */
    private Future<User> publishRoleAssignedEvent(User user, Role role, AssignRoleCommand command) {
        RoleAssignedEvent event = new RoleAssignedEvent(
            user.getId().toString(),
            user.getUsername(),
            user.getEmail().getValue(),
            role.getId().toString(),
            role.getName(),
            command.getUserId(),
            command.getIpAddress(),
            command.getUserAgent()
        );
        
        return eventPublisher.publish(event)
            .compose(v -> Future.succeededFuture(user))
            .recover(throwable -> {
                logger.warn("Failed to publish role assigned event for user: {}", user.getId(), throwable);
                return Future.succeededFuture(user); // Don't fail the operation if event publishing fails
            });
    }

    @Override
    public Class<AssignRoleCommand> getCommandType() {
        return AssignRoleCommand.class;
    }
}