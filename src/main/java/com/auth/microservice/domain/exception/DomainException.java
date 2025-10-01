package com.auth.microservice.domain.exception;

/**
 * Base exception for all domain-related errors.
 * Represents business rule violations and domain logic errors.
 */
public abstract class DomainException extends RuntimeException {
    
    protected DomainException(String message) {
        super(message);
    }
    
    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}