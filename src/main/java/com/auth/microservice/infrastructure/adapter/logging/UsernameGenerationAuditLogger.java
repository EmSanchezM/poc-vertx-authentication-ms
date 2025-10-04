package com.auth.microservice.infrastructure.adapter.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Specialized audit logger for username generation operations.
 * Provides detailed logging for all aspects of the username generation process
 * including normalization, collision resolution, and performance metrics.
 * 
 * This logger supports compliance and auditing requirements by tracking:
 * - Original input names and their transformations
 * - Collision resolution attempts and outcomes
 * - Performance metrics and timing information
 * - Error conditions and failure reasons
 */
public class UsernameGenerationAuditLogger {
    
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    /**
     * Logs the start of a username generation process.
     * 
     * @param firstName original first name input
     * @param lastName original last name input
     * @param requestId unique identifier for this generation request
     */
    public void logGenerationStarted(String firstName, String lastName, String requestId) {
        try {
            MDC.put("event_type", "username_generation_started");
            MDC.put("request_id", requestId);
            MDC.put("original_first_name", firstName);
            MDC.put("original_last_name", lastName);
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            AUDIT_LOG.info("Username generation started for firstName: '{}', lastName: '{}'", 
                          firstName, lastName);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Logs the normalization step of username generation.
     * 
     * @param requestId unique identifier for this generation request
     * @param originalFirstName original first name
     * @param originalLastName original last name
     * @param normalizedFirstName normalized first name
     * @param normalizedLastName normalized last name
     * @param normalizationDurationMs time taken for normalization in milliseconds
     */
    public void logNormalization(String requestId, String originalFirstName, String originalLastName,
                                String normalizedFirstName, String normalizedLastName, 
                                long normalizationDurationMs) {
        try {
            MDC.put("event_type", "username_normalization");
            MDC.put("request_id", requestId);
            MDC.put("original_first_name", originalFirstName);
            MDC.put("original_last_name", originalLastName);
            MDC.put("normalized_first_name", normalizedFirstName);
            MDC.put("normalized_last_name", normalizedLastName);
            MDC.put("normalization_duration_ms", String.valueOf(normalizationDurationMs));
            MDC.put("first_name_changed", String.valueOf(!originalFirstName.equals(normalizedFirstName)));
            MDC.put("last_name_changed", String.valueOf(!originalLastName.equals(normalizedLastName)));
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            AUDIT_LOG.info("Name normalization completed - firstName: '{}' -> '{}', lastName: '{}' -> '{}' ({}ms)", 
                          originalFirstName, normalizedFirstName, originalLastName, normalizedLastName, 
                          normalizationDurationMs);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Logs the base username creation step.
     * 
     * @param requestId unique identifier for this generation request
     * @param normalizedFirstName normalized first name
     * @param normalizedLastName normalized last name
     * @param baseUsername generated base username
     * @param wasTruncated whether the username was truncated
     * @param originalLength original length before truncation
     * @param finalLength final length after truncation
     */
    public void logBaseUsernameCreation(String requestId, String normalizedFirstName, String normalizedLastName,
                                       String baseUsername, boolean wasTruncated, int originalLength, int finalLength) {
        try {
            MDC.put("event_type", "base_username_creation");
            MDC.put("request_id", requestId);
            MDC.put("normalized_first_name", normalizedFirstName);
            MDC.put("normalized_last_name", normalizedLastName);
            MDC.put("base_username", baseUsername);
            MDC.put("was_truncated", String.valueOf(wasTruncated));
            MDC.put("original_length", String.valueOf(originalLength));
            MDC.put("final_length", String.valueOf(finalLength));
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            if (wasTruncated) {
                AUDIT_LOG.info("Base username created and truncated: '{}' (length: {} -> {})", 
                              baseUsername, originalLength, finalLength);
            } else {
                AUDIT_LOG.info("Base username created: '{}' (length: {})", baseUsername, finalLength);
            }
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Logs collision resolution attempts.
     * 
     * @param requestId unique identifier for this generation request
     * @param baseUsername base username being checked
     * @param attemptNumber current attempt number
     * @param candidateUsername username candidate being tested
     * @param collisionFound whether a collision was found
     * @param resolutionStrategy strategy used for resolution (numeric, uuid, etc.)
     */
    public void logCollisionResolutionAttempt(String requestId, String baseUsername, int attemptNumber,
                                            String candidateUsername, boolean collisionFound, String resolutionStrategy) {
        try {
            MDC.put("event_type", "collision_resolution_attempt");
            MDC.put("request_id", requestId);
            MDC.put("base_username", baseUsername);
            MDC.put("attempt_number", String.valueOf(attemptNumber));
            MDC.put("candidate_username", candidateUsername);
            MDC.put("collision_found", String.valueOf(collisionFound));
            MDC.put("resolution_strategy", resolutionStrategy);
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            if (collisionFound) {
                AUDIT_LOG.info("Collision detected for '{}' (attempt {}), trying next candidate with {} strategy", 
                              candidateUsername, attemptNumber, resolutionStrategy);
            } else {
                AUDIT_LOG.info("No collision found for '{}' (attempt {}), username available", 
                              candidateUsername, attemptNumber);
            }
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Logs the final username assignment.
     * 
     * @param requestId unique identifier for this generation request
     * @param originalFirstName original first name input
     * @param originalLastName original last name input
     * @param finalUsername final assigned username
     * @param totalAttempts total number of collision resolution attempts
     * @param totalDurationMs total time taken for generation in milliseconds
     * @param usedFallback whether UUID fallback was used
     */
    public void logFinalUsernameAssigned(String requestId, String originalFirstName, String originalLastName,
                                       String finalUsername, int totalAttempts, long totalDurationMs, boolean usedFallback) {
        try {
            MDC.put("event_type", "username_generation_completed");
            MDC.put("request_id", requestId);
            MDC.put("original_first_name", originalFirstName);
            MDC.put("original_last_name", originalLastName);
            MDC.put("final_username", finalUsername);
            MDC.put("total_attempts", String.valueOf(totalAttempts));
            MDC.put("total_duration_ms", String.valueOf(totalDurationMs));
            MDC.put("used_fallback", String.valueOf(usedFallback));
            MDC.put("generation_success", "true");
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            if (usedFallback) {
                AUDIT_LOG.info("Username generation completed with UUID fallback: '{}' for '{} {}' ({} attempts, {}ms)", 
                              finalUsername, originalFirstName, originalLastName, totalAttempts, totalDurationMs);
            } else {
                AUDIT_LOG.info("Username generation completed successfully: '{}' for '{} {}' ({} attempts, {}ms)", 
                              finalUsername, originalFirstName, originalLastName, totalAttempts, totalDurationMs);
            }
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Logs username generation failures.
     * 
     * @param requestId unique identifier for this generation request
     * @param originalFirstName original first name input
     * @param originalLastName original last name input
     * @param failureReason reason for the failure
     * @param exceptionType type of exception that caused the failure
     * @param totalDurationMs total time taken before failure in milliseconds
     * @param additionalContext additional context information
     */
    public void logGenerationFailure(String requestId, String originalFirstName, String originalLastName,
                                   String failureReason, String exceptionType, long totalDurationMs, 
                                   Map<String, String> additionalContext) {
        try {
            MDC.put("event_type", "username_generation_failed");
            MDC.put("request_id", requestId);
            MDC.put("original_first_name", originalFirstName);
            MDC.put("original_last_name", originalLastName);
            MDC.put("failure_reason", failureReason);
            MDC.put("exception_type", exceptionType);
            MDC.put("total_duration_ms", String.valueOf(totalDurationMs));
            MDC.put("generation_success", "false");
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            // Add additional context if provided
            if (additionalContext != null) {
                additionalContext.forEach(MDC::put);
            }
            
            AUDIT_LOG.error("Username generation failed for '{} {}': {} ({}ms)", 
                           originalFirstName, originalLastName, failureReason, totalDurationMs);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Logs performance metrics for username generation operations.
     * 
     * @param requestId unique identifier for this generation request
     * @param operation specific operation being measured
     * @param durationMs duration of the operation in milliseconds
     * @param metadata additional performance metadata
     */
    public void logPerformanceMetric(String requestId, String operation, long durationMs, Map<String, String> metadata) {
        try {
            MDC.put("event_type", "username_generation_performance");
            MDC.put("request_id", requestId);
            MDC.put("operation", operation);
            MDC.put("duration_ms", String.valueOf(durationMs));
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            // Add metadata if provided
            if (metadata != null) {
                metadata.forEach(MDC::put);
            }
            
            AUDIT_LOG.debug("Performance metric - {}: {}ms", operation, durationMs);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Logs validation results for generated usernames.
     * 
     * @param requestId unique identifier for this generation request
     * @param username username being validated
     * @param isValid whether the username passed validation
     * @param validationMessage validation result message
     * @param violations specific validation violations if any
     */
    public void logValidationResult(String requestId, String username, boolean isValid, 
                                  String validationMessage, String violations) {
        try {
            MDC.put("event_type", "username_validation");
            MDC.put("request_id", requestId);
            MDC.put("username", username);
            MDC.put("is_valid", String.valueOf(isValid));
            MDC.put("validation_message", validationMessage);
            if (violations != null) {
                MDC.put("violations", violations);
            }
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            if (isValid) {
                AUDIT_LOG.info("Username validation passed for: '{}'", username);
            } else {
                AUDIT_LOG.warn("Username validation failed for: '{}' - {} (violations: {})", 
                              username, validationMessage, violations);
            }
        } finally {
            MDC.clear();
        }
    }
}