package com.auth.microservice.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value object representing a JWT token with expiration
 */
public final class Token {
    private final String value;
    private final LocalDateTime expiresAt;
    private final TokenType type;
    
    public enum TokenType {
        ACCESS, REFRESH
    }
    
    public Token(String value, LocalDateTime expiresAt, TokenType type) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Token value cannot be null or empty");
        }
        
        if (expiresAt == null) {
            throw new IllegalArgumentException("Token expiration cannot be null");
        }
        
        if (type == null) {
            throw new IllegalArgumentException("Token type cannot be null");
        }
        
        this.value = value.trim();
        this.expiresAt = expiresAt;
        this.type = type;
    }
    
    public String getValue() {
        return value;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public TokenType getType() {
        return type;
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    public boolean isValid() {
        return !isExpired() && value != null && !value.isEmpty();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Token token = (Token) obj;
        return Objects.equals(value, token.value) &&
               Objects.equals(expiresAt, token.expiresAt) &&
               type == token.type;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(value, expiresAt, type);
    }
    
    @Override
    public String toString() {
        return "Token{type=" + type + ", expiresAt=" + expiresAt + ", expired=" + isExpired() + "}";
    }
}