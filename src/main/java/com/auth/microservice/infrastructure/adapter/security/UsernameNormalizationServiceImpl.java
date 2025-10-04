package com.auth.microservice.infrastructure.adapter.security;

import com.auth.microservice.domain.service.UsernameNormalizationService;
import com.auth.microservice.domain.exception.UsernameGenerationException;
import com.auth.microservice.domain.exception.InvalidNameException;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Implementation of UsernameNormalizationService that handles character normalization,
 * special character removal, and username formatting for automatic username generation.
 * 
 * This service normalizes names by:
 * - Removing accents and diacritical marks
 * - Converting to lowercase
 * - Removing special characters (keeping only letters, numbers, dots, and hyphens)
 * - Handling multiple consecutive dots and hyphens
 * - Extracting first word only from multi-word names
 */
public class UsernameNormalizationServiceImpl implements UsernameNormalizationService {
    
    // Pattern to match combining diacritical marks (accents)
    private static final Pattern ACCENTS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    
    // Pattern to match special characters (keep only letters, numbers, dots, and hyphens)
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[^a-zA-Z0-9.-]");
    
    // Pattern to match multiple consecutive dots
    private static final Pattern MULTIPLE_DOTS_PATTERN = Pattern.compile("\\.{2,}");
    
    // Pattern to match multiple consecutive hyphens
    private static final Pattern MULTIPLE_HYPHENS_PATTERN = Pattern.compile("-{2,}");
    
    // Pattern to match leading/trailing dots and hyphens
    private static final Pattern LEADING_TRAILING_PATTERN = Pattern.compile("^[.-]+|[.-]+$");
    
    // Pattern to match whitespace for splitting words
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    
    @Override
    public String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidNameException("Name cannot be null or empty");
        }
        
        String normalized = name.trim().toLowerCase();
        
        // Remove accents using Unicode normalization
        // NFD (Canonical Decomposition) separates base characters from combining marks
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        normalized = ACCENTS_PATTERN.matcher(normalized).replaceAll("");
        
        // Remove special characters except dots and hyphens
        normalized = SPECIAL_CHARS_PATTERN.matcher(normalized).replaceAll("");
        
        // Replace multiple consecutive dots with single dot
        normalized = MULTIPLE_DOTS_PATTERN.matcher(normalized).replaceAll(".");
        
        // Replace multiple consecutive hyphens with single hyphen
        normalized = MULTIPLE_HYPHENS_PATTERN.matcher(normalized).replaceAll("-");
        
        // Remove leading and trailing dots and hyphens
        normalized = LEADING_TRAILING_PATTERN.matcher(normalized).replaceAll("");
        
        // Extract first word only if multiple words exist
        String[] words = WHITESPACE_PATTERN.split(normalized);
        if (words.length > 0 && !words[0].isEmpty()) {
            normalized = words[0];
        }
        
        // Final validation - ensure we have something left after normalization
        if (normalized.isEmpty()) {
            throw new InvalidNameException("Name normalization resulted in empty string: " + name);
        }
        
        return normalized;
    }
    
    @Override
    public String createBaseUsername(String normalizedFirstName, String normalizedLastName) {
        if (normalizedFirstName == null || normalizedFirstName.isEmpty()) {
            throw new InvalidNameException("Normalized first name cannot be null or empty");
        }
        
        if (normalizedLastName == null || normalizedLastName.isEmpty()) {
            throw new InvalidNameException("Normalized last name cannot be null or empty");
        }
        
        return normalizedFirstName + "." + normalizedLastName;
    }
    
    @Override
    public String truncateUsername(String username, int maxLength) {
        if (username == null || username.isEmpty()) {
            throw new InvalidNameException("Username cannot be null or empty for truncation");
        }
        
        if (maxLength <= 0) {
            throw new InvalidNameException("Maximum length must be positive");
        }
        
        // If username is already within limits, return as-is
        if (username.length() <= maxLength) {
            return username;
        }
        
        // Try to truncate proportionally if it contains a dot separator
        String[] parts = username.split("\\.");
        if (parts.length == 2) {
            return truncateProportionally(parts[0], parts[1], maxLength);
        }
        
        // Simple truncation for usernames without dot separator
        return username.substring(0, maxLength);
    }
    
    /**
     * Truncates a username with two parts (first.last) proportionally
     * to maintain readability while staying within length constraints.
     * 
     * @param firstPart the first part of the username
     * @param secondPart the second part of the username
     * @param maxLength the maximum allowed length
     * @return truncated username in format "first.last"
     */
    private String truncateProportionally(String firstPart, String secondPart, int maxLength) {
        // Reserve 1 character for the dot separator
        int availableLength = maxLength - 1;
        
        // If available length is too small, just truncate the first part
        if (availableLength < 2) {
            return firstPart.substring(0, Math.min(firstPart.length(), maxLength));
        }
        
        // Calculate proportional lengths
        int totalLength = firstPart.length() + secondPart.length();
        int firstPartLength = Math.max(1, (firstPart.length() * availableLength) / totalLength);
        int secondPartLength = availableLength - firstPartLength;
        
        // Ensure both parts have at least 1 character
        if (firstPartLength == 0) {
            firstPartLength = 1;
            secondPartLength = availableLength - 1;
        } else if (secondPartLength == 0) {
            secondPartLength = 1;
            firstPartLength = availableLength - 1;
        }
        
        // Truncate each part to calculated length
        String truncatedFirst = firstPart.length() > firstPartLength 
            ? firstPart.substring(0, firstPartLength) 
            : firstPart;
            
        String truncatedSecond = secondPart.length() > secondPartLength 
            ? secondPart.substring(0, secondPartLength) 
            : secondPart;
        
        return truncatedFirst + "." + truncatedSecond;
    }
}