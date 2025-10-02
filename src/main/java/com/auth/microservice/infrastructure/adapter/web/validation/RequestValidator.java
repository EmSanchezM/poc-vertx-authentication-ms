package com.auth.microservice.infrastructure.adapter.web.validation;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utilidad para validación de requests HTTP
 * Proporciona validaciones comunes para endpoints REST
 */
public class RequestValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(RequestValidator.class);
    
    // Patrones de validación
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"
    );
    
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    
    /**
     * Valida el cuerpo del request como JSON
     */
    public static ValidationResult validateJsonBody(RoutingContext context) {
        try {
            JsonObject body = context.body().asJsonObject();
            if (body == null || body.isEmpty()) {
                return ValidationResult.error("Request body is required and must be valid JSON");
            }
            return ValidationResult.success(body);
        } catch (Exception e) {
            logger.warn("Invalid JSON in request body from IP: {}", 
                context.request().remoteAddress().host());
            return ValidationResult.error("Invalid JSON format in request body");
        }
    }
    
    /**
     * Valida request de login
     */
    public static ValidationResult validateLoginRequest(JsonObject body) {
        List<String> errors = new ArrayList<>();
        
        // Validar email
        String email = body.getString("email");
        if (email == null || email.trim().isEmpty()) {
            errors.add("Email is required");
        } else if (!isValidEmail(email)) {
            errors.add("Email format is invalid");
        }
        
        // Validar password
        String password = body.getString("password");
        if (password == null || password.trim().isEmpty()) {
            errors.add("Password is required");
        }
        
        return errors.isEmpty() ? ValidationResult.success(body) : ValidationResult.error(errors);
    }
    
    /**
     * Valida request de registro
     */
    public static ValidationResult validateRegistrationRequest(JsonObject body) {
        List<String> errors = new ArrayList<>();
        
        // Validar email
        String email = body.getString("email");
        if (email == null || email.trim().isEmpty()) {
            errors.add("Email is required");
        } else if (!isValidEmail(email)) {
            errors.add("Email format is invalid");
        }
        
        // Validar password
        String password = body.getString("password");
        if (password == null || password.trim().isEmpty()) {
            errors.add("Password is required");
        } else if (!isValidPassword(password)) {
            errors.add("Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character");
        }
        
        // Validar firstName
        String firstName = body.getString("firstName");
        if (firstName == null || firstName.trim().isEmpty()) {
            errors.add("First name is required");
        } else if (firstName.length() > 100) {
            errors.add("First name must not exceed 100 characters");
        }
        
        // Validar lastName
        String lastName = body.getString("lastName");
        if (lastName == null || lastName.trim().isEmpty()) {
            errors.add("Last name is required");
        } else if (lastName.length() > 100) {
            errors.add("Last name must not exceed 100 characters");
        }
        
        return errors.isEmpty() ? ValidationResult.success(body) : ValidationResult.error(errors);
    }
    
    /**
     * Valida request de refresh token
     */
    public static ValidationResult validateRefreshTokenRequest(JsonObject body) {
        List<String> errors = new ArrayList<>();
        
        String refreshToken = body.getString("refreshToken");
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            errors.add("Refresh token is required");
        }
        
        return errors.isEmpty() ? ValidationResult.success(body) : ValidationResult.error(errors);
    }
    
    /**
     * Valida request de actualización de perfil
     */
    public static ValidationResult validateUpdateProfileRequest(JsonObject body) {
        List<String> errors = new ArrayList<>();
        
        // firstName es opcional, pero si se proporciona debe ser válido
        String firstName = body.getString("firstName");
        if (firstName != null && (firstName.trim().isEmpty() || firstName.length() > 100)) {
            errors.add("First name must not be empty or exceed 100 characters");
        }
        
        // lastName es opcional, pero si se proporciona debe ser válido
        String lastName = body.getString("lastName");
        if (lastName != null && (lastName.trim().isEmpty() || lastName.length() > 100)) {
            errors.add("Last name must not be empty or exceed 100 characters");
        }
        
        // Verificar que al menos un campo esté presente
        if (firstName == null && lastName == null) {
            errors.add("At least one field (firstName or lastName) must be provided");
        }
        
        return errors.isEmpty() ? ValidationResult.success(body) : ValidationResult.error(errors);
    }
    
    /**
     * Valida request de creación de rol
     */
    public static ValidationResult validateCreateRoleRequest(JsonObject body) {
        List<String> errors = new ArrayList<>();
        
        String name = body.getString("name");
        if (name == null || name.trim().isEmpty()) {
            errors.add("Role name is required");
        } else if (name.length() > 100) {
            errors.add("Role name must not exceed 100 characters");
        }
        
        String description = body.getString("description");
        if (description != null && description.length() > 500) {
            errors.add("Role description must not exceed 500 characters");
        }
        
        return errors.isEmpty() ? ValidationResult.success(body) : ValidationResult.error(errors);
    }
    
    /**
     * Valida request de creación de usuario (admin)
     */
    public static ValidationResult validateCreateUserRequest(JsonObject body) {
        List<String> errors = new ArrayList<>();
        
        // Validar username
        String username = body.getString("username");
        if (username == null || username.trim().isEmpty()) {
            errors.add("Username is required");
        } else if (username.length() < 3 || username.length() > 50) {
            errors.add("Username must be between 3 and 50 characters");
        }
        
        // Validar email
        String email = body.getString("email");
        if (email == null || email.trim().isEmpty()) {
            errors.add("Email is required");
        } else if (!isValidEmail(email)) {
            errors.add("Email format is invalid");
        }
        
        // Validar firstName
        String firstName = body.getString("firstName");
        if (firstName == null || firstName.trim().isEmpty()) {
            errors.add("First name is required");
        } else if (firstName.length() > 100) {
            errors.add("First name must not exceed 100 characters");
        }
        
        // Validar lastName
        String lastName = body.getString("lastName");
        if (lastName == null || lastName.trim().isEmpty()) {
            errors.add("Last name is required");
        } else if (lastName.length() > 100) {
            errors.add("Last name must not exceed 100 characters");
        }
        
        return errors.isEmpty() ? ValidationResult.success(body) : ValidationResult.error(errors);
    }
    
    /**
     * Valida request de actualización de usuario (admin)
     */
    public static ValidationResult validateUpdateUserRequest(JsonObject body) {
        List<String> errors = new ArrayList<>();
        
        // firstName es opcional, pero si se proporciona debe ser válido
        String firstName = body.getString("firstName");
        if (firstName != null && (firstName.trim().isEmpty() || firstName.length() > 100)) {
            errors.add("First name must not be empty or exceed 100 characters");
        }
        
        // lastName es opcional, pero si se proporciona debe ser válido
        String lastName = body.getString("lastName");
        if (lastName != null && (lastName.trim().isEmpty() || lastName.length() > 100)) {
            errors.add("Last name must not be empty or exceed 100 characters");
        }
        
        // isActive es opcional
        Boolean isActive = body.getBoolean("isActive");
        // No validation needed for boolean, just check if present
        
        // Verificar que al menos un campo esté presente
        if (firstName == null && lastName == null && isActive == null) {
            errors.add("At least one field (firstName, lastName, or isActive) must be provided");
        }
        
        return errors.isEmpty() ? ValidationResult.success(body) : ValidationResult.error(errors);
    }
    
    /**
     * Valida parámetros de paginación
     */
    public static ValidationResult validatePaginationParams(RoutingContext context) {
        List<String> errors = new ArrayList<>();
        
        String pageStr = context.request().getParam("page");
        String sizeStr = context.request().getParam("size");
        
        int page = 0;
        int size = 20; // default
        
        if (pageStr != null) {
            try {
                page = Integer.parseInt(pageStr);
                if (page < 0) {
                    errors.add("Page number must be non-negative");
                }
            } catch (NumberFormatException e) {
                errors.add("Page number must be a valid integer");
            }
        }
        
        if (sizeStr != null) {
            try {
                size = Integer.parseInt(sizeStr);
                if (size <= 0 || size > 100) {
                    errors.add("Page size must be between 1 and 100");
                }
            } catch (NumberFormatException e) {
                errors.add("Page size must be a valid integer");
            }
        }
        
        if (!errors.isEmpty()) {
            return ValidationResult.error(errors);
        }
        
        JsonObject paginationParams = new JsonObject()
            .put("page", page)
            .put("size", size);
        
        return ValidationResult.success(paginationParams);
    }
    
    /**
     * Valida UUID en parámetros de path
     */
    public static ValidationResult validateUuidParam(String paramValue, String paramName) {
        if (paramValue == null || paramValue.trim().isEmpty()) {
            return ValidationResult.error(paramName + " is required");
        }
        
        if (!isValidUuid(paramValue)) {
            return ValidationResult.error(paramName + " must be a valid UUID");
        }
        
        return ValidationResult.success(paramValue);
    }
    
    // Métodos de validación privados
    
    private static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }
    
    private static boolean isValidPassword(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }
    
    private static boolean isValidUuid(String uuid) {
        return uuid != null && UUID_PATTERN.matcher(uuid.trim()).matches();
    }
    
    /**
     * Clase para representar resultados de validación
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final JsonObject data;
        private final String singleData;
        
        private ValidationResult(boolean valid, List<String> errors, JsonObject data, String singleData) {
            this.valid = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
            this.data = data;
            this.singleData = singleData;
        }
        
        public static ValidationResult success(JsonObject data) {
            return new ValidationResult(true, null, data, null);
        }
        
        public static ValidationResult success(String data) {
            return new ValidationResult(true, null, null, data);
        }
        
        public static ValidationResult error(String error) {
            return new ValidationResult(false, List.of(error), null, null);
        }
        
        public static ValidationResult error(List<String> errors) {
            return new ValidationResult(false, errors, null, null);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public JsonObject getData() {
            return data;
        }
        
        public String getSingleData() {
            return singleData;
        }
        
        public String getErrorMessage() {
            return String.join(", ", errors);
        }
        
        /**
         * Envía respuesta de error de validación al cliente
         */
        public void sendErrorResponse(RoutingContext context) {
            JsonObject errorResponse = new JsonObject()
                .put("error", new JsonObject()
                    .put("code", "VALIDATION_ERROR")
                    .put("message", "Request validation failed")
                    .put("details", errors)
                    .put("timestamp", java.time.Instant.now().toString())
                    .put("path", context.request().path()));
            
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(errorResponse.encode());
        }
    }
}