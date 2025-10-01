package com.auth.microservice.application.cqrs;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for all queries in the CQRS pattern.
 * Queries represent read operations that don't change the system state.
 * 
 * @param <T> The type of result this query returns
 */
public abstract class Query<T> {
    private final UUID queryId;
    private final LocalDateTime timestamp;

    protected Query() {
        this.queryId = UUID.randomUUID();
        this.timestamp = LocalDateTime.now();
    }

    public UUID getQueryId() {
        return queryId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("%s{queryId=%s, timestamp=%s}", 
            getClass().getSimpleName(), queryId, timestamp);
    }
}