package com.auth.microservice.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Utility class for token hashing operations.
 * Provides secure hashing of JWT tokens for storage and comparison.
 */
public final class TokenHashUtil {
    
    private static final String HASH_ALGORITHM = "SHA-256";
    
    private TokenHashUtil() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Hashes a token using SHA-256 algorithm.
     * 
     * @param token the token to hash
     * @return the hashed token as a hexadecimal string
     * @throws IllegalArgumentException if token is null or empty
     * @throws RuntimeException if hashing algorithm is not available
     */
    public static String hashToken(String token) {
        Objects.requireNonNull(token, "Token cannot be null");
        if (token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token cannot be empty");
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Verifies if a token matches the given hash.
     * 
     * @param token the plain token to verify
     * @param hash the hash to compare against
     * @return true if token matches the hash, false otherwise
     * @throws IllegalArgumentException if any parameter is null or empty
     */
    public static boolean verifyToken(String token, String hash) {
        Objects.requireNonNull(token, "Token cannot be null");
        Objects.requireNonNull(hash, "Hash cannot be null");
        
        if (token.trim().isEmpty() || hash.trim().isEmpty()) {
            return false;
        }
        
        String tokenHash = hashToken(token);
        return tokenHash.equals(hash);
    }
    
    /**
     * Converts byte array to hexadecimal string.
     * 
     * @param bytes the byte array to convert
     * @return hexadecimal string representation
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}