package com.auth.microservice.domain.exception;

/**
 * Exception thrown when authentication fails.
 * This includes invalid credentials, inactive users, etc.
 */
public class AuthenticationException extends DomainException {
    
    public AuthenticationException(String message) {
        super(message);
    }
    
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}