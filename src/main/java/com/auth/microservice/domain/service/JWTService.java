package com.auth.microservice.domain.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service interface for JWT token operations including generation, validation, and parsing.
 * Supports both access and refresh tokens with different expiration times.
 */
public interface JWTService {
    
    /**
     * Generates an access token for the given user with specified permissions.
     * 
     * @param userId the user ID to include in the token
     * @param email the user email
     * @param permissions the set of permissions for the user
     * @return the generated access token
     * @throws IllegalArgumentException if any parameter is null or invalid
     */
    String generateAccessToken(String userId, String email, Set<String> permissions);
    
    /**
     * Generates a refresh token for the given user.
     * 
     * @param userId the user ID to include in the token
     * @param email the user email
     * @return the generated refresh token
     * @throws IllegalArgumentException if any parameter is null or invalid
     */
    String generateRefreshToken(String userId, String email);
    
    /**
     * Validates a JWT token and returns the token claims if valid.
     * 
     * @param token the JWT token to validate
     * @return TokenValidationResult containing validation status and claims
     * @throws IllegalArgumentException if token is null or empty
     */
    TokenValidationResult validateToken(String token);
    
    /**
     * Extracts the user ID from a valid JWT token.
     * 
     * @param token the JWT token
     * @return Optional containing the user ID if token is valid, empty otherwise
     */
    Optional<String> extractUserId(String token);
    
    /**
     * Extracts the user email from a valid JWT token.
     * 
     * @param token the JWT token
     * @return Optional containing the user email if token is valid, empty otherwise
     */
    Optional<String> extractUserEmail(String token);
    
    /**
     * Extracts the permissions from a valid JWT token.
     * 
     * @param token the JWT token
     * @return Set of permissions if token is valid, empty set otherwise
     */
    Set<String> extractPermissions(String token);
    
    /**
     * Checks if a token is expired.
     * 
     * @param token the JWT token to check
     * @return true if token is expired, false otherwise
     */
    boolean isTokenExpired(String token);
    
    /**
     * Gets the expiration time of a token.
     * 
     * @param token the JWT token
     * @return Optional containing the expiration time if token is valid, empty otherwise
     */
    Optional<LocalDateTime> getTokenExpiration(String token);
    
    /**
     * Result of token validation.
     */
    record TokenValidationResult(
        boolean isValid,
        String message,
        Map<String, Object> claims
    ) {}
    
    /**
     * Pair of access and refresh tokens.
     */
    record TokenPair(
        String accessToken,
        String refreshToken,
        OffsetDateTime accessTokenExpiration,
        OffsetDateTime refreshTokenExpiration
    ) {}
    
    /**
     * Generates both access and refresh tokens for a user.
     * 
     * @param userId the user ID
     * @param email the user email
     * @param permissions the set of permissions for the user
     * @return TokenPair containing both tokens and their expiration times
     */
    TokenPair generateTokenPair(String userId, String email, Set<String> permissions);
}