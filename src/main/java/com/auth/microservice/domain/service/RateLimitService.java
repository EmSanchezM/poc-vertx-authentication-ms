package com.auth.microservice.domain.service;

import io.vertx.core.Future;
import java.time.LocalDateTime;

/**
 * Service interface for rate limiting operations.
 * Supports different types of rate limiting (by IP, by user) and temporary blocking.
 */
public interface RateLimitService {
    
    /**
     * Checks if a request is allowed based on rate limiting rules.
     * 
     * @param identifier the identifier to check (IP address or user ID)
     * @param endpoint the endpoint being accessed
     * @param limitType the type of rate limit to apply
     * @return Future containing the rate limit result
     */
    Future<RateLimitResult> checkRateLimit(String identifier, String endpoint, RateLimitType limitType);
    
    /**
     * Records a failed attempt for the given identifier and endpoint.
     * 
     * @param identifier the identifier (IP address or user ID)
     * @param endpoint the endpoint being accessed
     * @param limitType the type of rate limit to apply
     * @return Future that completes when the attempt is recorded
     */
    Future<Void> recordFailedAttempt(String identifier, String endpoint, RateLimitType limitType);
    
    /**
     * Records a successful attempt for the given identifier and endpoint.
     * This may reset or reduce the failure count.
     * 
     * @param identifier the identifier (IP address or user ID)
     * @param endpoint the endpoint being accessed
     * @param limitType the type of rate limit to apply
     * @return Future that completes when the attempt is recorded
     */
    Future<Void> recordSuccessfulAttempt(String identifier, String endpoint, RateLimitType limitType);
    
    /**
     * Temporarily blocks an identifier for a specific duration.
     * 
     * @param identifier the identifier to block
     * @param endpoint the endpoint to block access to
     * @param limitType the type of rate limit
     * @param blockDurationMinutes the duration to block in minutes
     * @return Future that completes when the block is applied
     */
    Future<Void> temporaryBlock(String identifier, String endpoint, RateLimitType limitType, int blockDurationMinutes);
    
    /**
     * Removes any existing blocks for the given identifier and endpoint.
     * 
     * @param identifier the identifier to unblock
     * @param endpoint the endpoint to unblock
     * @param limitType the type of rate limit
     * @return Future that completes when the block is removed
     */
    Future<Void> removeBlock(String identifier, String endpoint, RateLimitType limitType);
    
    /**
     * Gets the current rate limit status for an identifier and endpoint.
     * 
     * @param identifier the identifier to check
     * @param endpoint the endpoint to check
     * @param limitType the type of rate limit
     * @return Future containing the current status
     */
    Future<RateLimitStatus> getRateLimitStatus(String identifier, String endpoint, RateLimitType limitType);
    
    /**
     * Type of rate limiting to apply.
     */
    enum RateLimitType {
        BY_IP,      // Rate limit by IP address
        BY_USER,    // Rate limit by user ID
        BY_GLOBAL   // Global rate limit
    }
    
    /**
     * Result of a rate limit check.
     */
    record RateLimitResult(
        boolean allowed,
        int remainingAttempts,
        LocalDateTime resetTime,
        String message
    ) {}
    
    /**
     * Current status of rate limiting for an identifier.
     */
    record RateLimitStatus(
        String identifier,
        String endpoint,
        RateLimitType limitType,
        int currentAttempts,
        int maxAttempts,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        boolean isBlocked,
        LocalDateTime blockedUntil
    ) {}
}