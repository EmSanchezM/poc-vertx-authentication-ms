package com.auth.microservice.application.cqrs.exceptions;

/**
 * Exception thrown when no handler is found for a given command.
 */
public class CommandNotFoundException extends CqrsException {
    
    public CommandNotFoundException(Class<?> commandType) {
        super(String.format("No handler found for command type: %s", commandType.getSimpleName()));
    }
    
    public CommandNotFoundException(String commandTypeName) {
        super(String.format("No handler found for command type: %s", commandTypeName));
    }
}