package com.auth.microservice.infrastructure.adapter.security;

import com.auth.microservice.domain.service.UsernameCollisionService;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.domain.exception.UsernameGenerationException;
import com.auth.microservice.domain.exception.UsernameCollisionException;
import com.auth.microservice.domain.exception.UsernameGenerationLimitException;
import com.auth.microservice.infrastructure.adapter.logging.UsernameGenerationAuditLogger;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.UUID;

/**
 * Implementation of UsernameCollisionService that resolves username collisions
 * by appending numeric suffixes and provides UUID fallback for extreme cases.
 * 
 * This service handles collision resolution by:
 * - Starting with base username and checking availability
 * - Appending numeric suffixes (2, 3, 4, etc.) for collisions
 * - Limiting attempts to 100 iterations before fallback
 * - Using UUID suffix as fallback for extreme collision cases
 * - Ensuring final username stays within length limits
 */
public class UsernameCollisionServiceImpl implements UsernameCollisionService {
    
    private static final Logger logger = LoggerFactory.getLogger(UsernameCollisionServiceImpl.class);
    
    // Maximum number of numeric suffix attempts before falling back to UUID
    private static final int MAX_COLLISION_ATTEMPTS = 100;
    
    // Maximum username length constraint
    private static final int MAX_USERNAME_LENGTH = 64;
    
    // Length of UUID suffix to use for fallback
    private static final int UUID_SUFFIX_LENGTH = 8;
    
    private final UserRepository userRepository;
    private final UsernameGenerationAuditLogger auditLogger;
    
    /**
     * Creates a new UsernameCollisionServiceImpl with the specified dependencies.
     * 
     * @param userRepository the repository for checking username existence
     * @param auditLogger audit logger for collision resolution events
     */
    public UsernameCollisionServiceImpl(UserRepository userRepository, UsernameGenerationAuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
    }
    
    @Override
    public Future<String> resolveCollision(String baseUsername) {
        return resolveCollision(baseUsername, null);
    }
    
    /**
     * Resolves username collisions with audit logging support.
     * 
     * @param baseUsername base username to resolve collisions for
     * @param requestId request ID for audit logging (can be null)
     * @return Future containing unique username
     */
    public Future<String> resolveCollision(String baseUsername, String requestId) {
        if (baseUsername == null || baseUsername.trim().isEmpty()) {
            return Future.failedFuture(new UsernameGenerationException("Base username cannot be null or empty"));
        }
        
        String normalizedBaseUsername = baseUsername.trim().toLowerCase();
        
        logger.info("Starting collision resolution for base username: {}", normalizedBaseUsername);
        
        // First, check if the base username is available
        return userRepository.existsByUsername(normalizedBaseUsername)
            .compose(exists -> {
                // Log collision resolution attempt
                if (requestId != null) {
                    auditLogger.logCollisionResolutionAttempt(requestId, normalizedBaseUsername, 1,
                                                            normalizedBaseUsername, exists, "base_check");
                }
                
                if (!exists) {
                    logger.info("Base username '{}' is available", normalizedBaseUsername);
                    return Future.succeededFuture(normalizedBaseUsername);
                }
                
                logger.info("Base username '{}' exists, starting numeric suffix resolution", normalizedBaseUsername);
                return resolveWithNumericSuffix(normalizedBaseUsername, requestId);
            })
            .recover(throwable -> {
                logger.error("Error during collision resolution for username: {}", normalizedBaseUsername, throwable);
                
                if (throwable instanceof UsernameCollisionException || 
                    throwable instanceof UsernameGenerationLimitException) {
                    return Future.failedFuture(throwable);
                }
                
                return Future.failedFuture(new UsernameGenerationException(
                    "Failed to resolve collision for username: " + normalizedBaseUsername, throwable));
            });
    }
    
    @Override
    public String generateWithUuidSuffix(String baseUsername) {
        if (baseUsername == null || baseUsername.trim().isEmpty()) {
            throw new UsernameGenerationException("Base username cannot be null or empty for UUID suffix generation");
        }
        
        String normalizedBaseUsername = baseUsername.trim().toLowerCase();
        
        // Generate a short UUID suffix
        String uuidSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, UUID_SUFFIX_LENGTH);
        
