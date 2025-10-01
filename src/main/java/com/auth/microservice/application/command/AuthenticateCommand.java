package com.auth.microservice.application.command;

import com.auth.microservice.common.cqrs.Command;

import java.util.Objects;

/**
 * Command for user authentication (login).
 * Contains credentials and client information for security logging.
 */
public class AuthenticateCommand extends Command {
    private final String email;
    private final String password;
    private final String ipAddress;
    private final String userAgent;

    public AuthenticateCommand(String email, String password, String ipAddress, String userAgent) {
        super(null); // No userId yet since we're authenticating
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.password = Objects.requireNonNull(password, "Password cannot be null");
        this.ipAddress = ipAddress; // Can be null
        this.userAgent = userAgent; // Can be null
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public String toString() {
        return String.format("AuthenticateCommand{email='%s', ipAddress='%s', userAgent='%s', commandId=%s}", 
            email, ipAddress, userAgent, getCommandId());
    }
}