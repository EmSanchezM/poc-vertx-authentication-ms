package com.auth.microservice.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing a password with validation rules
 */
public final class Password {
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]{8,}$"
    );
    
    private final String value;
    
    public Password(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        
        if (value.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
        
        if (value.length() > 128) {
            throw new IllegalArgumentException("Password cannot exceed 128 characters");
        }
        
        if (!PASSWORD_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Password must contain at least one lowercase letter, one uppercase letter, " +
                "one digit, and one special character (@$!%*?&#)"
            );
        }
        
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Password password = (Password) obj;
        return Objects.equals(value, password.value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
    
    @Override
    public String toString() {
        return "Password{***}"; // Never expose the actual password
    }
}