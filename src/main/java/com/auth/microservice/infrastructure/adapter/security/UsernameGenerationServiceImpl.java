package com.auth.microservice.infrastructure.adapter.security;

import com.auth.microservice.domain.service.UsernameGenerationService;
import com.auth.microservice.domain.service.UsernameNormalizationService;
import com.auth.microservice.domain.service.UsernameCollisionService;
import com.auth.microservice.domain.service.UsernameValidationService;
import com.auth.microservice.domain.model.UsernameValidationResult;
import com.auth.microservice.domain.exception.UsernameGenerationException;
import com.auth.microservice.domain.exception.InvalidNameException;
import com.auth.microservice.domain.exception.UsernameCollisionException;
import com.auth.microservice.domain.exception.UsernameGenerationLimitException;
import com.auth.microservice.infrastructure.adapter.logging.UsernameGenerationAuditLogger;
import com.auth.microservice.infrastructure.adapter.logging.UsernameGenerationErrorHandler;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Main implementation of UsernameGenerationService that orchestrates the complete
 * username generation process by coordinating normalization, collision resolution,
 * and validation services.
 * 
 * This service handles the complete username generation workflow:
 * 1. Validates input parameters (firstName and lastName)
 * 2. Normalizes names using UsernameNormalizationService
 * 3. Creates base username from normalized names
 * 4. Resolves collisions using UsernameCollisionService
 * 5. Validates final username using UsernameValidationService
 * 6. Provides comprehensive error handling and logging
 */
public class UsernameGenerationServiceImpl implements UsernameGenerationService {
    
    private static final Logger logger = LoggerFactory.getLogger(UsernameGenerationServiceImpl.class);
    
    // Maximum username length constraint
    private static final int MAX_USERNAME_LENGTH = 64;
    
    private final UsernameNormalizationService normalizationService;
    private final UsernameCollisionService collisionService;
    private final UsernameValidationService validationService;
    private final UsernameGenerationAuditLogger auditLogger;
    private final UsernameGenerationErrorHandler errorHandler;
    
    /**
     * Creates a new UsernameGenerationServiceImpl with the required dependencies.
     * 
     * @param normalizationService service for normalizing names
     * @param collisionService service for resolving username collisions
     * @param validationService service for validating usernames
     * @param auditLogger audit logger for username generation events
     * @param errorHandler error handler for comprehensive exception handling
     */
    public UsernameGenerationServiceImpl(
            UsernameNormalizationService normalizationService,
            UsernameCollisionService collisionService,
            UsernameValidationService validationService,
            UsernameGenerationAuditLogger auditLogger,
            UsernameGenerationErrorHandler errorHandler) {
        this.normalizationService = normalizationService;
        this.collisionService = collisionService;
        this.validationService = validationService;
        this.auditLogger = auditLogger;
        this.errorHandler = errorHandler;
    }
    
