package com.auth.microservice.domain.service;

import com.auth.microservice.domain.model.UsernameValidationResult;

/**
 * Service for validating usernames against business rules and security constraints.
 */
public interface UsernameValidationService {
    
    /**
     * Validates a username against all business rules.
     * 
     * @param username Username to validate
     * @return Validation result with details
     */
    UsernameValidationResult validate(String username);
    
    /**
     * Checks if a username is a reserved word.
     * 
     * @param username Username to check
     * @return true if username is reserved
     */
    boolean isReservedWord(String username);
    
    /**
     * Validates username format and character constraints.
     * 
     * @param username Username to validate
     * @return true if format is valid
     */
    boolean isValidFormat(String username);
}