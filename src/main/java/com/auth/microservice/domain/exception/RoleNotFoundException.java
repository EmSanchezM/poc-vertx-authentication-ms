package com.auth.microservice.domain.exception;

/**
 * Exception thrown when a role is not found in the system.
 * Typically used when trying to access or assign a role that doesn't exist.
 */
public class RoleNotFoundException extends DomainException {
    
    public RoleNotFoundException(String message) {
        super(message);
    }
    
    public RoleNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}