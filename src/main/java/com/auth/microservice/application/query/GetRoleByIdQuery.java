package com.auth.microservice.application.query;

import com.auth.microservice.common.cqrs.Query;

import java.util.UUID;

/**
 * Query to get a role by its ID.
 * Supports optional loading of permissions for optimization.
 */
public class GetRoleByIdQuery extends Query {
    private final UUID roleId;
    private final boolean includePermissions;

    public GetRoleByIdQuery(String userId, UUID roleId, boolean includePermissions) {
        super(userId);
        this.roleId = roleId;
        this.includePermissions = includePermissions;
    }

    public GetRoleByIdQuery(String userId, UUID roleId) {
        this(userId, roleId, false);
    }

    public UUID getRoleId() {
        return roleId;
    }

    public boolean isIncludePermissions() {
        return includePermissions;
    }

    @Override
    public String toString() {
        return String.format("GetRoleByIdQuery{roleId=%s, includePermissions=%s, %s}", 
            roleId, includePermissions, super.toString());
    }
}