package com.auth.microservice.application.query;

import com.auth.microservice.common.cqrs.Query;

import java.util.UUID;

/**
 * Query to get all permissions for a specific user.
 * Returns permissions from all roles assigned to the user.
 */
public class GetUserPermissionsQuery extends Query {
    private final UUID targetUserId;
    private final boolean includeInherited;
    private final boolean useCache;

    public GetUserPermissionsQuery(String userId, UUID targetUserId, boolean includeInherited, boolean useCache) {
        super(userId);
        this.targetUserId = targetUserId;
        this.includeInherited = includeInherited;
        this.useCache = useCache;
    }

    public GetUserPermissionsQuery(String userId, UUID targetUserId) {
        this(userId, targetUserId, true, true);
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public boolean isIncludeInherited() {
        return includeInherited;
    }

    public boolean isUseCache() {
        return useCache;
    }

    @Override
    public String toString() {
        return String.format("GetUserPermissionsQuery{targetUserId=%s, includeInherited=%s, useCache=%s, %s}", 
            targetUserId, includeInherited, useCache, super.toString());
    }
}