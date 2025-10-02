package com.auth.microservice.domain.exception;

/**
 * Exception thrown when a requested session cannot be found.
 * This can occur when trying to invalidate a non-existent session or
 * when looking up a session by token that doesn't exist.
 */
public class SessionNotFoundException extends DomainException {
    
    public SessionNotFoundException(String message) {
        super(message);
    }
    
    public SessionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}