    @Override
    public Future<String> generateUsername(String firstName, String lastName) {
        // Generate unique request ID for audit tracking
        String requestId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        
        // Log generation start
        auditLogger.logGenerationStarted(firstName, lastName, requestId);
        logger.info("Starting username generation for firstName: '{}', lastName: '{}' (requestId: {})", 
                   firstName, lastName, requestId);
        
        try {
            // Validate input parameters
            validateInputParameters(firstName, lastName);
            
            // Step 1: Normalize names with timing
            long normalizationStart = System.currentTimeMillis();
            String normalizedFirstName = normalizationService.normalizeName(firstName);
            String normalizedLastName = normalizationService.normalizeName(lastName);
            long normalizationDuration = System.currentTimeMillis() - normalizationStart;
            
            // Log normalization results
            auditLogger.logNormalization(requestId, firstName, lastName, 
                                        normalizedFirstName, normalizedLastName, normalizationDuration);
            
            logger.info("Normalized names - firstName: '{}' -> '{}', lastName: '{}' -> '{}' ({}ms)", 
                       firstName, normalizedFirstName, lastName, normalizedLastName, normalizationDuration);
            
            // Step 2: Create base username
            String baseUsername = normalizationService.createBaseUsername(normalizedFirstName, normalizedLastName);
            int originalLength = baseUsername.length();
            
            // Step 3: Truncate if necessary to accommodate potential suffixes
            String truncatedBaseUsername = normalizationService.truncateUsername(baseUsername, MAX_USERNAME_LENGTH - 10);
            boolean wasTruncated = !baseUsername.equals(truncatedBaseUsername);
            
            // Log base username creation
            auditLogger.logBaseUsernameCreation(requestId, normalizedFirstName, normalizedLastName,
                                              truncatedBaseUsername, wasTruncated, originalLength, 
                                              truncatedBaseUsername.length());
            
            logger.info("Created base username: '{}' (truncated: '{}', wasTruncated: {})", 
                       baseUsername, truncatedBaseUsername, wasTruncated);
            
            // Step 4: Resolve collisions
            return ((UsernameCollisionServiceImpl) collisionService).resolveCollision(truncatedBaseUsername, requestId)
                .compose(uniqueUsername -> {
                    logger.info("Collision resolution completed. Final username: '{}'", uniqueUsername);
                    
                    // Step 5: Validate final username
                    UsernameValidationResult validationResult = validationService.validate(uniqueUsername);
                    
                    // Log validation result
                    auditLogger.logValidationResult(requestId, uniqueUsername, validationResult.isValid(),
                                                   validationResult.getMessage(), 
                                                   validationResult.getViolations().toString());
                    
                    if (!validationResult.isValid()) {
                        String errorMessage = String.format(
                            "Generated username '%s' failed validation: %s", 
                            uniqueUsername, validationResult.getMessage()
                        );
                        logger.error(errorMessage);
                        
                        // Log failure
                        long totalDuration = System.currentTimeMillis() - startTime;
                        Map<String, String> context = new HashMap<>();
                        context.put("validation_violations", validationResult.getViolations().toString());
                        auditLogger.logGenerationFailure(requestId, firstName, lastName, errorMessage,
                                                        "ValidationFailure", totalDuration, context);
                        
                        return Future.failedFuture(new UsernameGenerationException(errorMessage));
                    }
                    
                    // Log successful completion
                    long totalDuration = System.currentTimeMillis() - startTime;
                    boolean usedFallback = uniqueUsername.contains("-"); // Simple heuristic for UUID fallback
                    auditLogger.logFinalUsernameAssigned(requestId, firstName, lastName, uniqueUsername,
                                                        1, totalDuration, usedFallback); // TODO: Get actual attempt count
                    
                    logger.info("Username generation completed successfully: '{}' ({}ms)", uniqueUsername, totalDuration);
                    return Future.succeededFuture(uniqueUsername);
                })
                .recover(throwable -> {
                    long totalDuration = System.currentTimeMillis() - startTime;
                    
                    // Use error handler for comprehensive exception handling
                    String userFriendlyMessage = errorHandler.handleException(
                        (Exception) throwable, firstName, lastName, requestId);
                    
                    // Log failure with specific exception handling
                    String exceptionType = throwable.getClass().getSimpleName();
                    Map<String, String> context = new HashMap<>();
                    context.put("user_friendly_message", userFriendlyMessage);
                    context.put("recoverable", String.valueOf(errorHandler.isRecoverable((Exception) throwable)));
                    context.put("http_status", String.valueOf(errorHandler.getHttpStatusCode((Exception) throwable)));
                    
                    if (throwable instanceof UsernameCollisionException) {
                        UsernameCollisionException collisionEx = (UsernameCollisionException) throwable;
                        context.put("base_username", collisionEx.getBaseUsername());
                        context.put("attempt_count", String.valueOf(collisionEx.getAttemptCount()));
                    } else if (throwable instanceof UsernameGenerationLimitException) {
                        UsernameGenerationLimitException limitEx = (UsernameGenerationLimitException) throwable;
                        context.put("max_attempts", String.valueOf(limitEx.getMaxAttempts()));
                        context.put("limit_type", limitEx.getLimitType());
                    }
                    
                    auditLogger.logGenerationFailure(requestId, firstName, lastName, 
                                                    throwable.getMessage(), exceptionType, totalDuration, context);
                    
                    if (throwable instanceof UsernameGenerationException) {
                        return Future.failedFuture(throwable);
                    }
                    
                    return Future.failedFuture(new UsernameGenerationException(userFriendlyMessage, throwable));
                });
                
        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            
            // Use error handler for comprehensive exception handling
            String userFriendlyMessage = errorHandler.handleException(e, firstName, lastName, requestId);
            
            // Log initialization failure
            String exceptionType = e.getClass().getSimpleName();
            Map<String, String> context = new HashMap<>();
            context.put("user_friendly_message", userFriendlyMessage);
            context.put("recoverable", String.valueOf(errorHandler.isRecoverable(e)));
            context.put("http_status", String.valueOf(errorHandler.getHttpStatusCode(e)));
            context.put("phase", "initialization");
            
            auditLogger.logGenerationFailure(requestId, firstName, lastName, e.getMessage(),
                                            exceptionType, totalDuration, context);
            
            if (e instanceof InvalidNameException) {
                return Future.failedFuture(e);
            }
            
            return Future.failedFuture(new UsernameGenerationException(userFriendlyMessage, e));
        }
    }
    
    @Override
    public Future<UsernameValidationResult> validateUsername(String username) {
        logger.info("Starting username validation for: '{}'", username);
        
        try {
            // Perform validation using the validation service
            UsernameValidationResult result = validationService.validate(username);
            
            if (result.isValid()) {
                logger.info("Username validation successful for: '{}'", username);
            } else {
                logger.warn("Username validation failed for: '{}' - {}", username, result.getMessage());
            }
            
            return Future.succeededFuture(result);
            
        } catch (Exception e) {
            String errorMessage = String.format("Username validation failed for: '%s'", username);
            logger.error(errorMessage, e);
            
            // Return invalid result with error details
            UsernameValidationResult errorResult = UsernameValidationResult.invalid(
                "Validation error: " + e.getMessage(),
                "VALIDATION_ERROR"
            );
            
            return Future.succeededFuture(errorResult);
        }
    }
    
    /**
     * Validates input parameters for username generation.
     * 
     * @param firstName the first name to validate
     * @param lastName the last name to validate
     * @throws InvalidNameException if parameters are invalid
     */
    private void validateInputParameters(String firstName, String lastName) {
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new InvalidNameException("First name cannot be null or empty");
        }
        
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new InvalidNameException("Last name cannot be null or empty");
        }
        
        // Additional validation for reasonable name lengths
        if (firstName.trim().length() > 100) {
            throw new InvalidNameException("First name is too long (maximum 100 characters)");
        }
        
        if (lastName.trim().length() > 100) {
            throw new InvalidNameException("Last name is too long (maximum 100 characters)");
        }
        
        // Check for names that contain only whitespace or special characters
        if (firstName.trim().replaceAll("[^a-zA-Z0-9]", "").isEmpty()) {
            throw new InvalidNameException("First name must contain at least one alphanumeric character");
        }
        
        if (lastName.trim().replaceAll("[^a-zA-Z0-9]", "").isEmpty()) {
            throw new InvalidNameException("Last name must contain at least one alphanumeric character");
        }
    }
}