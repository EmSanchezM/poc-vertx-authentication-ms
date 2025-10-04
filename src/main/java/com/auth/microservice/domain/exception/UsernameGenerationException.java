package com.auth.microservice.domain.exception;

/**
 * Exception thrown when username generation fails.
 * This includes failures in normalization, collision resolution, or validation.
 */
public class UsernameGenerationException extends DomainException {
    
    /**
     * Creates a new UsernameGenerationException with the specified message.
     * 
     * @param message the detail message explaining the failure
     */
    public UsernameGenerationException(String message) {
        super(message);
    }
    
    /**
     * Creates a new UsernameGenerationException with the specified message and cause.
     * 
     * @param message the detail message explaining the failure
     * @param cause the underlying cause of the failure
     */
    public UsernameGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}