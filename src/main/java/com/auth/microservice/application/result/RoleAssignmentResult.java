package com.auth.microservice.application.result;

import com.auth.microservice.domain.model.User;

/**
 * Result of role assignment operations.
 * Contains the updated user with new role or error information.
 */
public class RoleAssignmentResult {
    private final boolean success;
    private final User user;
    private final String errorMessage;

    private RoleAssignmentResult(boolean success, User user, String errorMessage) {
        this.success = success;
        this.user = user;
        this.errorMessage = errorMessage;
    }

    public static RoleAssignmentResult success(User user) {
        return new RoleAssignmentResult(true, user, null);
    }

    public static RoleAssignmentResult failure(String errorMessage) {
        return new RoleAssignmentResult(false, null, errorMessage);
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
            return "RoleAssignmentResult{success=true, userId=" + (user != null ? user.getId() : "null") + "}";
        } else {
            return "RoleAssignmentResult{success=false, error='" + errorMessage + "'}";
        }
    }
}