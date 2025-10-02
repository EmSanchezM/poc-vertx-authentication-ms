package com.auth.microservice.infrastructure.adapter.web.middleware;

import com.auth.microservice.domain.service.GeoLocationService;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Middleware para logging de seguridad con geolocalización
 * Registra eventos de seguridad incluyendo información de ubicación
 */
public class SecurityLoggingMiddleware implements Handler<RoutingContext> {
    
    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY");
    private static final Logger logger = LoggerFactory.getLogger(SecurityLoggingMiddleware.class);
    
    private final GeoLocationService geoLocationService;
    
    public SecurityLoggingMiddleware(GeoLocationService geoLocationService) {
        this.geoLocationService = geoLocationService;
    }
    
    @Override
    public void handle(RoutingContext context) {
        String clientIp = getClientIp(context);
        
        // Obtener información de geolocalización de forma asíncrona
        geoLocationService.getCountryFromIp(clientIp)
            .onSuccess(country -> {
                setupSecurityLogging(context, clientIp, country);
                context.next();
            })
            .onFailure(throwable -> {
                logger.debug("Failed to get geolocation for IP {}: {}", clientIp, throwable.getMessage());
                setupSecurityLogging(context, clientIp, "Unknown");
                context.next();
            });
    }
    
    private void setupSecurityLogging(RoutingContext context, String clientIp, String country) {
        JsonObject userContext = AuthenticationMiddleware.getUserContext(context);
        String userId = userContext != null ? userContext.getString("userId") : "anonymous";
        String email = userContext != null ? userContext.getString("email") : "anonymous";
        
        // Configurar MDC para logging estructurado
        MDC.put("clientIp", clientIp);
        MDC.put("country", country);
        MDC.put("userId", userId);
        MDC.put("email", email);
        MDC.put("method", context.request().method().name());
        MDC.put("path", context.request().path());
        MDC.put("userAgent", context.request().getHeader("User-Agent"));
        
        // Agregar información de geolocalización al contexto
        JsonObject geoInfo = new JsonObject()
            .put("clientIp", clientIp)
            .put("country", country)
            .put("timestamp", java.time.Instant.now().toString());
        
        context.put("geoInfo", geoInfo);
        
        // Log de evento de seguridad
        logSecurityEvent(context, clientIp, country, userContext);
        
        // Limpiar MDC al final del request
        context.addEndHandler(v -> MDC.clear());
    }
    
    private void logSecurityEvent(RoutingContext context, String clientIp, String country, JsonObject userContext) {
        String method = context.request().method().name();
        String path = context.request().path();
        String userId = userContext != null ? userContext.getString("userId") : "anonymous";
        String email = userContext != null ? userContext.getString("email") : "anonymous";
        
        // Determinar el tipo de evento de seguridad
        String eventType = determineSecurityEventType(method, path);
        
        if (eventType != null) {
            JsonObject securityEvent = new JsonObject()
                .put("eventType", eventType)
                .put("userId", userId)
                .put("email", email)
                .put("clientIp", clientIp)
                .put("country", country)
                .put("method", method)
                .put("path", path)
                .put("userAgent", context.request().getHeader("User-Agent"))
                .put("timestamp", java.time.Instant.now().toString());
            
            securityLogger.info("Security Event: {}", securityEvent.encode());
        }
    }
    
    private String determineSecurityEventType(String method, String path) {
        if ("POST".equals(method)) {
            if (path.contains("/auth/login")) return "LOGIN_ATTEMPT";
            if (path.contains("/auth/register")) return "REGISTRATION_ATTEMPT";
            if (path.contains("/auth/refresh")) return "TOKEN_REFRESH";
            if (path.contains("/admin/")) return "ADMIN_ACTION";
        }
        
        if ("PUT".equals(method) || "PATCH".equals(method)) {
            if (path.contains("/users/")) return "USER_UPDATE";
            if (path.contains("/roles/")) return "ROLE_UPDATE";
        }
        
        if ("DELETE".equals(method)) {
            if (path.contains("/auth/logout")) return "LOGOUT";
            if (path.contains("/admin/")) return "ADMIN_DELETE";
        }
        
        if ("GET".equals(method)) {
            if (path.contains("/admin/")) return "ADMIN_ACCESS";
            if (path.contains("/users/profile")) return "PROFILE_ACCESS";
        }
        
        return null; // No es un evento de seguridad relevante
    }
    
    private String getClientIp(RoutingContext context) {
        // Verificar headers de proxy primero
        String xForwardedFor = context.request().getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = context.request().getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return context.request().remoteAddress().host();
    }
    
    /**
     * Log específico para eventos de autenticación exitosa
     */
    public static void logSuccessfulAuthentication(RoutingContext context, String userId, String email) {
        JsonObject geoInfo = context.get("geoInfo");
        String clientIp = geoInfo != null ? geoInfo.getString("clientIp") : "unknown";
        String country = geoInfo != null ? geoInfo.getString("country") : "unknown";
        
        JsonObject event = new JsonObject()
            .put("eventType", "LOGIN_SUCCESS")
            .put("userId", userId)
            .put("email", email)
            .put("clientIp", clientIp)
            .put("country", country)
            .put("timestamp", java.time.Instant.now().toString());
        
        securityLogger.info("Authentication Success: {}", event.encode());
    }
    
    /**
     * Log específico para eventos de autenticación fallida
     */
    public static void logFailedAuthentication(RoutingContext context, String email, String reason) {
        JsonObject geoInfo = context.get("geoInfo");
        String clientIp = geoInfo != null ? geoInfo.getString("clientIp") : "unknown";
        String country = geoInfo != null ? geoInfo.getString("country") : "unknown";
        
        JsonObject event = new JsonObject()
            .put("eventType", "LOGIN_FAILURE")
            .put("email", email)
            .put("reason", reason)
            .put("clientIp", clientIp)
            .put("country", country)
            .put("timestamp", java.time.Instant.now().toString());
        
        securityLogger.warn("Authentication Failure: {}", event.encode());
    }
    
    /**
     * Log específico para eventos de autorización denegada
     */
    public static void logAuthorizationDenied(RoutingContext context, String userId, String requiredPermission) {
        JsonObject geoInfo = context.get("geoInfo");
        String clientIp = geoInfo != null ? geoInfo.getString("clientIp") : "unknown";
        String country = geoInfo != null ? geoInfo.getString("country") : "unknown";
        
        JsonObject event = new JsonObject()
            .put("eventType", "AUTHORIZATION_DENIED")
            .put("userId", userId)
            .put("requiredPermission", requiredPermission)
            .put("clientIp", clientIp)
            .put("country", country)
            .put("path", context.request().path())
            .put("timestamp", java.time.Instant.now().toString());
        
        securityLogger.warn("Authorization Denied: {}", event.encode());
    }
}