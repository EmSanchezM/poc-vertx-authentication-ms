package com.auth.microservice.common.cqrs;

import com.auth.microservice.common.cqrs.exceptions.HandlerRegistrationException;
import com.auth.microservice.common.cqrs.exceptions.QueryNotFoundException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vert.x implementation of the QueryBus.
 * Routes queries to their appropriate handlers using Vert.x's async capabilities.
 */
public class VertxQueryBus implements QueryBus {
    
    private static final Logger logger = LoggerFactory.getLogger(VertxQueryBus.class);
    
    private final Vertx vertx;
    private final Map<Class<? extends Query>, QueryHandler<?, ?>> handlers;
    
    public VertxQueryBus(Vertx vertx) {
        this.vertx = vertx;
        this.handlers = new ConcurrentHashMap<>();
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <R> Future<R> send(Query query) {
        logger.debug("Executing query: {}", query);
        
        QueryHandler<Query, R> handler = (QueryHandler<Query, R>) handlers.get(query.getClass());
        
        if (handler == null) {
            logger.error("No handler found for query type: {}", query.getClass().getSimpleName());
            return Future.failedFuture(new QueryNotFoundException(query.getClass()));
        }
        
        return vertx.executeBlocking(promise -> {
            try {
                handler.handle(query)
                    .onSuccess(result -> {
                        logger.debug("Query executed successfully: {}", query.getQueryId());
                        promise.complete(result);
                    })
                    .onFailure(error -> {
                        logger.error("Query execution failed: {}", query.getQueryId(), error);
                        promise.fail(error);
                    });
            } catch (Exception e) {
                logger.error("Unexpected error executing query: {}", query.getQueryId(), e);
                promise.fail(e);
            }
        }, false);
    }
    
    @Override
    public <Q extends Query, R> void registerHandler(QueryHandler<Q, R> handler) {
        Class<Q> queryType = handler.getQueryType();
        
        if (handlers.containsKey(queryType)) {
            throw new HandlerRegistrationException(
                String.format("Handler for query type %s is already registered", queryType.getSimpleName())
            );
        }
        
        handlers.put(queryType, handler);
        logger.info("Registered query handler for type: {}", queryType.getSimpleName());
    }
    
    /**
     * Returns the number of registered handlers.
     * Useful for testing and monitoring.
     */
    public int getHandlerCount() {
        return handlers.size();
    }
    
    /**
     * Checks if a handler is registered for the given query type.
     */
    public boolean hasHandler(Class<? extends Query> queryType) {
        return handlers.containsKey(queryType);
    }
}