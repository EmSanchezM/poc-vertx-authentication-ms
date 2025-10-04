package com.auth.microservice.domain.service;

/**
 * Service for normalizing names to create valid usernames.
 * Handles character normalization, special character removal, and formatting.
 */
public interface UsernameNormalizationService {
    
    /**
     * Normalizes a name by removing accents, converting to lowercase,
     * removing special characters, and handling spaces.
     * 
     * @param name Name to normalize
     * @return Normalized name suitable for username generation
     */
    String normalizeName(String name);
    
    /**
     * Creates a base username from normalized first and last names.
     * 
     * @param normalizedFirstName Normalized first name
     * @param normalizedLastName Normalized last name
     * @return Base username in format "firstname.lastname"
     */
    String createBaseUsername(String normalizedFirstName, String normalizedLastName);
    
    /**
     * Truncates a username to fit within maximum length constraints
     * while maintaining readability.
     * 
     * @param username Username to truncate
     * @param maxLength Maximum allowed length
     * @return Truncated username
     */
    String truncateUsername(String username, int maxLength);
}