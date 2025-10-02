package com.auth.microservice.application.handler;

import com.auth.microservice.common.cqrs.CommandHandler;
import com.auth.microservice.application.command.CreateRoleCommand;
import com.auth.microservice.application.result.RoleCreationResult;
import com.auth.microservice.domain.event.RoleCreatedEvent;
import com.auth.microservice.domain.exception.RoleAlreadyExistsException;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.port.EventPublisher;
import com.auth.microservice.domain.port.PermissionRepository;
import com.auth.microservice.domain.port.RoleRepository;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Command handler for role creation operations.
 * Handles role creation with RBAC validations and permission inheritance.
 */
public class CreateRoleCommandHandler implements CommandHandler<CreateRoleCommand, RoleCreationResult> {

    private static final Logger logger = LoggerFactory.getLogger(CreateRoleCommandHandler.class);

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final EventPublisher eventPublisher;

    public CreateRoleCommandHandler(RoleRepository roleRepository, 
                                  PermissionRepository permissionRepository,
                                  EventPublisher eventPublisher) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Handles role creation command with RBAC validations.
     */
    @Override
    public Future<RoleCreationResult> handle(CreateRoleCommand command) {
        logger.info("Processing role creation for name: {}", command.getName());
        
        try {
            // Check if role already exists
            return roleRepository.existsByName(command.getName())
                .compose(exists -> {
                    if (exists) {
                        logger.warn("Role creation failed: Role already exists: {}", command.getName());
                        return Future.succeededFuture(RoleCreationResult.failure("Role already exists"));
                    }
                    
                    // Create the role
                    Role role = new Role(command.getName(), command.getDescription());
                    
                    // Assign initial permissions if specified
                    if (!command.getPermissionNames().isEmpty()) {
                        return assignPermissionsToRole(role, command.getPermissionNames())
                            .compose(roleWithPermissions -> roleRepository.save(roleWithPermissions))
                            .compose(savedRole -> publishRoleCreatedEvent(savedRole, command))
                            .compose(savedRole -> {
                                logger.info("Role created successfully: {} with {} permissions", 
                                    savedRole.getName(), savedRole.getPermissions().size());
                                return Future.succeededFuture(RoleCreationResult.success(savedRole));
                            });
                    } else {
                        return roleRepository.save(role)
                            .compose(savedRole -> publishRoleCreatedEvent(savedRole, command))
                            .compose(savedRole -> {
                                logger.info("Role created successfully: {}", savedRole.getName());
                                return Future.succeededFuture(RoleCreationResult.success(savedRole));
                            });
                    }
                })
                .recover(throwable -> {
                    if (throwable instanceof IllegalArgumentException) {
                        logger.warn("Role creation failed: Invalid input for role: {}", command.getName(), throwable);
                        return Future.succeededFuture(RoleCreationResult.failure(throwable.getMessage()));
                    }
                    logger.error("Role creation error for role: {}", command.getName(), throwable);
                    return Future.failedFuture(new RoleAlreadyExistsException("Role creation failed", throwable));
                });
                
        } catch (Exception e) {
            logger.error("Role creation error for role: {}", command.getName(), e);
            return Future.failedFuture(new RoleAlreadyExistsException("Role creation failed", e));
        }
    }

    /**
     * Assigns permissions to a role by permission names.
     */
    private Future<Role> assignPermissionsToRole(Role role, Set<String> permissionNames) {
        List<Future<Optional<Permission>>> permissionFutures = permissionNames.stream()
            .map(permissionRepository::findByName)
            .collect(Collectors.toList());
        
        return Future.all(permissionFutures)
            .compose(compositeFuture -> {
                Set<Permission> permissions = new HashSet<>();
                
                for (int i = 0; i < compositeFuture.size(); i++) {
                    Optional<Permission> permissionOpt = compositeFuture.resultAt(i);
                    if (permissionOpt.isPresent()) {
                        permissions.add(permissionOpt.get());
                    } else {
                        String permissionName = permissionNames.stream().skip(i).findFirst().orElse("unknown");
                        logger.warn("Permission not found: {}", permissionName);
                    }
                }
                
                role.addPermissions(permissions);
                return Future.succeededFuture(role);
            });
    }

    /**
     * Publishes role created event for audit purposes.
     */
    private Future<Role> publishRoleCreatedEvent(Role role, CreateRoleCommand command) {
        Set<String> permissionNames = role.getPermissions().stream()
            .map(Permission::getName)
            .collect(Collectors.toSet());
            
        RoleCreatedEvent event = new RoleCreatedEvent(
            role.getId().toString(),
            role.getName(),
            role.getDescription(),
            permissionNames,
            command.getUserId(),
            command.getIpAddress(),
            command.getUserAgent()
        );
        
        return eventPublisher.publish(event)
            .compose(v -> Future.succeededFuture(role))
            .recover(throwable -> {
                logger.warn("Failed to publish role created event for role: {}", role.getId(), throwable);
                return Future.succeededFuture(role); // Don't fail the operation if event publishing fails
            });
    }

    @Override
    public Class<CreateRoleCommand> getCommandType() {
        return CreateRoleCommand.class;
    }
}