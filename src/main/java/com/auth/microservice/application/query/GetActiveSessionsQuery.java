package com.auth.microservice.application.query;

import com.auth.microservice.common.cqrs.Query;

import java.util.Objects;
import java.util.UUID;

/**
 * Query to get all active sessions for a specific user.
 * Used for displaying active sessions to users and for security monitoring.
 */
public class GetActiveSessionsQuery extends Query {
    private final UUID targetUserId;
    private final boolean includeSensitiveData;
    private final int maxResults;

    public GetActiveSessionsQuery(String userId, UUID targetUserId, boolean includeSensitiveData, int maxResults) {
        super(userId);
        this.targetUserId = Objects.requireNonNull(targetUserId, "Target user ID cannot be null");
        this.includeSensitiveData = includeSensitiveData;
        this.maxResults = maxResults > 0 ? maxResults : 50; // Default limit of 50
    }

    public GetActiveSessionsQuery(String userId, UUID targetUserId) {
        this(userId, targetUserId, false, 50);
    }

    public GetActiveSessionsQuery(String userId, UUID targetUserId, boolean includeSensitiveData) {
        this(userId, targetUserId, includeSensitiveData, 50);
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public boolean isIncludeSensitiveData() {
        return includeSensitiveData;
    }

    public int getMaxResults() {
        return maxResults;
    }

    @Override
    public String toString() {
        return String.format("GetActiveSessionsQuery{targetUserId=%s, includeSensitiveData=%s, maxResults=%d, %s}", 
            targetUserId, includeSensitiveData, maxResults, super.toString());
    }
}