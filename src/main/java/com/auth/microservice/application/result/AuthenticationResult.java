package com.auth.microservice.application.result;

import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.service.JWTService.TokenPair;

import java.util.Objects;

/**
 * Result of authentication command execution.
 * Contains the authenticated user and generated tokens.
 */
public class AuthenticationResult {
    private final User user;
    private final TokenPair tokenPair;
    private final boolean success;
    private final String message;

    private AuthenticationResult(User user, TokenPair tokenPair, boolean success, String message) {
        this.user = user;
        this.tokenPair = tokenPair;
        this.success = success;
        this.message = message;
    }

    public static AuthenticationResult success(User user, TokenPair tokenPair) {
        return new AuthenticationResult(
            Objects.requireNonNull(user, "User cannot be null"),
            Objects.requireNonNull(tokenPair, "Token pair cannot be null"),
            true,
            "Authentication successful"
        );
    }

    public static AuthenticationResult failure(String message) {
        return new AuthenticationResult(
            null,
            null,
            false,
            Objects.requireNonNull(message, "Message cannot be null")
        );
    }

    public User getUser() {
        return user;
    }

    public TokenPair getTokenPair() {
        return tokenPair;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("AuthenticationResult{success=%s, message='%s', user=%s}", 
            success, message, user != null ? user.getEmail() : "null");
    }
}