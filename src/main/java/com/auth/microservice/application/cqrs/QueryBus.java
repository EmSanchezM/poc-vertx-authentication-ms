package com.auth.microservice.application.cqrs;

import io.vertx.core.Future;

/**
 * Interface for the query bus in the CQRS pattern.
 * The query bus routes queries to their appropriate handlers.
 */
public interface QueryBus {
    
    /**
     * Executes a query by routing it to the appropriate handler.
     * 
     * @param query The query to execute
     * @param <T> The type of result expected from the query
     * @return A Future containing the result of the query execution
     */
    <T> Future<T> execute(Query<T> query);
    
    /**
     * Registers a query handler with the bus.
     * 
     * @param handler The query handler to register
     * @param <Q> The type of query the handler processes
     * @param <R> The type of result the handler returns
     */
    <Q extends Query<R>, R> void registerHandler(QueryHandler<Q, R> handler);
}