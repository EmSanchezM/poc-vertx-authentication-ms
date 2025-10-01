package com.auth.microservice.application.cqrs;

import io.vertx.core.Future;

/**
 * Interface for the command bus in the CQRS pattern.
 * The command bus routes commands to their appropriate handlers.
 */
public interface CommandBus {
    
    /**
     * Executes a command by routing it to the appropriate handler.
     * 
     * @param command The command to execute
     * @param <T> The type of result expected from the command
     * @return A Future containing the result of the command execution
     */
    <T> Future<T> execute(Command command);
    
    /**
     * Registers a command handler with the bus.
     * 
     * @param handler The command handler to register
     * @param <C> The type of command the handler processes
     * @param <R> The type of result the handler returns
     */
    <C extends Command, R> void registerHandler(CommandHandler<C, R> handler);
}