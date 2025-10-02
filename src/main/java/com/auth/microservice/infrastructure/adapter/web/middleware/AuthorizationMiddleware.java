package com.auth.microservice.infrastructure.adapter.web.middleware;

import com.auth.microservice.common.cqrs.QueryBus;
import com.auth.microservice.application.query.CheckPermissionQuery;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Middleware para autorización RBAC
 * Verifica permisos específicos basados en roles del usuario
 */
public class AuthorizationMiddleware implements Handler<RoutingContext> {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthorizationMiddleware.class);
    
    private final QueryBus queryBus;
    private final String requiredPermission;
    private final String resource;
    private final String action;
    
    public AuthorizationMiddleware(QueryBus queryBus, String requiredPermission) {
        this.queryBus = queryBus;
        this.requiredPermission = requiredPermission;
        
        // Parsear el permiso en resource:action
        String[] parts = requiredPermission.split(":");
        this.resource = parts.length > 0 ? parts[0] : "";
        this.action = parts.length > 1 ? parts[1] : "";
    }
    
    public AuthorizationMiddleware(QueryBus queryBus, String resource, String action) {
        this.queryBus = queryBus;
        this.resource = resource;
        this.action = action;
        this.requiredPermission = resource + ":" + action;
    }
    
    @Override
    public void handle(RoutingContext context) {
        JsonObject userContext = AuthenticationMiddleware.getUserContext(context);
        
        if (userContext == null) {
            logger.warn("Authorization attempted without authentication context from IP: {}", 
                context.request().remoteAddress().host());
            sendForbiddenResponse(context, "Authentication required");
            return;
        }
        
        String userId = userContext.getString("userId");
        
        CheckPermissionQuery query = new CheckPermissionQuery(userId, resource, action);
        
        queryBus.execute(query)
            .onSuccess(hasPermission -> {
                if (hasPermission) {
                    logger.debug("User {} authorized for permission: {} from IP: {}", 
                        userContext.getString("email"), requiredPermission, 
                        context.request().remoteAddress().host());
                    context.next();
                } else {
                    logger.warn("User {} denied access to permission: {} from IP: {}", 
                        userContext.getString("email"), requiredPermission, 
                        context.request().remoteAddress().host());
                    sendForbiddenResponse(context, 
                        "Insufficient permissions. Required: " + requiredPermission);
                }
            })
            .onFailure(throwable -> {
                logger.error("Error checking permissions for user {} from IP: {} - Error: {}", 
                    userContext.getString("email"), 
                    context.request().remoteAddress().host(), 
                    throwable.getMessage(), throwable);
                sendInternalErrorResponse(context, "Authorization check failed");
            });
    }
    
    private void sendForbiddenResponse(RoutingContext context, String message) {
        JsonObject errorResponse = new JsonObject()
            .put("error", new JsonObject()
                .put("code", "FORBIDDEN")
                .put("message", message)
                .put("timestamp", java.time.Instant.now().toString())
                .put("path", context.request().path()));
        
        context.response()
            .setStatusCode(403)
            .putHeader("Content-Type", "application/json")
            .end(errorResponse.encode());
    }
    
    private void sendInternalErrorResponse(RoutingContext context, String message) {
        JsonObject errorResponse = new JsonObject()
            .put("error", new JsonObject()
                .put("code", "INTERNAL_ERROR")
                .put("message", message)
                .put("timestamp", java.time.Instant.now().toString())
                .put("path", context.request().path()));
        
        context.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json")
            .end(errorResponse.encode());
    }
    
    /**
     * Factory method para crear middleware de autorización con permiso específico
     */
    public static AuthorizationMiddleware requirePermission(QueryBus queryBus, String permission) {
        return new AuthorizationMiddleware(queryBus, permission);
    }
    
    /**
     * Factory method para crear middleware de autorización con resource y action
     */
    public static AuthorizationMiddleware requirePermission(QueryBus queryBus, String resource, String action) {
        return new AuthorizationMiddleware(queryBus, resource, action);
    }
}