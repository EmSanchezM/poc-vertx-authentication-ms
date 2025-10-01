package com.auth.microservice.common.cqrs;

import io.vertx.core.Future;

/**
 * Interface for the query bus in the CQRS pattern.
 * The query bus is responsible for routing queries to their appropriate handlers.
 */
public interface QueryBus {
    
    /**
     * Registers a query handler for a specific query type.
     * 
     * @param <Q> The query type
     * @param <R> The result type
     * @param handler The query handler to register
     */
    <Q extends Query, R> void registerHandler(QueryHandler<Q, R> handler);
    
    /**
     * Sends a query to its registered handler for processing.
     * 
     * @param <R> The expected result type
     * @param query The query to send
     * @return A Future containing the result of query processing
     */
    <R> Future<R> send(Query query);
}