package com.auth.microservice.application.cqrs;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for all commands in the CQRS pattern.
 * Commands represent write operations that change the system state.
 */
public abstract class Command {
    private final UUID commandId;
    private final LocalDateTime timestamp;
    private final String userId;

    protected Command(String userId) {
        this.commandId = UUID.randomUUID();
        this.timestamp = LocalDateTime.now();
        this.userId = userId;
    }

    public UUID getCommandId() {
        return commandId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public String toString() {
        return String.format("%s{commandId=%s, timestamp=%s, userId='%s'}", 
            getClass().getSimpleName(), commandId, timestamp, userId);
    }
}