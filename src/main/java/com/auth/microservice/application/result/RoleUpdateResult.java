package com.auth.microservice.application.result;

import com.auth.microservice.domain.model.Role;

/**
 * Result of role update operations.
 * Contains the updated role or error information.
 */
public class RoleUpdateResult {
    private final boolean success;
    private final Role role;
    private final String errorMessage;

    private RoleUpdateResult(boolean success, Role role, String errorMessage) {
        this.success = success;
        this.role = role;
        this.errorMessage = errorMessage;
    }

    public static RoleUpdateResult success(Role role) {
        return new RoleUpdateResult(true, role, null);
    }

    public static RoleUpdateResult failure(String errorMessage) {
        return new RoleUpdateResult(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public Role getRole() {
        return role;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (success) {
            return "RoleUpdateResult{success=true, roleId=" + (role != null ? role.getId() : "null") + "}";
        } else {
            return "RoleUpdateResult{success=false, error='" + errorMessage + "'}";
        }
    }
}