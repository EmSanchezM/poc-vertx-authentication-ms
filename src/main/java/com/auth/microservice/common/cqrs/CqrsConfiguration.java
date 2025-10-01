package com.auth.microservice.common.cqrs;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Configuration class for CQRS infrastructure.
 * Provides factory methods and automatic handler registration.
 */
public class CqrsConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(CqrsConfiguration.class);
    
    private final CommandBus commandBus;
    private final QueryBus queryBus;
    
    public CqrsConfiguration(Vertx vertx) {
        this.commandBus = new VertxCommandBus(vertx);
        this.queryBus = new VertxQueryBus(vertx);
        logger.info("CQRS infrastructure initialized");
    }
    
    /**
     * Returns the configured command bus.
     */
    public CommandBus getCommandBus() {
        return commandBus;
    }
    
    /**
     * Returns the configured query bus.
     */
    public QueryBus getQueryBus() {
        return queryBus;
    }
    
    /**
     * Registers multiple command handlers at once.
     * 
     * @param handlers List of command handlers to register
     */
    public void registerCommandHandlers(List<CommandHandler<?, ?>> handlers) {
        handlers.forEach(handler -> {
            try {
                commandBus.registerHandler(handler);
                logger.debug("Registered command handler: {}", handler.getClass().getSimpleName());
            } catch (Exception e) {
                logger.error("Failed to register command handler: {}", handler.getClass().getSimpleName(), e);
                throw e;
            }
        });
        logger.info("Registered {} command handlers", handlers.size());
    }
    
    /**
     * Registers multiple query handlers at once.
     * 
     * @param handlers List of query handlers to register
     */
    public void registerQueryHandlers(List<QueryHandler<?, ?>> handlers) {
        handlers.forEach(handler -> {
            try {
                queryBus.registerHandler(handler);
                logger.debug("Registered query handler: {}", handler.getClass().getSimpleName());
            } catch (Exception e) {
                logger.error("Failed to register query handler: {}", handler.getClass().getSimpleName(), e);
                throw e;
            }
        });
        logger.info("Registered {} query handlers", handlers.size());
    }
    
    /**
     * Registers all handlers (both command and query) at once.
     * 
     * @param commandHandlers List of command handlers
     * @param queryHandlers List of query handlers
     */
    public void registerAllHandlers(List<CommandHandler<?, ?>> commandHandlers, 
                                   List<QueryHandler<?, ?>> queryHandlers) {
        registerCommandHandlers(commandHandlers);
        registerQueryHandlers(queryHandlers);
        logger.info("CQRS handler registration completed");
    }
}