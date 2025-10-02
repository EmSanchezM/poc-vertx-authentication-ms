package com.auth.microservice.infrastructure.adapter.web.middleware;

import com.auth.microservice.domain.exception.*;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Middleware para manejo global de errores
 * Convierte excepciones en respuestas HTTP apropiadas
 */
public class ErrorHandlerMiddleware implements Handler<RoutingContext> {
    
    private static final Logger logger = LoggerFactory.getLogger(ErrorHandlerMiddleware.class);
    
    @Override
    public void handle(RoutingContext context) {
        Throwable failure = context.failure();
        
        if (failure == null) {
            // No hay error, continuar
            context.next();
            return;
        }
        
        logger.error("Request failed: {} {} from IP: {} - Error: {}", 
            context.request().method(), 
            context.request().path(),
            context.request().remoteAddress().host(),
            failure.getMessage(), 
            failure);
        
        // Determinar código de estado y mensaje basado en el tipo de excepción
        ErrorResponse errorResponse = mapExceptionToErrorResponse(failure, context);
        
        // Crear respuesta JSON de error
        JsonObject responseBody = new JsonObject()
            .put("error", new JsonObject()
                .put("code", errorResponse.code)
                .put("message", errorResponse.message)
                .put("timestamp", java.time.Instant.now().toString())
                .put("path", context.request().path()));
        
        // Agregar detalles adicionales en desarrollo
        if (isDevelopmentMode()) {
            responseBody.getJsonObject("error")
                .put("details", failure.getMessage())
                .put("type", failure.getClass().getSimpleName());
        }
        
        context.response()
            .setStatusCode(errorResponse.statusCode)
            .putHeader("Content-Type", "application/json")
            .end(responseBody.encode());
    }
    
    private ErrorResponse mapExceptionToErrorResponse(Throwable throwable, RoutingContext context) {
        // Excepciones de dominio específicas
        if (throwable instanceof AuthenticationException) {
            return new ErrorResponse(401, "AUTHENTICATION_FAILED", 
                "Invalid credentials or authentication failed");
        }
        
        if (throwable instanceof InvalidTokenException) {
            return new ErrorResponse(401, "INVALID_TOKEN", 
                "Token is invalid or expired");
        }
        
        if (throwable instanceof UserNotFoundException) {
            return new ErrorResponse(404, "USER_NOT_FOUND", 
                "User not found");
        }
        
        if (throwable instanceof UserAlreadyExistsException) {
            return new ErrorResponse(409, "USER_ALREADY_EXISTS", 
                "User with this email already exists");
        }
        
        if (throwable instanceof RoleNotFoundException) {
            return new ErrorResponse(404, "ROLE_NOT_FOUND", 
                "Role not found");
        }
        
        if (throwable instanceof RoleAlreadyExistsException) {
            return new ErrorResponse(409, "ROLE_ALREADY_EXISTS", 
                "Role with this name already exists");
        }
        
        if (throwable instanceof SessionNotFoundException) {
            return new ErrorResponse(404, "SESSION_NOT_FOUND", 
                "Session not found or expired");
        }
        
        // Excepciones de validación
        if (throwable instanceof IllegalArgumentException) {
            return new ErrorResponse(400, "INVALID_REQUEST", 
                "Invalid request parameters: " + throwable.getMessage());
        }
        
        // Excepciones de autorización (403 Forbidden)
        if (throwable.getMessage() != null && 
            throwable.getMessage().toLowerCase().contains("permission")) {
            return new ErrorResponse(403, "INSUFFICIENT_PERMISSIONS", 
                "Insufficient permissions to perform this action");
        }
        
        // Errores de validación de JSON/request body
        if (throwable.getMessage() != null && 
            (throwable.getMessage().contains("JSON") || 
             throwable.getMessage().contains("validation"))) {
            return new ErrorResponse(400, "INVALID_REQUEST_FORMAT", 
                "Invalid request format or missing required fields");
        }
        
        // Error genérico del servidor
        return new ErrorResponse(500, "INTERNAL_SERVER_ERROR", 
            "An internal server error occurred");
    }
    
    private boolean isDevelopmentMode() {
        String environment = System.getenv().getOrDefault("ENVIRONMENT", "development");
        return "development".equalsIgnoreCase(environment) || "dev".equalsIgnoreCase(environment);
    }
    
    /**
     * Clase interna para representar respuestas de error
     */
    private static class ErrorResponse {
        final int statusCode;
        final String code;
        final String message;
        
        ErrorResponse(int statusCode, String code, String message) {
            this.statusCode = statusCode;
            this.code = code;
            this.message = message;
        }
    }
    
    /**
     * Handler específico para errores 404 (Not Found)
     */
    public static Handler<RoutingContext> notFoundHandler() {
        return context -> {
            JsonObject errorResponse = new JsonObject()
                .put("error", new JsonObject()
                    .put("code", "NOT_FOUND")
                    .put("message", "The requested resource was not found")
                    .put("timestamp", java.time.Instant.now().toString())
                    .put("path", context.request().path()));
            
            context.response()
                .setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end(errorResponse.encode());
        };
    }
    
    /**
     * Handler específico para errores 405 (Method Not Allowed)
     */
    public static Handler<RoutingContext> methodNotAllowedHandler() {
        return context -> {
            JsonObject errorResponse = new JsonObject()
                .put("error", new JsonObject()
                    .put("code", "METHOD_NOT_ALLOWED")
                    .put("message", "HTTP method not allowed for this endpoint")
                    .put("timestamp", java.time.Instant.now().toString())
                    .put("path", context.request().path()));
            
            context.response()
                .setStatusCode(405)
                .putHeader("Content-Type", "application/json")
                .end(errorResponse.encode());
        };
    }
}