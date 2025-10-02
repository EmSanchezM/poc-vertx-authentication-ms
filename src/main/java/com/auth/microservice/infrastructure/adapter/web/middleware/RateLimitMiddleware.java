package com.auth.microservice.infrastructure.adapter.web.middleware;

import com.auth.microservice.domain.service.RateLimitService;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Middleware para rate limiting y auditoría de requests
 * Controla la frecuencia de requests por IP y por usuario
 */
public class RateLimitMiddleware implements Handler<RoutingContext> {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitMiddleware.class);
    
    private final RateLimitService rateLimitService;
    private final String endpoint;
    private final int maxAttempts;
    private final int windowMinutes;
    
    public RateLimitMiddleware(RateLimitService rateLimitService, String endpoint, 
                              int maxAttempts, int windowMinutes) {
        this.rateLimitService = rateLimitService;
        this.endpoint = endpoint;
        this.maxAttempts = maxAttempts;
        this.windowMinutes = windowMinutes;
    }
    
    @Override
    public void handle(RoutingContext context) {
        String clientIp = getClientIp(context);
        String identifier = clientIp;
        
        // Si el usuario está autenticado, usar su ID como identificador adicional
        JsonObject userContext = AuthenticationMiddleware.getUserContext(context);
        if (userContext != null) {
            String userId = userContext.getString("userId");
            identifier = userId + ":" + clientIp;
        }
        
        // Auditoría del request
        auditRequest(context, clientIp, userContext);
        
        rateLimitService.checkRateLimit(identifier, endpoint, maxAttempts, windowMinutes)
            .onSuccess(allowed -> {
                if (allowed) {
                    logger.debug("Rate limit check passed for {} on endpoint {} from IP: {}", 
                        identifier, endpoint, clientIp);
                    context.next();
                } else {
                    logger.warn("Rate limit exceeded for {} on endpoint {} from IP: {}", 
                        identifier, endpoint, clientIp);
                    sendRateLimitResponse(context);
                }
            })
            .onFailure(throwable -> {
                logger.error("Rate limit check failed for {} on endpoint {} from IP: {} - Error: {}", 
                    identifier, endpoint, clientIp, throwable.getMessage(), throwable);
                // En caso de error, permitir el request pero loggear el problema
                context.next();
            });
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
    
    private void auditRequest(RoutingContext context, String clientIp, JsonObject userContext) {
        String method = context.request().method().name();
        String path = context.request().path();
        String userAgent = context.request().getHeader("User-Agent");
        String userId = userContext != null ? userContext.getString("userId") : "anonymous";
        String email = userContext != null ? userContext.getString("email") : "anonymous";
        
        logger.info("API Request - Method: {}, Path: {}, User: {} ({}), IP: {}, UserAgent: {}", 
            method, path, email, userId, clientIp, userAgent);
        
        // Agregar información de auditoría al contexto para uso posterior
        JsonObject auditInfo = new JsonObject()
            .put("clientIp", clientIp)
            .put("method", method)
            .put("path", path)
            .put("userAgent", userAgent)
            .put("userId", userId)
            .put("email", email)
            .put("timestamp", java.time.Instant.now().toString());
        
        context.put("auditInfo", auditInfo);
    }
    
    private void sendRateLimitResponse(RoutingContext context) {
        JsonObject errorResponse = new JsonObject()
            .put("error", new JsonObject()
                .put("code", "RATE_LIMIT_EXCEEDED")
                .put("message", "Too many requests. Please try again later.")
                .put("timestamp", java.time.Instant.now().toString())
                .put("path", context.request().path())
                .put("retryAfter", windowMinutes * 60)); // seconds
        
        context.response()
            .setStatusCode(429)
            .putHeader("Content-Type", "application/json")
            .putHeader("Retry-After", String.valueOf(windowMinutes * 60))
            .end(errorResponse.encode());
    }
    
    /**
     * Factory method para crear middleware de rate limiting para login
     */
    public static RateLimitMiddleware forLogin(RateLimitService rateLimitService) {
        return new RateLimitMiddleware(rateLimitService, "login", 5, 15); // 5 intentos por 15 minutos
    }
    
    /**
     * Factory method para crear middleware de rate limiting para registro
     */
    public static RateLimitMiddleware forRegistration(RateLimitService rateLimitService) {
        return new RateLimitMiddleware(rateLimitService, "register", 3, 60); // 3 intentos por hora
    }
    
    /**
     * Factory method para crear middleware de rate limiting general
     */
    public static RateLimitMiddleware forGeneral(RateLimitService rateLimitService, String endpoint) {
        return new RateLimitMiddleware(rateLimitService, endpoint, 100, 1); // 100 requests por minuto
    }
    
    /**
     * Factory method para crear middleware de rate limiting para endpoints administrativos
     */
    public static RateLimitMiddleware forAdmin(RateLimitService rateLimitService, String endpoint) {
        return new RateLimitMiddleware(rateLimitService, endpoint, 50, 1); // 50 requests por minuto
    }
}