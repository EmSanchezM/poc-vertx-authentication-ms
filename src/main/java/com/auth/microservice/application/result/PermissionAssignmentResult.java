package com.auth.microservice.application.result;

import com.auth.microservice.domain.model.Role;

/**
 * Result of permission assignment operations to roles.
 * Contains the updated role with permissions or error information.
 */
public class PermissionAssignmentResult {
    private final boolean success;
    private final Role role;
    private final String errorMessage;
    private final int assignedCount;
    private final int skippedCount;

    private PermissionAssignmentResult(boolean success, Role role, String errorMessage, 
                                     int assignedCount, int skippedCount) {
        this.success = success;
        this.role = role;
        this.errorMessage = errorMessage;
        this.assignedCount = assignedCount;
        this.skippedCount = skippedCount;
    }

    public static PermissionAssignmentResult success(Role role, int assignedCount, int skippedCount) {
        return new PermissionAssignmentResult(true, role, null, assignedCount, skippedCount);
    }

    public static PermissionAssignmentResult failure(String errorMessage) {
        return new PermissionAssignmentResult(false, null, errorMessage, 0, 0);
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

    public int getAssignedCount() {
        return assignedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("PermissionAssignmentResult{success=true, roleId=%s, assigned=%d, skipped=%d}", 
                role != null ? role.getId() : "null", assignedCount, skippedCount);
        } else {
            return "PermissionAssignmentResult{success=false, error='" + errorMessage + "'}";
        }
    }
}