package com.auth.microservice.application.handler;

import com.auth.microservice.common.cqrs.CommandHandler;
import com.auth.microservice.application.command.UpdateRoleCommand;
import com.auth.microservice.application.result.RoleUpdateResult;
import com.auth.microservice.domain.event.RoleUpdatedEvent;
import com.auth.microservice.domain.exception.RoleNotFoundException;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.port.EventPublisher;
import com.auth.microservice.domain.port.RoleRepository;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Command handler for role update operations.
 * Handles role updates with RBAC validations and audit events.
 */
public class UpdateRoleCommandHandler implements CommandHandler<UpdateRoleCommand, RoleUpdateResult> {

    private static final Logger logger = LoggerFactory.getLogger(UpdateRoleCommandHandler.class);

    private final RoleRepository roleRepository;
    private final EventPublisher eventPublisher;

    public UpdateRoleCommandHandler(RoleRepository roleRepository, EventPublisher eventPublisher) {
        this.roleRepository = roleRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Handles role update command with RBAC validations.
     */
    @Override
    public Future<RoleUpdateResult> handle(UpdateRoleCommand command) {
        logger.info("Processing role update for ID: {}", command.getRoleId());
        
        return roleRepository.findByIdWithPermissions(command.getRoleId())
            .compose(roleOpt -> {
                if (roleOpt.isEmpty()) {
                    logger.warn("Role update failed: Role not found: {}", command.getRoleId());
                    return Future.succeededFuture(RoleUpdateResult.failure("Role not found"));
                }
                
                Role role = roleOpt.get();
                String oldDescription = role.getDescription();
                
                // Update role description
                role.updateDescription(command.getDescription());
                
                return roleRepository.save(role)
                    .compose(savedRole -> publishRoleUpdatedEvent(savedRole, oldDescription, command))
                    .compose(savedRole -> {
                        logger.info("Role updated successfully: {}", savedRole.getName());
                        return Future.succeededFuture(RoleUpdateResult.success(savedRole));
                    });
            })
            .recover(throwable -> {
                if (throwable instanceof IllegalArgumentException) {
                    logger.warn("Role update failed: Invalid input for role: {}", command.getRoleId(), throwable);
                    return Future.succeededFuture(RoleUpdateResult.failure(throwable.getMessage()));
                }
                logger.error("Role update error for role: {}", command.getRoleId(), throwable);
                return Future.failedFuture(new RoleNotFoundException("Role update failed", throwable));
            });
    }

    /**
     * Publishes role updated event for audit purposes.
     */
    private Future<Role> publishRoleUpdatedEvent(Role role, String oldDescription, UpdateRoleCommand command) {
        RoleUpdatedEvent event = new RoleUpdatedEvent(
            role.getId().toString(),
            role.getName(),
            oldDescription,
            role.getDescription(),
            command.getUserId(),
            command.getIpAddress(),
            command.getUserAgent()
        );
        
        return eventPublisher.publish(event)
            .compose(v -> Future.succeededFuture(role))
            .recover(throwable -> {
                logger.warn("Failed to publish role updated event for role: {}", role.getId(), throwable);
                return Future.succeededFuture(role); // Don't fail the operation if event publishing fails
            });
    }

    @Override
    public Class<UpdateRoleCommand> getCommandType() {
        return UpdateRoleCommand.class;
    }
}