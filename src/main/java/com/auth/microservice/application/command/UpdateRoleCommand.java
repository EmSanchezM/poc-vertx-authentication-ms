package com.auth.microservice.application.command;

import com.auth.microservice.common.cqrs.Command;

import java.util.Objects;
import java.util.UUID;

/**
 * Command for updating an existing role in the RBAC system.
 * This command allows administrators to modify role description and other properties.
 */
public class UpdateRoleCommand extends Command {
    private final UUID roleId;
    private final String description;
    private final String ipAddress;
    private final String userAgent;

    public UpdateRoleCommand(String adminUserId, UUID roleId, String description, 
                           String ipAddress, String userAgent) {
        super(adminUserId);
        this.roleId = Objects.requireNonNull(roleId, "Role ID cannot be null");
        this.description = description;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public String getDescription() {
        return description;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public String toString() {
        return String.format("UpdateRoleCommand{roleId=%s, description='%s', commandId=%s}", 
            roleId, description, getCommandId());
    }
}