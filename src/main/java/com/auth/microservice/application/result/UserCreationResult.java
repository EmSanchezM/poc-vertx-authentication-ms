package com.auth.microservice.application.result;

import com.auth.microservice.domain.model.User;

/**
 * Result of user creation operations.
 * Contains the created user or error information.
 */
public class UserCreationResult {
    private final boolean success;
    private final User user;
    private final String errorMessage;

    private UserCreationResult(boolean success, User user, String errorMessage) {
        this.success = success;
        this.user = user;
        this.errorMessage = errorMessage;
    }

    public static UserCreationResult success(User user) {
        return new UserCreationResult(true, user, null);
    }

    public static UserCreationResult failure(String errorMessage) {
        return new UserCreationResult(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public User getUser() {
        return user;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (success) {
            return "UserCreationResult{success=true, userId=" + (user != null ? user.getId() : "null") + "}";
        } else {
            return "UserCreationResult{success=false, error='" + errorMessage + "'}";
        }
    }
}