package com.auth.microservice.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for all domain events.
 * Domain events represent something that happened in the domain that is of interest to other parts of the system.
 */
public abstract class DomainEvent {
    private final UUID eventId;
    private final LocalDateTime occurredAt;
    private final String aggregateId;
    private final String eventType;

    protected DomainEvent(String aggregateId) {
        this.eventId = UUID.randomUUID();
        this.occurredAt = LocalDateTime.now();
        this.aggregateId = aggregateId;
        this.eventType = this.getClass().getSimpleName();
    }

    public UUID getEventId() {
        return eventId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    @Override
    public String toString() {
        return String.format("%s{eventId=%s, aggregateId='%s', occurredAt=%s}", 
            eventType, eventId, aggregateId, occurredAt);
    }
}