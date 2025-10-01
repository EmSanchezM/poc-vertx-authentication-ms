package com.auth.microservice.application.command;

import com.auth.microservice.common.cqrs.Command;

import java.util.Objects;

/**
 * Command for refreshing an access token using a valid refresh token.
 * Used to obtain a new access token without requiring user credentials.
 */
public class RefreshTokenCommand extends Command {
    private final String refreshToken;
    private final String ipAddress;
    private final String userAgent;

    public RefreshTokenCommand(String refreshToken, String ipAddress, String userAgent) {
        super(null); // UserId will be extracted from the refresh token
        this.refreshToken = Objects.requireNonNull(refreshToken, "Refresh token cannot be null");
        this.ipAddress = ipAddress; // Can be null
        this.userAgent = userAgent; // Can be null
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public String toString() {
        return String.format("RefreshTokenCommand{ipAddress='%s', userAgent='%s', commandId=%s}", 
            ipAddress, userAgent, getCommandId());
    }
}