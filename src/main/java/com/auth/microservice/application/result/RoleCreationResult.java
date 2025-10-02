package com.auth.microservice.application.result;

import com.auth.microservice.domain.model.Role;

/**
 * Result of role creation operations.
 * Contains the created role or error information.
 */
public class RoleCreationResult {
    private final boolean success;
    private final Role role;
    private final String errorMessage;

    private RoleCreationResult(boolean success, Role role, String errorMessage) {
        this.success = success;
        this.role = role;
        this.errorMessage = errorMessage;
    }

    public static RoleCreationResult success(Role role) {
        return new RoleCreationResult(true, role, null);
    }

    public static RoleCreationResult failure(String errorMessage) {
        return new RoleCreationResult(false, null, errorMessage);
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
            return "RoleCreationResult{success=true, roleId=" + (role != null ? role.getId() : "null") + "}";
        } else {
            return "RoleCreationResult{success=false, error='" + errorMessage + "'}";
        }
    }
}