package com.auth.microservice.common.cqrs;

import com.auth.microservice.common.cqrs.exceptions.CommandNotFoundException;
import com.auth.microservice.common.cqrs.exceptions.HandlerRegistrationException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vert.x implementation of the CommandBus.
 * Routes commands to their appropriate handlers using Vert.x's async capabilities.
 */
public class VertxCommandBus implements CommandBus {
    
    private static final Logger logger = LoggerFactory.getLogger(VertxCommandBus.class);
    
    private final Vertx vertx;
    private final Map<Class<? extends Command>, CommandHandler<?, ?>> handlers;
    
    public VertxCommandBus(Vertx vertx) {
        this.vertx = vertx;
        this.handlers = new ConcurrentHashMap<>();
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <R> Future<R> send(Command command) {
        logger.debug("Executing command: {}", command);
        
        CommandHandler<Command, R> handler = (CommandHandler<Command, R>) handlers.get(command.getClass());
        
        if (handler == null) {
            logger.error("No handler found for command type: {}", command.getClass().getSimpleName());
            return Future.failedFuture(new CommandNotFoundException(command.getClass()));
        }
        
        return vertx.executeBlocking(promise -> {
            try {
                handler.handle(command)
                    .onSuccess(result -> {
                        logger.debug("Command executed successfully: {}", command.getCommandId());
                        promise.complete(result);
                    })
                    .onFailure(error -> {
                        logger.error("Command execution failed: {}", command.getCommandId(), error);
                        promise.fail(error);
                    });
            } catch (Exception e) {
                logger.error("Unexpected error executing command: {}", command.getCommandId(), e);
                promise.fail(e);
            }
        }, false);
    }
    
    @Override
    public <C extends Command, R> void registerHandler(CommandHandler<C, R> handler) {
        Class<C> commandType = handler.getCommandType();
        
        if (handlers.containsKey(commandType)) {
            throw new HandlerRegistrationException(
                String.format("Handler for command type %s is already registered", commandType.getSimpleName())
            );
        }
        
        handlers.put(commandType, handler);
        logger.info("Registered command handler for type: {}", commandType.getSimpleName());
    }
    
    /**
     * Returns the number of registered handlers.
     * Useful for testing and monitoring.
     */
    public int getHandlerCount() {
        return handlers.size();
    }
    
    /**
     * Checks if a handler is registered for the given command type.
     */
    public boolean hasHandler(Class<? extends Command> commandType) {
        return handlers.containsKey(commandType);
    }
}