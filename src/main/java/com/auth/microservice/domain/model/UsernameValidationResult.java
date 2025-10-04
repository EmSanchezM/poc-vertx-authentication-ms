package com.auth.microservice.domain.model;

import java.util.Objects;
import java.util.Set;

/**
 * Value object representing the result of username validation.
 */
public final class UsernameValidationResult {
    private final boolean valid;
    private final String message;
    private final Set<String> violations;
    
    private UsernameValidationResult(boolean valid, String message, Set<String> violations) {
        this.valid = valid;
        this.message = Objects.requireNonNull(message, "Message cannot be null");
        this.violations = violations != null ? Set.copyOf(violations) : Set.of();
    }
    
    /**
     * Creates a valid username validation result.
     * 
     * @return Valid validation result
     */
    public static UsernameValidationResult valid() {
        return new UsernameValidationResult(true, "Username is valid", Set.of());
    }
    
    /**
     * Creates an invalid username validation result with specific violations.
     * 
     * @param message Error message describing the validation failure
     * @param violations Set of specific validation violations
     * @return Invalid validation result
     */
    public static UsernameValidationResult invalid(String message, Set<String> violations) {
        return new UsernameValidationResult(false, message, violations);
    }
    
    /**
     * Creates an invalid username validation result with a single violation.
     * 
     * @param message Error message describing the validation failure
     * @param violation Single validation violation
     * @return Invalid validation result
     */
    public static UsernameValidationResult invalid(String message, String violation) {
        return new UsernameValidationResult(false, message, Set.of(violation));
    }
    
    /**
     * Creates an invalid username validation result with just a message.
     * 
     * @param message Error message describing the validation failure
     * @return Invalid validation result
     */
    public static UsernameValidationResult invalid(String message) {
        return new UsernameValidationResult(false, message, Set.of());
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Set<String> getViolations() {
        return violations;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        UsernameValidationResult that = (UsernameValidationResult) obj;
        return valid == that.valid && 
               Objects.equals(message, that.message) && 
               Objects.equals(violations, that.violations);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(valid, message, violations);
    }
    
    @Override
    public String toString() {
        return "UsernameValidationResult{" +
                "valid=" + valid +
                ", message='" + message + '\'' +
                ", violations=" + violations +
                '}';
    }
}