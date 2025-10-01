package com.auth.microservice.common.cqrs;

import io.vertx.core.Future;

/**
 * Interface for the command bus in the CQRS pattern.
 * The command bus is responsible for routing commands to their appropriate handlers.
 */
public interface CommandBus {
    
    /**
     * Registers a command handler for a specific command type.
     * 
     * @param <C> The command type
     * @param <R> The result type
     * @param handler The command handler to register
     */
    <C extends Command, R> void registerHandler(CommandHandler<C, R> handler);
    
    /**
     * Sends a command to its registered handler for processing.
     * 
     * @param <R> The expected result type
     * @param command The command to send
     * @return A Future containing the result of command processing
     */
    <R> Future<R> send(Command command);
}