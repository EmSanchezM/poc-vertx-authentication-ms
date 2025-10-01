package com.auth.microservice.common.cqrs;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for all queries in the CQRS pattern.
 * Queries represent read operations that don't change the system state.
 */
public abstract class Query {
    private final UUID queryId;
    private final LocalDateTime timestamp;
    private final String userId;

    protected Query(String userId) {
        this.queryId = UUID.randomUUID();
        this.timestamp = LocalDateTime.now();
        this.userId = userId;
    }

    public UUID getQueryId() {
        return queryId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public String toString() {
        return String.format("%s{queryId=%s, timestamp=%s, userId='%s'}", 
            getClass().getSimpleName(), queryId, timestamp, userId);
    }
}