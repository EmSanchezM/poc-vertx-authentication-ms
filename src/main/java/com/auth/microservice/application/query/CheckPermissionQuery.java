package com.auth.microservice.application.query;

import com.auth.microservice.common.cqrs.Query;

import java.util.UUID;

/**
 * Query to check if a user has a specific permission.
 * Used for authorization decisions in the application.
 */
public class CheckPermissionQuery extends Query {
    private final UUID targetUserId;
    private final String permissionName;
    private final String resource;
    private final String action;
    private final boolean useCache;

    // Constructor for checking by permission name
    public CheckPermissionQuery(String userId, UUID targetUserId, String permissionName, boolean useCache) {
        super(userId);
        this.targetUserId = targetUserId;
        this.permissionName = permissionName;
        this.resource = null;
        this.action = null;
        this.useCache = useCache;
    }

    // Constructor for checking by resource and action
    public CheckPermissionQuery(String userId, UUID targetUserId, String resource, String action, boolean useCache) {
        super(userId);
        this.targetUserId = targetUserId;
        this.permissionName = null;
        this.resource = resource;
        this.action = action;
        this.useCache = useCache;
    }

    // Convenience constructors with default cache enabled
    public CheckPermissionQuery(String userId, UUID targetUserId, String permissionName) {
        this(userId, targetUserId, permissionName, true);
    }

    public CheckPermissionQuery(String userId, UUID targetUserId, String resource, String action) {
        this(userId, targetUserId, resource, action, true);
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public String getPermissionName() {
        return permissionName;
    }

    public String getResource() {
        return resource;
    }

    public String getAction() {
        return action;
    }

    public boolean isUseCache() {
        return useCache;
    }

    public boolean isCheckByName() {
        return permissionName != null;
    }

    public boolean isCheckByResourceAction() {
        return resource != null && action != null;
    }

    @Override
    public String toString() {
        if (isCheckByName()) {
            return String.format("CheckPermissionQuery{targetUserId=%s, permissionName='%s', useCache=%s, %s}", 
                targetUserId, permissionName, useCache, super.toString());
        } else {
            return String.format("CheckPermissionQuery{targetUserId=%s, resource='%s', action='%s', useCache=%s, %s}", 
                targetUserId, resource, action, useCache, super.toString());
        }
    }
}