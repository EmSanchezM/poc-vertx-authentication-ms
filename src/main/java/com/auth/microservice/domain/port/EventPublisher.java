package com.auth.microservice.domain.port;

import com.auth.microservice.domain.event.DomainEvent;
import io.vertx.core.Future;

/**
 * Interface for publishing domain events.
 * Used for audit logging and event-driven architecture.
 */
public interface EventPublisher {
    
    /**
     * Publishes a domain event for processing.
     * 
     * @param event The domain event to publish
     * @return Future that completes when the event is published
     */
    Future<Void> publish(DomainEvent event);
    
    /**
     * Publishes multiple domain events in a batch.
     * 
     * @param events The domain events to publish
     * @return Future that completes when all events are published
     */
    Future<Void> publishAll(DomainEvent... events);
}