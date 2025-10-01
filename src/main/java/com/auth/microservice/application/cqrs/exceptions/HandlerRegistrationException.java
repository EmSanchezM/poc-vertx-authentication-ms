package com.auth.microservice.application.cqrs.exceptions;

/**
 * Exception thrown when there's an error registering a handler.
 */
public class HandlerRegistrationException extends CqrsException {
    
    public HandlerRegistrationException(String message) {
        super(message);
    }
    
    public HandlerRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}