package com.auth.microservice.domain.exception;

/**
 * Exception thrown when attempting to create a user that already exists.
 * This includes duplicate email or username violations.
 */
public class UserAlreadyExistsException extends DomainException {
    
    public UserAlreadyExistsException(String message) {
        super(message);
    }
    
    public UserAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}