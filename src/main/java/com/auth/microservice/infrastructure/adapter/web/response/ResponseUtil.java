package com.auth.microservice.infrastructure.adapter.web.response;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;
import java.util.List;

/**
 * Utilidad para crear respuestas HTTP estandarizadas
 * Proporciona métodos para respuestas comunes de la API
 */
public class ResponseUtil {
    
    /**
     * Envía respuesta de éxito con datos
     */
    public static void sendSuccess(RoutingContext context, JsonObject data) {
        sendSuccess(context, 200, data);
    }
    
    /**
     * Envía respuesta de éxito con código de estado específico
     */
    public static void sendSuccess(RoutingContext context, int statusCode, JsonObject data) {
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("data", data)
            .put("timestamp", Instant.now().toString());
        
        context.response()
            .setStatusCode(statusCode)
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
    }
    
    /**
     * Envía respuesta de éxito con lista de datos
     */
    public static void sendSuccess(RoutingContext context, JsonArray data) {
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("data", data)
            .put("timestamp", Instant.now().toString());
        
        context.response()
            .setStatusCode(200)
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
    }
    
    /**
     * Envía respuesta de éxito con datos paginados
     */
    public static void sendPaginatedSuccess(RoutingContext context, JsonArray data, 
                                          int page, int size, long totalElements) {
        JsonObject pagination = new JsonObject()
            .put("page", page)
            .put("size", size)
            .put("totalElements", totalElements)
            .put("totalPages", (int) Math.ceil((double) totalElements / size));
        
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("data", data)
            .put("pagination", pagination)
            .put("timestamp", Instant.now().toString());
        
        context.response()
            .setStatusCode(200)
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
    }
    
    /**
     * Envía respuesta de éxito sin contenido (204 No Content)
     */
    public static void sendNoContent(RoutingContext context) {
        context.response()
            .setStatusCode(204)
            .end();
    }
    
    /**
     * Envía respuesta de éxito para creación (201 Created)
     */
    public static void sendCreated(RoutingContext context, JsonObject data) {
        sendSuccess(context, 201, data);
    }
    
    /**
     * Envía respuesta de éxito para creación con location header
     */
    public static void sendCreated(RoutingContext context, JsonObject data, String location) {
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("data", data)
            .put("timestamp", Instant.now().toString());
        
        context.response()
            .setStatusCode(201)
            .putHeader("Content-Type", "application/json")
            .putHeader("Location", location)
            .end(response.encode());
    }
    
    /**
     * Envía respuesta de error con código y mensaje
     */
    public static void sendError(RoutingContext context, int statusCode, String errorCode, String message) {
        JsonObject errorResponse = new JsonObject()
            .put("success", false)
            .put("error", new JsonObject()
                .put("code", errorCode)
                .put("message", message)
                .put("timestamp", Instant.now().toString())
                .put("path", context.request().path()));
        
        context.response()
            .setStatusCode(statusCode)
            .putHeader("Content-Type", "application/json")
            .end(errorResponse.encode());
    }
    
    /**
     * Envía respuesta de error de validación
     */
    public static void sendValidationError(RoutingContext context, List<String> errors) {
        JsonObject errorResponse = new JsonObject()
            .put("success", false)
            .put("error", new JsonObject()
                .put("code", "VALIDATION_ERROR")
                .put("message", "Request validation failed")
                .put("details", new JsonArray(errors))
                .put("timestamp", Instant.now().toString())
                .put("path", context.request().path()));
        
        context.response()
            .setStatusCode(400)
            .putHeader("Content-Type", "application/json")
            .end(errorResponse.encode());
    }
    
    /**
     * Envía respuesta de error de autenticación
     */
    public static void sendUnauthorized(RoutingContext context, String message) {
        sendError(context, 401, "UNAUTHORIZED", message);
    }
    
    /**
     * Envía respuesta de error de autorización
     */
    public static void sendForbidden(RoutingContext context, String message) {
        sendError(context, 403, "FORBIDDEN", message);
    }
    
    /**
     * Envía respuesta de recurso no encontrado
     */
    public static void sendNotFound(RoutingContext context, String message) {
        sendError(context, 404, "NOT_FOUND", message);
    }
    
    /**
     * Envía respuesta de conflicto (recurso ya existe)
     */
    public static void sendConflict(RoutingContext context, String message) {
        sendError(context, 409, "CONFLICT", message);
    }
    
    /**
     * Envía respuesta de error interno del servidor
     */
    public static void sendInternalError(RoutingContext context, String message) {
        sendError(context, 500, "INTERNAL_SERVER_ERROR", message);
    }
    
    /**
     * Envía respuesta de rate limit excedido
     */
    public static void sendRateLimitExceeded(RoutingContext context, int retryAfterSeconds) {
        JsonObject errorResponse = new JsonObject()
            .put("success", false)
            .put("error", new JsonObject()
                .put("code", "RATE_LIMIT_EXCEEDED")
                .put("message", "Too many requests. Please try again later.")
                .put("retryAfter", retryAfterSeconds)
                .put("timestamp", Instant.now().toString())
                .put("path", context.request().path()));
        
        context.response()
            .setStatusCode(429)
            .putHeader("Content-Type", "application/json")
            .putHeader("Retry-After", String.valueOf(retryAfterSeconds))
            .end(errorResponse.encode());
    }
    
    /**
     * Crea respuesta de autenticación exitosa con tokens
     */
    public static JsonObject createAuthSuccessResponse(String accessToken, String refreshToken, 
                                                      JsonObject userInfo) {
        return new JsonObject()
            .put("accessToken", accessToken)
            .put("refreshToken", refreshToken)
            .put("tokenType", "Bearer")
            .put("user", userInfo);
    }
    
    /**
     * Crea respuesta de información de usuario
     */
    public static JsonObject createUserResponse(String userId, String email, String firstName, 
                                              String lastName, JsonArray roles) {
        return new JsonObject()
            .put("id", userId)
            .put("email", email)
            .put("firstName", firstName)
            .put("lastName", lastName)
            .put("roles", roles);
    }
    
    /**
     * Crea respuesta de información de rol
     */
    public static JsonObject createRoleResponse(String roleId, String name, String description, 
                                              JsonArray permissions) {
        return new JsonObject()
            .put("id", roleId)
            .put("name", name)
            .put("description", description)
            .put("permissions", permissions);
    }
    
    /**
     * Crea respuesta simple de éxito con mensaje
     */
    public static JsonObject createMessageResponse(String message) {
        return new JsonObject()
            .put("message", message);
    }
}