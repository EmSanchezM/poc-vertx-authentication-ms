package com.auth.microservice.application.command;

import com.auth.microservice.common.cqrs.Command;

import java.util.Objects;

/**
 * Command for assigning a role to a user.
 * Used for role-based access control management.
 */
public class AssignRoleCommand extends Command {
    private final String targetUserId;
    private final String roleId;
    private final String ipAddress;
    private final String userAgent;

    public AssignRoleCommand(String executorUserId, String targetUserId, String roleId,
                           String ipAddress, String userAgent) {
        super(executorUserId);
        this.targetUserId = Objects.requireNonNull(targetUserId, "Target user ID cannot be null");
        this.roleId = Objects.requireNonNull(roleId, "Role ID cannot be null");
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public String getRoleId() {
        return roleId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public String toString() {
        return String.format("AssignRoleCommand{targetUserId='%s', roleId='%s', commandId=%s}", 
            targetUserId, roleId, getCommandId());
    }
}