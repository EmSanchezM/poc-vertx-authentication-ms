package com.auth.microservice.infrastructure.adapter.web.util;

import io.vertx.ext.web.RoutingContext;

/**
 * Utility class for common request processing operations.
 * Provides helper methods for extracting information from HTTP requests.
 */
public final class RequestUtil {
    
    private RequestUtil() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Extracts the client IP address from the request context.
     * Checks proxy headers first (X-Forwarded-For, X-Real-IP) before falling back to remote address.
     * 
     * @param context the Vert.x routing context
     * @return the client IP address as a string
     */
    public static String getClientIp(RoutingContext context) {
        // Check proxy headers first
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
     * Extracts the User-Agent header from the request context.
     * 
     * @param context the Vert.x routing context
     * @return the User-Agent string, or "Unknown" if not present
     */
    public static String getUserAgent(RoutingContext context) {
        String userAgent = context.request().getHeader("User-Agent");
        return userAgent != null ? userAgent : "Unknown";
    }
    
    /**
     * Extracts the Authorization header from the request context.
     * 
     * @param context the Vert.x routing context
     * @return the Authorization header value, or null if not present
     */
    public static String getAuthorizationHeader(RoutingContext context) {
        return context.request().getHeader("Authorization");
    }
    
    /**
     * Extracts the Bearer token from the Authorization header.
     * 
     * @param context the Vert.x routing context
     * @return the Bearer token without the "Bearer " prefix, or null if not present or invalid format
     */
    public static String getBearerToken(RoutingContext context) {
        String authHeader = getAuthorizationHeader(context);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // Remove "Bearer " prefix
        }
        return null;
    }
}