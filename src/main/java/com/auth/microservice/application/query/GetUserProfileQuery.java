package com.auth.microservice.application.query;

import com.auth.microservice.common.cqrs.Query;

import java.util.UUID;

/**
 * Query to get user profile information.
 * Returns a projection with essential user data excluding sensitive information.
 */
public class GetUserProfileQuery extends Query {
    private final UUID targetUserId;
    private final boolean includePermissions;

    public GetUserProfileQuery(String userId, UUID targetUserId, boolean includePermissions) {
        super(userId);
        this.targetUserId = targetUserId;
        this.includePermissions = includePermissions;
    }

    public GetUserProfileQuery(String userId, UUID targetUserId) {
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
        return String.format("GetUserProfileQuery{targetUserId=%s, includePermissions=%s, %s}", 
            targetUserId, includePermissions, super.toString());
    }
}