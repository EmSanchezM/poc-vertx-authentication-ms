package com.auth.microservice.infrastructure.adapter.security;

import com.auth.microservice.domain.service.UsernameValidationService;
import com.auth.microservice.domain.model.UsernameValidationResult;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Implementation of UsernameValidationService that validates usernames against
 * business rules and security constraints.
 * 
 * This service validates usernames by checking:
 * - Length constraints (3-64 characters)
 * - Character set (lowercase letters, numbers, dots, hyphens only)
 * - Leading/trailing dots or hyphens
 * - Consecutive dots or hyphens
 * - Reserved words
 */
public class UsernameValidationServiceImpl implements UsernameValidationService {
    
    // Username length constraints
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 64;
    
    // Pattern to validate allowed characters (lowercase letters, numbers, dots, hyphens)
    private static final Pattern VALID_CHARS_PATTERN = Pattern.compile("^[a-z0-9.-]+$");
    
    // Pattern to check for leading/trailing dots or hyphens
    private static final Pattern LEADING_TRAILING_PATTERN = Pattern.compile("^[.-]|[.-]$");
    
    // Pattern to check for consecutive dots or hyphens
    private static final Pattern CONSECUTIVE_DOTS_PATTERN = Pattern.compile("\\.{2,}");
    private static final Pattern CONSECUTIVE_HYPHENS_PATTERN = Pattern.compile("-{2,}");
    
    // Set of reserved words that cannot be used as usernames
    private static final Set<String> RESERVED_WORDS = Set.of(
        "admin", "root", "system", "api", "www", "mail", "ftp",
        "test", "demo", "guest", "anonymous", "null", "undefined",
        "user", "users", "auth", "login", "register", "password",
        "support", "help", "info", "contact", "about", "home",
        "index", "default", "public", "private", "static", "assets"
    );
    
    @Override
    public UsernameValidationResult validate(String username) {
        if (username == null) {
            return UsernameValidationResult.invalid(
                "Username cannot be null", 
                "NULL_USERNAME"
            );
        }
        
        Set<String> violations = new HashSet<>();
        
        // Check length constraints
        if (username.length() < MIN_LENGTH) {
            violations.add("USERNAME_TOO_SHORT");
        }
        
        if (username.length() > MAX_LENGTH) {
            violations.add("USERNAME_TOO_LONG");
        }
        
        // Check character set
        if (!isValidFormat(username)) {
            violations.add("INVALID_CHARACTERS");
        }
        
        // Check for leading/trailing dots or hyphens
        if (LEADING_TRAILING_PATTERN.matcher(username).find()) {
            violations.add("INVALID_START_OR_END");
        }
        
        // Check for consecutive dots or hyphens
        if (CONSECUTIVE_DOTS_PATTERN.matcher(username).find()) {
            violations.add("CONSECUTIVE_DOTS");
        }
        
        if (CONSECUTIVE_HYPHENS_PATTERN.matcher(username).find()) {
            violations.add("CONSECUTIVE_HYPHENS");
        }
        
        // Check for reserved words
        if (isReservedWord(username)) {
            violations.add("RESERVED_WORD");
        }
        
        // Return result based on violations
        if (violations.isEmpty()) {
            return UsernameValidationResult.valid();
        } else {
            String message = buildValidationMessage(violations);
            return UsernameValidationResult.invalid(message, violations);
        }
    }
    
    @Override
    public boolean isReservedWord(String username) {
        if (username == null) {
            return false;
        }
        
        // Case-insensitive comparison
        return RESERVED_WORDS.contains(username.toLowerCase());
    }
    
    @Override
    public boolean isValidFormat(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        
        // Check if username contains only allowed characters
        return VALID_CHARS_PATTERN.matcher(username).matches();
    }
    
    /**
     * Builds a human-readable validation message based on the violations found.
     * 
     * @param violations Set of validation violations
     * @return Descriptive error message
     */
    private String buildValidationMessage(Set<String> violations) {
        if (violations.isEmpty()) {
            return "Username is valid";
        }
        
        StringBuilder message = new StringBuilder("Username validation failed: ");
        
        if (violations.contains("NULL_USERNAME")) {
            message.append("username cannot be null; ");
        }
        
        if (violations.contains("USERNAME_TOO_SHORT")) {
            message.append("must be at least ").append(MIN_LENGTH).append(" characters; ");
        }
        
        if (violations.contains("USERNAME_TOO_LONG")) {
            message.append("must be at most ").append(MAX_LENGTH).append(" characters; ");
        }
        
        if (violations.contains("INVALID_CHARACTERS")) {
            message.append("can only contain lowercase letters, numbers, dots, and hyphens; ");
        }
        
        if (violations.contains("INVALID_START_OR_END")) {
            message.append("cannot start or end with dots or hyphens; ");
        }
        
        if (violations.contains("CONSECUTIVE_DOTS")) {
            message.append("cannot contain consecutive dots; ");
        }
        
        if (violations.contains("CONSECUTIVE_HYPHENS")) {
            message.append("cannot contain consecutive hyphens; ");
        }
        
        if (violations.contains("RESERVED_WORD")) {
            message.append("is a reserved word and cannot be used; ");
        }
        
        // Remove trailing "; " and return
        String result = message.toString();
        if (result.endsWith("; ")) {
            result = result.substring(0, result.length() - 2);
        }
        
        return result;
    }
}