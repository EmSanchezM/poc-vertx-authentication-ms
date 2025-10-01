package com.auth.microservice.application.cqrs;

import io.vertx.core.Future;

/**
 * Interface for command handlers in the CQRS pattern.
 * Command handlers process commands and execute write operations.
 * 
 * @param <C> The type of command this handler processes
 * @param <R> The type of result this handler returns
 */
public interface CommandHandler<C extends Command, R> {
    
    /**
     * Handles the given command and returns a Future with the result.
     * 
     * @param command The command to handle
     * @return A Future containing the result of the command execution
     */
    Future<R> handle(C command);
    
    /**
     * Returns the class of command this handler can process.
     * Used for automatic registration in the command bus.
     * 
     * @return The command class this handler supports
     */
    Class<C> getCommandType();
}