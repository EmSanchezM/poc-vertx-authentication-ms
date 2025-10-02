package com.auth.microservice.application.handler;

import com.auth.microservice.common.cqrs.CommandHandler;
import com.auth.microservice.application.command.AssignPermissionCommand;
import com.auth.microservice.application.result.PermissionAssignmentResult;
import com.auth.microservice.domain.event.PermissionsAssignedEvent;
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
 * Command handler for permission assignment operations.
 * Handles permission assignments to roles with RBAC validations and inheritance logic.
 */
public class AssignPermissionCommandHandler implements CommandHandler<AssignPermissionCommand, PermissionAssignmentResult> {

    private static final Logger logger = LoggerFactory.getLogger(AssignPermissionCommandHandler.class);

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final EventPublisher eventPublisher;

    public AssignPermissionCommandHandler(RoleRepository roleRepository, 
                                        PermissionRepository permissionRepository,
                                        EventPublisher eventPublisher) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Handles permission assignment command with RBAC validations and inheritance logic.
     */
    @Override
    public Future<PermissionAssignmentResult> handle(AssignPermissionCommand command) {
        logger.info("Processing permission assignment for role: {} with {} permissions", 
            command.getRoleId(), command.getPermissionNames().size());
        
        return roleRepository.findByIdWithPermissions(command.getRoleId())
            .compose(roleOpt -> {
                if (roleOpt.isEmpty()) {
                    logger.warn("Permission assignment failed: Role not found: {}", command.getRoleId());
                    return Future.succeededFuture(PermissionAssignmentResult.failure("Role not found"));
                }
                
                Role role = roleOpt.get();
                Set<String> currentPermissionNames = role.getPermissions().stream()
                    .map(Permission::getName)
                    .collect(Collectors.toSet());
                
                return assignPermissionsToRoleWithInheritance(role, command.getPermissionNames(), 
                                                            command.isReplaceExisting())
                    .compose(updatedRole -> roleRepository.save(updatedRole))
                    .compose(savedRole -> {
                        // Calculate what was actually assigned and removed
                        Set<String> newPermissionNames = savedRole.getPermissions().stream()
                            .map(Permission::getName)
                            .collect(Collectors.toSet());
                        
                        Set<String> assignedPermissions = new HashSet<>(newPermissionNames);
                        assignedPermissions.removeAll(currentPermissionNames);
                        
                        Set<String> removedPermissions = new HashSet<>(currentPermissionNames);
                        removedPermissions.removeAll(newPermissionNames);
                        
                        return publishPermissionsAssignedEvent(savedRole, assignedPermissions, 
                                                             removedPermissions, command)
                            .compose(v -> {
                                int assignedCount = assignedPermissions.size();
                                int skippedCount = command.getPermissionNames().size() - assignedCount;
                                
                                logger.info("Permission assignment completed for role: {} - assigned: {}, skipped: {}", 
                                    savedRole.getName(), assignedCount, skippedCount);
                                
                                return Future.succeededFuture(
                                    PermissionAssignmentResult.success(savedRole, assignedCount, skippedCount));
                            });
                    });
            })
            .recover(throwable -> {
                logger.error("Permission assignment error for role: {}", command.getRoleId(), throwable);
                return Future.succeededFuture(PermissionAssignmentResult.failure("Permission assignment failed: " + throwable.getMessage()));
            });
    }

    /**
     * Assigns permissions to a role with inheritance logic and replace option.
     * This method implements RBAC permission inheritance rules.
     */
    private Future<Role> assignPermissionsToRoleWithInheritance(Role role, Set<String> permissionNames, 
                                                               boolean replaceExisting) {
        List<Future<Optional<Permission>>> permissionFutures = permissionNames.stream()
            .map(permissionRepository::findByName)
            .collect(Collectors.toList());
        
        return Future.all(permissionFutures)
            .compose(compositeFuture -> {
                Set<Permission> newPermissions = new HashSet<>();
                
                for (int i = 0; i < compositeFuture.size(); i++) {
                    Optional<Permission> permissionOpt = compositeFuture.resultAt(i);
                    if (permissionOpt.isPresent()) {
                        newPermissions.add(permissionOpt.get());
                    } else {
                        String permissionName = permissionNames.stream().skip(i).findFirst().orElse("unknown");
                        logger.warn("Permission not found during assignment: {}", permissionName);
                    }
                }
                
                if (replaceExisting) {
                    // Replace all existing permissions
                    role.clearPermissions();
                    role.addPermissions(newPermissions);
                    logger.debug("Replaced all permissions for role: {} with {} new permissions", 
                        role.getName(), newPermissions.size());
                } else {
                    // Add to existing permissions (inheritance logic)
                    role.addPermissions(newPermissions);
                    logger.debug("Added {} permissions to role: {} (total: {})", 
                        newPermissions.size(), role.getName(), role.getPermissions().size());
                }
                
                return Future.succeededFuture(role);
            });
    }

    /**
     * Publishes permissions assigned event for audit purposes.
     */
    private Future<Void> publishPermissionsAssignedEvent(Role role, Set<String> assignedPermissions, 
                                                        Set<String> removedPermissions, 
                                                        AssignPermissionCommand command) {
        PermissionsAssignedEvent event = new PermissionsAssignedEvent(
            role.getId().toString(),
            role.getName(),
            assignedPermissions,
            removedPermissions,
            command.isReplaceExisting(),
            command.getUserId(),
            command.getIpAddress(),
            command.getUserAgent()
        );
        
        return eventPublisher.publish(event)
            .recover(throwable -> {
                logger.warn("Failed to publish permissions assigned event for role: {}", role.getId(), throwable);
                return Future.succeededFuture(); // Don't fail the operation if event publishing fails
            });
    }

    @Override
    public Class<AssignPermissionCommand> getCommandType() {
        return AssignPermissionCommand.class;
    }
}