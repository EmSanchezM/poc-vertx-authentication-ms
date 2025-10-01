package com.auth.microservice.application.query;

import com.auth.microservice.common.cqrs.Query;
import com.auth.microservice.domain.model.Email;

/**
 * Query to find a user by their email address.
 * Used for authentication and user lookup operations.
 */
public class FindUserByEmailQuery extends Query {
    private final Email email;
    private final boolean includeRoles;
    private final boolean includePermissions;

    public FindUserByEmailQuery(String userId, Email email, boolean includeRoles, boolean includePermissions) {
        super(userId);
        this.email = email;
        this.includeRoles = includeRoles;
        this.includePermissions = includePermissions;
    }

    public FindUserByEmailQuery(String userId, Email email) {
        this(userId, email, false, false);
    }

    public Email getEmail() {
        return email;
    }

    public boolean isIncludeRoles() {
        return includeRoles;
    }

    public boolean isIncludePermissions() {
        return includePermissions;
    }

    @Override
    public String toString() {
        return String.format("FindUserByEmailQuery{email=%s, includeRoles=%s, includePermissions=%s, %s}", 
            email, includeRoles, includePermissions, super.toString());
    }
}