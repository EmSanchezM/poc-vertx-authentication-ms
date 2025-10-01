package com.auth.microservice.common.cqrs;

import io.vertx.core.Future;

/**
 * Interface for query handlers in the CQRS pattern.
 * Query handlers process queries and execute read operations.
 * 
 * @param <Q> The type of query this handler processes
 * @param <R> The type of result this handler returns
 */
public interface QueryHandler<Q extends Query, R> {
    
    /**
     * Handles the given query and returns a Future with the result.
     * 
     * @param query The query to handle
     * @return A Future containing the result of the query execution
     */
    Future<R> handle(Q query);
    
    /**
     * Returns the class of query this handler can process.
     * Used for automatic registration in the query bus.
     * 
     * @return The query class this handler supports
     */
    Class<Q> getQueryType();
}