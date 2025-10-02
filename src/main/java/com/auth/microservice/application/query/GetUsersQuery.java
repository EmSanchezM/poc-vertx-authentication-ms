package com.auth.microservice.application.query;

import com.auth.microservice.common.cqrs.Query;
import com.auth.microservice.domain.port.Pagination;

/**
 * Query to get users with pagination and filtering options.
 * Supports filtering by active status and includes optimization flags.
 */
public class GetUsersQuery extends Query {
    private final Pagination pagination;
    private final boolean includeInactive;
    private final boolean includeRoles;
    private final String searchTerm; // Optional search by name or email

    public GetUsersQuery(String userId, Pagination pagination, boolean includeInactive, 
                        boolean includeRoles, String searchTerm) {
        super(userId);
        this.pagination = pagination;
        this.includeInactive = includeInactive;
        this.includeRoles = includeRoles;
        this.searchTerm = searchTerm;
    }

    public GetUsersQuery(String userId, Pagination pagination) {
        this(userId, pagination, false, false, null);
    }

    public GetUsersQuery(String userId, Pagination pagination, boolean includeInactive) {
        this(userId, pagination, includeInactive, false, null);
    }

    public Pagination getPagination() {
        return pagination;
    }

    public boolean isIncludeInactive() {
        return includeInactive;
    }

    public boolean isIncludeRoles() {
        return includeRoles;
    }

    public String getSearchTerm() {
        return searchTerm;
    }

    public boolean hasSearchTerm() {
        return searchTerm != null && !searchTerm.trim().isEmpty();
    }

    @Override
    public String toString() {
        return String.format("GetUsersQuery{pagination=%s, includeInactive=%s, includeRoles=%s, searchTerm='%s', %s}", 
            pagination, includeInactive, includeRoles, searchTerm, super.toString());
    }
}