package com.auth.microservice.application.result;

import com.auth.microservice.domain.model.User;

/**
 * Result of user update operations.
 * Contains the updated user or error information.
 */
public class UserUpdateResult {
    private final boolean success;
    private final User user;
    private final String errorMessage;

    private UserUpdateResult(boolean success, User user, String errorMessage) {
        this.success = success;
        this.user = user;
        this.errorMessage = errorMessage;
    }

    public static UserUpdateResult success(User user) {
        return new UserUpdateResult(true, user, null);
    }

    public static UserUpdateResult failure(String errorMessage) {
        return new UserUpdateResult(false, null, errorMessage);
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
            return "UserUpdateResult{success=true, userId=" + (user != null ? user.getId() : "null") + "}";
        } else {
            return "UserUpdateResult{success=false, error='" + errorMessage + "'}";
        }
    }
}