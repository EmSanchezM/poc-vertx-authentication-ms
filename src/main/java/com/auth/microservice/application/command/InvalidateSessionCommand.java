package com.auth.microservice.application.command;

import com.auth.microservice.common.cqrs.Command;

import java.util.Objects;

/**
 * Command for invalidating a specific session by token.
 * Used when a user logs out or when a session needs to be terminated.
 */
public class InvalidateSessionCommand extends Command {
    private final String token;
    private final String reason;
    private final String ipAddress;

    public InvalidateSessionCommand(String userId, String token, String reason, String ipAddress) {
        super(userId);
        this.token = Objects.requireNonNull(token, "Token cannot be null");
        this.reason = reason != null ? reason : "User logout";
        this.ipAddress = ipAddress;
    }

    public InvalidateSessionCommand(String userId, String token) {
        this(userId, token, "User logout", null);
    }

    public String getToken() {
        return token;
    }

    public String getReason() {
        return reason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    @Override
    public String toString() {
        return String.format("InvalidateSessionCommand{reason='%s', ipAddress='%s', commandId=%s}", 
            reason, ipAddress, getCommandId());
    }
}