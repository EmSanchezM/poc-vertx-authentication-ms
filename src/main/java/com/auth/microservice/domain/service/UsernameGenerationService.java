package com.auth.microservice.domain.service;

import com.auth.microservice.domain.model.UsernameValidationResult;
import io.vertx.core.Future;

/**
 * Service for generating unique usernames based on user's first and last name.
 * Handles normalization, collision resolution, and validation.
 */
public interface UsernameGenerationService {
    
    /**
     * Generates a unique username based on first and last name.
     * 
     * @param firstName User's first name
     * @param lastName User's last name
     * @return Future containing the generated unique username
     * @throws UsernameGenerationException if unable to generate a unique username
     */
    Future<String> generateUsername(String firstName, String lastName);
    
    /**
     * Validates if a username meets the system requirements.
     * 
     * @param username Username to validate
     * @return Future containing validation result
     */
    Future<UsernameValidationResult> validateUsername(String username);
}