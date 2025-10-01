package com.auth.microservice.domain.exception;

/**
 * Exception thrown when a user is not found in the system.
 * Typically used when trying to access or modify a user that doesn't exist.
 */
public class UserNotFoundException extends DomainException {
    
    public UserNotFoundException(String message) {
        super(message);
    }
    
    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}