        // Calculate available space for base username
        int availableLength = MAX_USERNAME_LENGTH - UUID_SUFFIX_LENGTH;
        
        // Truncate base username if necessary to accommodate UUID suffix
        String truncatedBase = normalizedBaseUsername.length() > availableLength 
            ? normalizedBaseUsername.substring(0, availableLength)
            : normalizedBaseUsername;
        
        String usernameWithUuid = truncatedBase + uuidSuffix;
        
        logger.info("Generated UUID fallback username: {} (from base: {})", usernameWithUuid, normalizedBaseUsername);
        
        return usernameWithUuid;
    }
    
    /**
     * Resolves username collision by trying numeric suffixes (2, 3, 4, etc.)
     * up to the maximum number of attempts.
     * 
     * @param baseUsername the base username to resolve collisions for
     * @param requestId request ID for audit logging (can be null)
     * @return Future containing unique username with numeric suffix
     */
    private Future<String> resolveWithNumericSuffix(String baseUsername, String requestId) {
        return tryNumericSuffix(baseUsername, 2, requestId);
    }
    
    /**
     * Recursively tries numeric suffixes starting from the given suffix number.
     * 
     * @param baseUsername the base username
     * @param suffixNumber the current suffix number to try
     * @param requestId request ID for audit logging (can be null)
     * @return Future containing unique username or fallback to UUID
     */
    private Future<String> tryNumericSuffix(String baseUsername, int suffixNumber, String requestId) {
        if (suffixNumber > MAX_COLLISION_ATTEMPTS + 1) {
            logger.warn("Exceeded maximum collision attempts ({}) for username: {}, falling back to UUID", 
                       MAX_COLLISION_ATTEMPTS, baseUsername);
            
            // Log the limit exceeded event
            if (requestId != null) {
                auditLogger.logCollisionResolutionAttempt(requestId, baseUsername, suffixNumber - 1,
                                                        baseUsername + (suffixNumber - 1), true, "uuid_fallback");
            }
            
            // Throw specific exception for limit exceeded
            throw new UsernameGenerationLimitException(MAX_COLLISION_ATTEMPTS, "MAX_COLLISION_ATTEMPTS",
                "Exceeded maximum collision resolution attempts, using UUID fallback");
        }
        
        String candidateUsername = baseUsername + suffixNumber;
        
        // Check if the candidate username exceeds maximum length
        if (candidateUsername.length() > MAX_USERNAME_LENGTH) {
            logger.warn("Username with suffix '{}' exceeds maximum length, falling back to UUID", candidateUsername);
            
            // Log the length limit event
            if (requestId != null) {
                auditLogger.logCollisionResolutionAttempt(requestId, baseUsername, suffixNumber,
                                                        candidateUsername, true, "length_limit_fallback");
            }
            
            return Future.succeededFuture(generateWithUuidSuffix(baseUsername));
        }
        
        return userRepository.existsByUsername(candidateUsername)
            .compose(exists -> {
                // Log collision resolution attempt
                if (requestId != null) {
                    auditLogger.logCollisionResolutionAttempt(requestId, baseUsername, suffixNumber,
                                                            candidateUsername, exists, "numeric_suffix");
                }
                
                if (!exists) {
                    logger.info("Found available username with suffix: {}", candidateUsername);
                    return Future.succeededFuture(candidateUsername);
                }
                
                // Try next suffix number
                return tryNumericSuffix(baseUsername, suffixNumber + 1, requestId);
            })
            .recover(throwable -> {
                logger.error("Error checking username existence for: {}", candidateUsername, throwable);
                
                // Throw specific collision exception if we've made multiple attempts
                if (suffixNumber > 10) {
                    return Future.failedFuture(new UsernameCollisionException(baseUsername, suffixNumber - 1,
                        "Failed to resolve collision after multiple attempts: " + throwable.getMessage()));
                }
                
                return Future.failedFuture(new UsernameGenerationException(
                    "Failed to check username availability: " + candidateUsername, throwable));
            });
    }
}