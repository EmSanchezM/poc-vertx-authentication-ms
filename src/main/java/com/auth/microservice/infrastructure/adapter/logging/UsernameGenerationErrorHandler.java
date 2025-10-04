package com.auth.microservice.infrastructure.adapter.logging;

import com.auth.microservice.domain.exception.InvalidNameException;
import com.auth.microservice.domain.exception.UsernameCollisionException;
import com.auth.microservice.domain.exception.UsernameGenerationException;
import com.auth.microservice.domain.exception.UsernameGenerationLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Specialized error handler for username generation operations.
 * Provides comprehensive error handling, logging, and meaningful error messages
 * for all types of username generation exceptions.
 * 
 * This handler categorizes and processes different types of exceptions:
 * - InvalidNameException: Issues with input names
 * - UsernameCollisionException: Unresolvable username collisions
 * - UsernameGenerationLimitException: System limits exceeded
 * - General UsernameGenerationException: Other generation failures
 */
public class UsernameGenerationErrorHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(UsernameGenerationErrorHandler.class);
    private static final Logger ERROR_LOG = LoggerFactory.getLogger("ERROR");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    /**
     * Handles and logs username generation exceptions with appropriate error categorization.
     * 
     * @param exception the exception that occurred
     * @param firstName original first name input
     * @param lastName original last name input
     * @param requestId unique request identifier
     * @return user-friendly error message
     */
    public String handleException(Exception exception, String firstName, String lastName, String requestId) {
        String errorMessage;
        String errorCategory;
        Map<String, String> errorContext = new HashMap<>();
        
        // Add common context
        errorContext.put("request_id", requestId);
        errorContext.put("first_name", firstName);
        errorContext.put("last_name", lastName);
        errorContext.put("exception_class", exception.getClass().getSimpleName());
        
        if (exception instanceof InvalidNameException) {
            errorMessage = handleInvalidNameException((InvalidNameException) exception, errorContext);
            errorCategory = "INVALID_NAME";
        } else if (exception instanceof UsernameCollisionException) {
            errorMessage = handleUsernameCollisionException((UsernameCollisionException) exception, errorContext);
            errorCategory = "COLLISION_UNRESOLVABLE";
        } else if (exception instanceof UsernameGenerationLimitException) {
            errorMessage = handleUsernameGenerationLimitException((UsernameGenerationLimitException) exception, errorContext);
            errorCategory = "LIMIT_EXCEEDED";
        } else if (exception instanceof UsernameGenerationException) {
            errorMessage = handleUsernameGenerationException((UsernameGenerationException) exception, errorContext);
            errorCategory = "GENERATION_FAILURE";
        } else {
            errorMessage = handleGenericException(exception, errorContext);
            errorCategory = "UNEXPECTED_ERROR";
        }
        
        // Log the error with structured logging
        logError(errorCategory, errorMessage, exception, errorContext);
        
        return errorMessage;
    }
    
    /**
     * Handles InvalidNameException with specific error messages for different name issues.
     */
    private String handleInvalidNameException(InvalidNameException exception, Map<String, String> context) {
        String originalMessage = exception.getMessage();
        context.put("error_type", "invalid_name");
        
        // Provide user-friendly messages based on the specific issue
        if (originalMessage.contains("null or empty")) {
            context.put("issue", "missing_name");
            return "Both first name and last name are required for username generation.";
        } else if (originalMessage.contains("too long")) {
            context.put("issue", "name_too_long");
            return "The provided name is too long. Names must be 100 characters or less.";
        } else if (originalMessage.contains("alphanumeric character")) {
            context.put("issue", "no_valid_characters");
            return "The provided name must contain at least one letter or number.";
        } else if (originalMessage.contains("normalization resulted in empty")) {
            context.put("issue", "normalization_empty");
            return "The provided name contains only special characters and cannot be used for username generation.";
        } else {
            context.put("issue", "general_invalid");
            return "The provided name is not valid for username generation. Please use names with letters and numbers.";
        }
    }
    
    /**
     * Handles UsernameCollisionException with information about collision resolution attempts.
     */
    private String handleUsernameCollisionException(UsernameCollisionException exception, Map<String, String> context) {
        context.put("error_type", "collision_unresolvable");
        context.put("base_username", exception.getBaseUsername());
        context.put("attempt_count", String.valueOf(exception.getAttemptCount()));
        
        return String.format(
            "Unable to generate a unique username after %d attempts. " +
            "This name combination is very common. Please try using a middle name or initial.",
            exception.getAttemptCount()
        );
    }
    
    /**
     * Handles UsernameGenerationLimitException with information about exceeded limits.
     */
    private String handleUsernameGenerationLimitException(UsernameGenerationLimitException exception, Map<String, String> context) {
        context.put("error_type", "limit_exceeded");
        context.put("limit_type", exception.getLimitType());
        context.put("max_attempts", String.valueOf(exception.getMaxAttempts()));
        
        if ("MAX_ATTEMPTS".equals(exception.getLimitType())) {
            return String.format(
                "Username generation exceeded the maximum number of attempts (%d). " +
                "This indicates an extremely common name combination. Please contact support.",
                exception.getMaxAttempts()
            );
        } else {
            return "Username generation exceeded system limits. Please try with a shorter name or contact support.";
        }
    }
    
    /**
     * Handles general UsernameGenerationException.
     */
    private String handleUsernameGenerationException(UsernameGenerationException exception, Map<String, String> context) {
        context.put("error_type", "generation_failure");
        
        String originalMessage = exception.getMessage();
        
        if (originalMessage.contains("validation")) {
            context.put("issue", "validation_failure");
            return "The generated username did not meet security requirements. Please try with a different name.";
        } else if (originalMessage.contains("database") || originalMessage.contains("repository")) {
            context.put("issue", "database_error");
            return "A temporary system error occurred. Please try again in a moment.";
        } else {
            context.put("issue", "general_failure");
            return "Username generation failed due to an unexpected error. Please try again or contact support.";
        }
    }
    
    /**
     * Handles unexpected exceptions that are not username generation specific.
     */
    private String handleGenericException(Exception exception, Map<String, String> context) {
        context.put("error_type", "unexpected_error");
        
        return "An unexpected error occurred during username generation. Please try again or contact support.";
    }
    
    /**
     * Logs the error with structured logging format.
     */
    private void logError(String errorCategory, String userMessage, Exception exception, Map<String, String> context) {
        try {
            MDC.put("event_type", "username_generation_error");
            MDC.put("error_category", errorCategory);
            MDC.put("user_message", userMessage);
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            // Add all context to MDC
            context.forEach(MDC::put);
            
            // Log at appropriate level based on error type
            if (exception instanceof InvalidNameException) {
                logger.warn("Invalid name provided for username generation: {}", userMessage);
            } else if (exception instanceof UsernameCollisionException) {
                logger.warn("Username collision could not be resolved: {}", userMessage);
            } else if (exception instanceof UsernameGenerationLimitException) {
                logger.error("Username generation limit exceeded: {}", userMessage);
            } else {
                logger.error("Username generation error: {}", userMessage, exception);
            }
            
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Provides recovery suggestions based on the type of exception.
     * 
     * @param exception the exception that occurred
     * @return suggested recovery actions
     */
    public String getRecoverySuggestion(Exception exception) {
        if (exception instanceof InvalidNameException) {
            return "Please ensure both first and last names contain letters or numbers and are not too long.";
        } else if (exception instanceof UsernameCollisionException) {
            return "Try using a middle name, initial, or a less common variation of your name.";
        } else if (exception instanceof UsernameGenerationLimitException) {
            return "Contact system administrator - this name combination may need manual handling.";
        } else {
            return "Try again in a moment, or contact support if the problem persists.";
        }
    }
    
    /**
     * Determines if the error is recoverable by the user.
     * 
     * @param exception the exception that occurred
     * @return true if the user can potentially resolve the issue
     */
    public boolean isRecoverable(Exception exception) {
        return exception instanceof InvalidNameException || 
               exception instanceof UsernameCollisionException;
    }
    
    /**
     * Gets the appropriate HTTP status code for the exception.
     * 
     * @param exception the exception that occurred
     * @return HTTP status code
     */
    public int getHttpStatusCode(Exception exception) {
        if (exception instanceof InvalidNameException) {
            return 400; // Bad Request
        } else if (exception instanceof UsernameCollisionException) {
            return 409; // Conflict
        } else if (exception instanceof UsernameGenerationLimitException) {
            return 503; // Service Unavailable
        } else {
            return 500; // Internal Server Error
        }
    }
}