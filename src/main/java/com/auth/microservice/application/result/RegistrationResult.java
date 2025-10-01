package com.auth.microservice.application.result;

import com.auth.microservice.domain.model.User;

import java.util.Objects;

/**
 * Result of user registration command execution.
 * Contains the created user and operation status.
 */
public class RegistrationResult {
    private final User user;
    private final boolean success;
    private final String message;

    private RegistrationResult(User user, boolean success, String message) {
        this.user = user;
        this.success = success;
        this.message = message;
    }

    public static RegistrationResult success(User user) {
        return new RegistrationResult(
            Objects.requireNonNull(user, "User cannot be null"),
            true,
            "User registered successfully"
        );
    }

    public static RegistrationResult failure(String message) {
        return new RegistrationResult(
            null,
            false,
            Objects.requireNonNull(message, "Message cannot be null")
        );
    }

    public User getUser() {
        return user;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("RegistrationResult{success=%s, message='%s', user=%s}", 
            success, message, user != null ? user.getEmail() : "null");
    }
}