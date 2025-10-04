package com.auth.microservice.domain.exception;

/**
 * Exception thrown when username generation exceeds system-defined limits.
 * This includes scenarios where the maximum number of generation attempts
 * has been reached, or when system constraints prevent further processing.
 */
public class UsernameGenerationLimitException extends UsernameGenerationException {
    
    private final int maxAttempts;
    private final String limitType;
    
    /**
     * Creates a new UsernameGenerationLimitException for exceeded attempts.
     * 
     * @param maxAttempts the maximum number of attempts allowed
     */
    public UsernameGenerationLimitException(int maxAttempts) {
        super(String.format("Exceeded maximum username generation attempts: %d", maxAttempts));
        this.maxAttempts = maxAttempts;
        this.limitType = "MAX_ATTEMPTS";
    }
    
    /**
     * Creates a new UsernameGenerationLimitException with custom limit type.
     * 
     * @param maxAttempts the maximum number of attempts allowed
     * @param limitType the type of limit that was exceeded
     * @param message custom error message
     */
    public UsernameGenerationLimitException(int maxAttempts, String limitType, String message) {
        super(message);
        this.maxAttempts = maxAttempts;
        this.limitType = limitType;
    }
    
    /**
     * Gets the maximum attempts that were allowed.
     * 
     * @return the maximum attempts
     */
    public int getMaxAttempts() {
        return maxAttempts;
    }
    
    /**
     * Gets the type of limit that was exceeded.
     * 
     * @return the limit type
     */
    public String getLimitType() {
        return limitType;
    }
}