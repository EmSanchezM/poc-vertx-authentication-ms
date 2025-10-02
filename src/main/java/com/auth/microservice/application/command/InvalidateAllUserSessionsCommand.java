package com.auth.microservice.application.command;

import com.auth.microservice.common.cqrs.Command;

import java.util.Objects;
import java.util.UUID;

/**
 * Command for invalidating all sessions for a specific user.
 * Used for security purposes when suspicious activity is detected or when user requests to logout from all devices.
 */
public class InvalidateAllUserSessionsCommand extends Command {
    private final UUID targetUserId;
    private final String reason;
    private final String ipAddress;
    private final boolean excludeCurrentSession;
    private final String currentSessionToken;

    public InvalidateAllUserSessionsCommand(String userId, UUID targetUserId, String reason, 
                                          String ipAddress, boolean excludeCurrentSession, String currentSessionToken) {
        super(userId);
        this.targetUserId = Objects.requireNonNull(targetUserId, "Target user ID cannot be null");
        this.reason = reason != null ? reason : "Security measure";
        this.ipAddress = ipAddress;
        this.excludeCurrentSession = excludeCurrentSession;
        this.currentSessionToken = currentSessionToken;
    }

    public InvalidateAllUserSessionsCommand(String userId, UUID targetUserId, String reason) {
        this(userId, targetUserId, reason, null, false, null);
    }

    public InvalidateAllUserSessionsCommand(String userId, UUID targetUserId) {
        this(userId, targetUserId, "Security measure", null, false, null);
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public String getReason() {
        return reason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public boolean isExcludeCurrentSession() {
        return excludeCurrentSession;
    }

    public String getCurrentSessionToken() {
        return currentSessionToken;
    }

    @Override
    public String toString() {
        return String.format("InvalidateAllUserSessionsCommand{targetUserId=%s, reason='%s', excludeCurrentSession=%s, commandId=%s}", 
            targetUserId, reason, excludeCurrentSession, getCommandId());
    }
}