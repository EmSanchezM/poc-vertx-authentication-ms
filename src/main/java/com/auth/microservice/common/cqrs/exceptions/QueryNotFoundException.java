package com.auth.microservice.common.cqrs.exceptions;

/**
 * Exception thrown when no handler is found for a given query.
 */
public class QueryNotFoundException extends CqrsException {
    
    public QueryNotFoundException(Class<?> queryType) {
        super(String.format("No handler found for query type: %s", queryType.getSimpleName()));
    }
    
    public QueryNotFoundException(String queryTypeName) {
        super(String.format("No handler found for query type: %s", queryTypeName));
    }
}