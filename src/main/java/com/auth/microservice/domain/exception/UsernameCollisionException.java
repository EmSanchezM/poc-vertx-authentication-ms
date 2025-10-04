package com.auth.microservice.domain.exception;

/**
 * Exception thrown when username collision resolution fails after exhausting
 * all available options. This typically occurs when the maximum number of
 * collision resolution attempts has been reached and no unique username
 * could be generated.
 */
public class UsernameCollisionException extends UsernameGenerationException {
    
    private final String baseUsername;
    private final int attemptCount;
    
    /**
     * Creates a new UsernameCollisionException for an unresolvable collision.
     * 
     * @param baseUsername the base username that had collisions
     * @param attemptCount the number of attempts made to resolve the collision
     */
    public UsernameCollisionException(String baseUsername, int attemptCount) {
        super(String.format("Unable to resolve username collision for '%s' after %d attempts", 
              baseUsername, attemptCount));
        this.baseUsername = baseUsername;
        this.attemptCount = attemptCount;
    }
    
    /**
     * Creates a new UsernameCollisionException with a custom message.
     * 
     * @param baseUsername the base username that had collisions
     * @param attemptCount the number of attempts made to resolve the collision
     * @param message custom error message
     */
    public UsernameCollisionException(String baseUsername, int attemptCount, String message) {
        super(message);
        this.baseUsername = baseUsername;
        this.attemptCount = attemptCount;
    }
    
    /**
     * Gets the base username that could not be resolved.
     * 
     * @return the base username
     */
    public String getBaseUsername() {
        return baseUsername;
    }
    
    /**
     * Gets the number of collision resolution attempts made.
     * 
     * @return the attempt count
     */
    public int getAttemptCount() {
        return attemptCount;
    }
}