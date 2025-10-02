package com.auth.microservice.application.command;

import com.auth.microservice.common.cqrs.Command;

import java.util.Objects;
import java.util.Set;

/**
 * Command for creating a new role in the RBAC system.
 * This command is used by administrators to create roles with optional permissions.
 */
public class CreateRoleCommand extends Command {
    private final String name;
    private final String description;
    private final Set<String> permissionNames;
    private final String ipAddress;
    private final String userAgent;

    public CreateRoleCommand(String adminUserId, String name, String description, 
                           Set<String> permissionNames, String ipAddress, String userAgent) {
        super(adminUserId);
        this.name = Objects.requireNonNull(name, "Role name cannot be null");
        this.description = description;
        this.permissionNames = permissionNames != null ? Set.copyOf(permissionNames) : Set.of();
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getPermissionNames() {
        return permissionNames;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public String toString() {
        return String.format("CreateRoleCommand{name='%s', description='%s', permissions=%s, commandId=%s}", 
            name, description, permissionNames, getCommandId());
    }
}