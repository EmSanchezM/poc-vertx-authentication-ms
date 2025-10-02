package com.auth.microservice.application.query;

import com.auth.microservice.common.cqrs.Query;

import java.util.UUID;

/**
 * Query to get all roles assigned to a specific user.
 * Supports optional loading of permissions for optimization.
 */
public class GetUserRolesQuery extends Query {
    private final UUID targetUserId;
    private final boolean includePermissions;

    public GetUserRolesQuery(String userId, UUID targetUserId, boolean includePermissions) {
        super(userId);
        this.targetUserId = targetUserId;
        this.includePermissions = includePermissions;
    }

    public GetUserRolesQuery(String userId, UUID targetUserId) {
        this(userId, targetUserId, false);
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public boolean isIncludePermissions() {
        return includePermissions;
    }

    @Override
    public String toString() {
        return String.format("GetUserRolesQuery{targetUserId=%s, includePermissions=%s, %s}", 
            targetUserId, includePermissions, super.toString());
    }
}