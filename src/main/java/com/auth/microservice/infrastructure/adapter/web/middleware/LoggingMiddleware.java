package com.auth.microservice.infrastructure.adapter.web.middleware;

import com.auth.microservice.infrastructure.adapter.logging.StructuredLogger;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Middleware that logs HTTP requests with geolocation information.
 * Integrates with StructuredLogger for consistent logging format.
 */
public class LoggingMiddleware implements Handler<RoutingContext> {
    
    private final StructuredLogger structuredLogger;
    
    public LoggingMiddleware(StructuredLogger structuredLogger) {
        this.structuredLogger = structuredLogger;
    }
    
    @Override
    public void handle(RoutingContext context) {
        long startTime = System.currentTimeMillis();
        
        // Extract request information
        String method = context.request().method().name();
        String path = context.request().path();
        String ipAddress = getClientIpAddress(context);
        String userAgent = context.request().getHeader("User-Agent");
        
        // Get user ID from context if available (set by authentication middleware)
        String userId = context.get("userId");
        
        // Continue with the request
        context.addBodyEndHandler(v -> {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = context.response().getStatusCode();
            
            // Log the API request asynchronously
            structuredLogger.logApiRequest(method, path, ipAddress, userAgent, userId, statusCode, duration)
                .onFailure(throwable -> {
                    // If geolocation fails, we still want to log the request
                    structuredLogger.logApplicationError("logging_error", 
                        "Failed to log API request with geolocation", throwable, null);
                });
        });
        
        context.next();
    }
    
    /**
     * Extracts the client IP address from the request, considering proxy headers.
     */
    private String getClientIpAddress(RoutingContext context) {
        // Check for X-Forwarded-For header (common in load balancers/proxies)
        String xForwardedFor = context.request().getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        
        // Check for X-Real-IP header (used by some proxies)
        String xRealIp = context.request().getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }
        
        // Check for CF-Connecting-IP header (Cloudflare)
        String cfConnectingIp = context.request().getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
            return cfConnectingIp.trim();
        }
        
        // Fall back to remote address
        return context.request().remoteAddress().host();
    }
}