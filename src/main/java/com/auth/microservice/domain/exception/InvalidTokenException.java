package com.auth.microservice.domain.exception;

/**
 * Exception thrown when a JWT token is invalid, expired, or malformed.
 */
public class InvalidTokenException extends DomainException {
    
    public InvalidTokenException(String message) {
        super(message);
    }
    
    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}