package com.auth.microservice.application.command;

import com.auth.microservice.common.cqrs.Command;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Command for assigning permissions to a role in the RBAC system.
 * This command allows administrators to manage role-permission relationships.
 */
public class AssignPermissionCommand extends Command {
    private final UUID roleId;
    private final Set<String> permissionNames;
    private final boolean replaceExisting;
    private final String ipAddress;
    private final String userAgent;

    public AssignPermissionCommand(String adminUserId, UUID roleId, Set<String> permissionNames, 
                                 boolean replaceExisting, String ipAddress, String userAgent) {
        super(adminUserId);
        this.roleId = Objects.requireNonNull(roleId, "Role ID cannot be null");
        this.permissionNames = Objects.requireNonNull(permissionNames, "Permission names cannot be null");
        this.replaceExisting = replaceExisting;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public Set<String> getPermissionNames() {
        return Set.copyOf(permissionNames);
    }

    public boolean isReplaceExisting() {
        return replaceExisting;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public String toString() {
        return String.format("AssignPermissionCommand{roleId=%s, permissions=%s, replace=%s, commandId=%s}", 
            roleId, permissionNames, replaceExisting, getCommandId());
    }
}