package com.auth.microservice.infrastructure.adapter.web.middleware;

import com.auth.microservice.domain.exception.InvalidTokenException;
import com.auth.microservice.domain.service.JWTService;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Middleware para autenticación JWT
 * Valida tokens JWT en el header Authorization
 */
public class AuthenticationMiddleware implements Handler<RoutingContext> {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationMiddleware.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_CONTEXT_KEY = "user";
    
    private final JWTService jwtService;
    
    public AuthenticationMiddleware(JWTService jwtService) {
        this.jwtService = jwtService;
    }
    
    @Override
    public void handle(RoutingContext context) {
        String authHeader = context.request().getHeader(AUTHORIZATION_HEADER);
        
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            logger.warn("Missing or invalid Authorization header from IP: {}", 
                context.request().remoteAddress().host());
            sendUnauthorizedResponse(context, "Missing or invalid Authorization header");
            return;
        }
        
        String token = authHeader.substring(BEARER_PREFIX.length());
        
        jwtService.validateToken(token)
            .onSuccess(claims -> {
                // Extraer información del usuario del token
                String userId = claims.getSubject();
                String email = claims.get("email", String.class);
                
                // Crear contexto de usuario
                JsonObject userContext = new JsonObject()
                    .put("userId", userId)
                    .put("email", email)
                    .put("token", token);
                
                // Agregar información adicional si está disponible
                if (claims.containsKey("roles")) {
                    userContext.put("roles", claims.get("roles"));
                }
                
                context.put(USER_CONTEXT_KEY, userContext);
                
                logger.debug("User authenticated successfully: {} from IP: {}", 
                    email, context.request().remoteAddress().host());
                
                context.next();
            })
            .onFailure(throwable -> {
                logger.warn("Token validation failed from IP: {} - Error: {}", 
                    context.request().remoteAddress().host(), throwable.getMessage());
                
                if (throwable instanceof InvalidTokenException) {
                    sendUnauthorizedResponse(context, "Invalid or expired token");
                } else {
                    sendUnauthorizedResponse(context, "Authentication failed");
                }
            });
    }
    
    private void sendUnauthorizedResponse(RoutingContext context, String message) {
        JsonObject errorResponse = new JsonObject()
            .put("error", new JsonObject()
                .put("code", "UNAUTHORIZED")
                .put("message", message)
                .put("timestamp", java.time.Instant.now().toString())
                .put("path", context.request().path()));
        
        context.response()
            .setStatusCode(401)
            .putHeader("Content-Type", "application/json")
            .end(errorResponse.encode());
    }
    
    /**
     * Obtiene el contexto del usuario autenticado
     */
    public static JsonObject getUserContext(RoutingContext context) {
        return context.get(USER_CONTEXT_KEY);
    }
    
    /**
     * Obtiene el ID del usuario autenticado
     */
    public static String getUserId(RoutingContext context) {
        JsonObject userContext = getUserContext(context);
        return userContext != null ? userContext.getString("userId") : null;
    }
    
    /**
     * Obtiene el email del usuario autenticado
     */
    public static String getUserEmail(RoutingContext context) {
        JsonObject userContext = getUserContext(context);
        return userContext != null ? userContext.getString("email") : null;
    }
}