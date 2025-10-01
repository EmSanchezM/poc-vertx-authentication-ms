package com.auth.microservice.domain.service;

import java.util.regex.Pattern;

/**
 * Service interface for password operations including hashing, verification, and validation.
 * Implements secure password handling following security best practices.
 */
public interface PasswordService {
    
    /**
     * Hashes a plain text password using BCrypt with secure salt.
     * 
     * @param plainPassword the plain text password to hash
     * @return the hashed password
     * @throws IllegalArgumentException if password is null or empty
     */
    String hashPassword(String plainPassword);
    
    /**
     * Verifies a plain text password against a hashed password.
     * 
     * @param plainPassword the plain text password to verify
     * @param hashedPassword the hashed password to compare against
     * @return true if passwords match, false otherwise
     * @throws IllegalArgumentException if any parameter is null or empty
     */
    boolean verifyPassword(String plainPassword, String hashedPassword);
    
    /**
     * Validates password strength according to security requirements.
     * 
     * @param password the password to validate
     * @return PasswordValidationResult containing validation status and messages
     * @throws IllegalArgumentException if password is null
     */
    PasswordValidationResult validatePasswordStrength(String password);
    
    /**
     * Result of password strength validation.
     */
    record PasswordValidationResult(
        boolean isValid,
        String message
    ) {}
}