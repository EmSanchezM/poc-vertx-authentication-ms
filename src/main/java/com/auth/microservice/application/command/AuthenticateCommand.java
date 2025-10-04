package com.auth.microservice.application.command;

import com.auth.microservice.common.cqrs.Command;

import java.util.Objects;

/**
 * Command for user authentication (login).
 * Contains credentials and client information for security logging.
 * Supports authentication with either email or username.
 */
public class AuthenticateCommand extends Command {
    private final String usernameOrEmail;
    private final String password;
    private final String ipAddress;
    private final String userAgent;

    public AuthenticateCommand(String usernameOrEmail, String password, String ipAddress, String userAgent) {
        super(null); // No userId yet since we're authenticating
        this.usernameOrEmail = Objects.requireNonNull(usernameOrEmail, "UsernameOrEmail cannot be null");
        this.password = Objects.requireNonNull(password, "Password cannot be null");
        this.ipAddress = ipAddress; // Can be null
        this.userAgent = userAgent; // Can be null
    }

    public String getUsernameOrEmail() {
        return usernameOrEmail;
    }

    /**
     * @deprecated Use getUsernameOrEmail() instead. This method will be removed in task 3.
     */
    @Deprecated
    public String getEmail() {
        return usernameOrEmail;
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

    /**
     * Determines if the usernameOrEmail field contains an email address
     * @return true if it contains '@', false otherwise
     */
    public boolean isEmail() {
        return usernameOrEmail != null && usernameOrEmail.contains("@");
    }

    @Override
    public String toString() {
        String identifierType = isEmail() ? "email" : "username";
        return String.format("AuthenticateCommand{identifierType='%s', ipAddress='%s', userAgent='%s', commandId=%s}", 
            identifierType, ipAddress, userAgent, getCommandId());
    }
}