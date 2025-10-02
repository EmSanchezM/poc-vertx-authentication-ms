package com.auth.microservice.domain.exception;

/**
 * Exception thrown when attempting to create a role that already exists.
 * This includes duplicate role name violations.
 */
public class RoleAlreadyExistsException extends DomainException {
    
    public RoleAlreadyExistsException(String message) {
        super(message);
    }
    
    public RoleAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}