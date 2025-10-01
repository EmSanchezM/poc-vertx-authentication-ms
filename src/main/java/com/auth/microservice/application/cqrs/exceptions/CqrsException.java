package com.auth.microservice.application.cqrs.exceptions;

/**
 * Base exception for all CQRS-related errors.
 */
public abstract class CqrsException extends RuntimeException {
    
    protected CqrsException(String message) {
        super(message);
    }
    
    protected CqrsException(String message, Throwable cause) {
        super(message, cause);
    }
}