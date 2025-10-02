package com.auth.microservice.application.query;

import com.auth.microservice.common.cqrs.Query;
import com.auth.microservice.domain.port.Pagination;

/**
 * Query to get all roles with pagination support.
 * Supports optional loading of permissions for optimization.
 */
public class GetRolesQuery extends Query {
    private final Pagination pagination;
    private final boolean includePermissions;

    public GetRolesQuery(String userId, Pagination pagination, boolean includePermissions) {
        super(userId);
        this.pagination = pagination;
        this.includePermissions = includePermissions;
    }

    public GetRolesQuery(String userId, Pagination pagination) {
        this(userId, pagination, false);
    }

    public Pagination getPagination() {
        return pagination;
    }

    public boolean isIncludePermissions() {
        return includePermissions;
    }

    @Override
    public String toString() {
        return String.format("GetRolesQuery{pagination=%s, includePermissions=%s, %s}", 
            pagination, includePermissions, super.toString());
    }
}