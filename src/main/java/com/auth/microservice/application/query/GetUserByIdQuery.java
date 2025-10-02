package com.auth.microservice.application.query;

import com.auth.microservice.common.cqrs.Query;

import java.util.UUID;

/**
 * Query to get a user by their ID.
 * Supports optional loading of roles and permissions for optimization.
 */
public class GetUserByIdQuery extends Query {
    private final UUID targetUserId;
    private final boolean includeRoles;
    private final boolean includePermissions;

    public GetUserByIdQuery(String userId, UUID targetUserId, boolean includeRoles, boolean includePermissions) {
        super(userId);
        this.targetUserId = targetUserId;
        this.includeRoles = includeRoles;
        this.includePermissions = includePermissions;
    }

    public GetUserByIdQuery(String userId, UUID targetUserId) {
        this(userId, targetUserId, false, false);
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public boolean isIncludeRoles() {
        return includeRoles;
    }

    public boolean isIncludePermissions() {
        return includePermissions;
    }

    @Override
    public String toString() {
        return String.format("GetUserByIdQuery{targetUserId=%s, includeRoles=%s, includePermissions=%s, %s}", 
            targetUserId, includeRoles, includePermissions, super.toString());
    }
}