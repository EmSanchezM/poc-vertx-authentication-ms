package com.auth.microservice.infrastructure.adapter.security;

import com.auth.microservice.domain.service.PasswordService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.regex.Pattern;

/**
 * BCrypt implementation of PasswordService.
 * Uses Spring Security's BCryptPasswordEncoder for secure password hashing.
 * Uses configuration from application properties for BCrypt rounds.
 */
public class BCryptPasswordService implements PasswordService {
    
    private final PasswordEncoder passwordEncoder;
    private final int bcryptRounds;
    
    // Password validation patterns
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
    
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;
    
    /**
     * Constructor that uses configuration from application properties.
     * 
     * @param bcryptRounds the number of BCrypt rounds from configuration
     */
    public BCryptPasswordService(int bcryptRounds) {
        if (bcryptRounds < 10 || bcryptRounds > 15) {
            throw new IllegalArgumentException("BCrypt rounds must be between 10 and 15 for security and performance balance");
        }
        this.bcryptRounds = bcryptRounds;
        this.passwordEncoder = new BCryptPasswordEncoder(bcryptRounds);
    }
    
    // Constructor for testing with custom encoder
    BCryptPasswordService(PasswordEncoder passwordEncoder, int bcryptRounds) {
        this.passwordEncoder = passwordEncoder;
        this.bcryptRounds = bcryptRounds;
    }
    
    @Override
    public String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        
        return passwordEncoder.encode(plainPassword);
    }
    
    @Override
    public boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || plainPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Plain password cannot be null or empty");
        }
        
        if (hashedPassword == null || hashedPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Hashed password cannot be null or empty");
        }
        
        try {
            return passwordEncoder.matches(plainPassword, hashedPassword);
        } catch (Exception e) {
            // Log the error but don't expose internal details
            return false;
        }
    }
    
    @Override
    public PasswordValidationResult validatePasswordStrength(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        
        // Check minimum length
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return new PasswordValidationResult(false, 
                "Password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
        
        // Check maximum length
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return new PasswordValidationResult(false, 
                "Password must not exceed " + MAX_PASSWORD_LENGTH + " characters");
        }
        
        // Check for uppercase letter
        if (!UPPERCASE_PATTERN.matcher(password).matches()) {
            return new PasswordValidationResult(false, 
                "Password must contain at least one uppercase letter");
        }
        
        // Check for lowercase letter
        if (!LOWERCASE_PATTERN.matcher(password).matches()) {
            return new PasswordValidationResult(false, 
                "Password must contain at least one lowercase letter");
        }
        
        // Check for digit
        if (!DIGIT_PATTERN.matcher(password).matches()) {
            return new PasswordValidationResult(false, 
                "Password must contain at least one digit");
        }
        
        // Check for special character
        if (!SPECIAL_CHAR_PATTERN.matcher(password).matches()) {
            return new PasswordValidationResult(false, 
                "Password must contain at least one special character");
        }
        
        // Check for common weak passwords
        if (isCommonWeakPassword(password)) {
            return new PasswordValidationResult(false, 
                "Password is too common and easily guessable");
        }
        
        return new PasswordValidationResult(true, "Password meets all security requirements");
    }
    
    /**
     * Checks if the password is a commonly used weak password.
     * In a production system, this would check against a comprehensive list.
     */
    private boolean isCommonWeakPassword(String password) {
        String lowerPassword = password.toLowerCase();
        
        // Common weak passwords (this is a minimal list for demonstration)
        String[] commonPasswords = {
            "password", "123456", "password123", "admin", "qwerty",
            "letmein", "welcome", "monkey", "1234567890", "password1",
            "abc123", "123456789", "welcome123", "admin123", "root"
        };
        
        for (String common : commonPasswords) {
            if (lowerPassword.equals(common)) {
                return true;
            }
            // Also check for common passwords with simple modifications
            if (lowerPassword.startsWith(common) || lowerPassword.contains(common)) {
                return true;
            }
        }
        
        // Check for simple patterns - same character repeated (case insensitive)
        if (password.length() >= 8) {
            // Check for repeated characters (like "aaaaaaaa" or "Aaaaaaaa")
            String lowerCase = password.toLowerCase();
            if (lowerCase.matches("^(.)\\1{7,}$")) {
                return true;
            }
            // Check for patterns like "Aaaaaaaa1!" - mostly repeated with minimal variation
            if (lowerCase.matches("^a{6,}.*") || lowerCase.matches(".*a{6,}.*")) {
                return true;
            }
        }
        
        // Check for sequential numbers at the beginning
        if (password.matches("^(012345|123456|234567|345678|456789|567890|678901|789012|890123|901234).*")) {
            return true;
        }
        
        return false;
    }
}