package com.auth.microservice.domain.exception;

/**
 * Exception thrown when provided names are invalid or cannot be processed
 * for username generation. This includes names that are null, empty, contain
 * only special characters, or result in empty strings after normalization.
 */
public class InvalidNameException extends UsernameGenerationException {
    
    /**
     * Creates a new InvalidNameException with the specified message.
     * 
     * @param message the detail message explaining why the name is invalid
     */
    public InvalidNameException(String message) {
        super("Invalid name provided: " + message);
    }
    
    /**
     * Creates a new InvalidNameException with the specified message and cause.
     * 
     * @param message the detail message explaining why the name is invalid
     * @param cause the underlying cause of the failure
     */
    public InvalidNameException(String message, Throwable cause) {
        super("Invalid name provided: " + message, cause);
    }
}