package com.auth.microservice.infrastructure.adapter.web.middleware;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Middleware para configuración de CORS (Cross-Origin Resource Sharing)
 * Maneja las políticas de acceso entre dominios
 */
public class CorsMiddleware implements Handler<RoutingContext> {
    
    private static final Logger logger = LoggerFactory.getLogger(CorsMiddleware.class);
    
    private final Set<String> allowedOrigins;
    private final Set<String> allowedMethods;
    private final Set<String> allowedHeaders;
    private final boolean allowCredentials;
    private final int maxAge;
    
    public CorsMiddleware(Set<String> allowedOrigins, Set<String> allowedMethods, 
                         Set<String> allowedHeaders, boolean allowCredentials, int maxAge) {
        this.allowedOrigins = allowedOrigins;
        this.allowedMethods = allowedMethods;
        this.allowedHeaders = allowedHeaders;
        this.allowCredentials = allowCredentials;
        this.maxAge = maxAge;
    }
    
    @Override
    public void handle(RoutingContext context) {
        String origin = context.request().getHeader("Origin");
        String method = context.request().method().name();
        
        // Verificar si el origen está permitido
        if (origin != null && isOriginAllowed(origin)) {
            context.response().putHeader("Access-Control-Allow-Origin", origin);
        } else if (allowedOrigins.contains("*")) {
            context.response().putHeader("Access-Control-Allow-Origin", "*");
        }
        
        // Configurar headers CORS
        if (!allowedMethods.isEmpty()) {
            context.response().putHeader("Access-Control-Allow-Methods", 
                String.join(", ", allowedMethods));
        }
        
        if (!allowedHeaders.isEmpty()) {
            context.response().putHeader("Access-Control-Allow-Headers", 
                String.join(", ", allowedHeaders));
        }
        
        if (allowCredentials) {
            context.response().putHeader("Access-Control-Allow-Credentials", "true");
        }
        
        if (maxAge > 0) {
            context.response().putHeader("Access-Control-Max-Age", String.valueOf(maxAge));
        }
        
        // Manejar preflight requests (OPTIONS)
        if ("OPTIONS".equals(method)) {
            logger.debug("Handling CORS preflight request from origin: {}", origin);
            context.response()
                .setStatusCode(204)
                .end();
            return;
        }
        
        context.next();
    }
    
    private boolean isOriginAllowed(String origin) {
        return allowedOrigins.contains(origin) || allowedOrigins.contains("*");
    }
    
    /**
     * Builder para crear configuración CORS
     */
    public static class Builder {
        private Set<String> allowedOrigins = Set.of("*");
        private Set<String> allowedMethods = Set.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");
        private Set<String> allowedHeaders = Set.of(
            "Content-Type", 
            "Authorization", 
            "X-Requested-With",
            "Accept",
            "Origin"
        );
        private boolean allowCredentials = true;
        private int maxAge = 3600; // 1 hora
        
        public Builder allowedOrigins(Set<String> origins) {
            this.allowedOrigins = origins;
            return this;
        }
        
        public Builder allowedMethods(Set<String> methods) {
            this.allowedMethods = methods;
            return this;
        }
        
        public Builder allowedHeaders(Set<String> headers) {
            this.allowedHeaders = headers;
            return this;
        }
        
        public Builder allowCredentials(boolean allow) {
            this.allowCredentials = allow;
            return this;
        }
        
        public Builder maxAge(int seconds) {
            this.maxAge = seconds;
            return this;
        }
        
        public CorsMiddleware build() {
            return new CorsMiddleware(allowedOrigins, allowedMethods, allowedHeaders, 
                                    allowCredentials, maxAge);
        }
    }
    
    /**
     * Configuración CORS por defecto para desarrollo
     */
    public static CorsMiddleware development() {
        return new Builder()
            .allowedOrigins(Set.of("*"))
            .allowCredentials(false) // No permitir credenciales con wildcard origin
            .build();
    }
    
    /**
     * Configuración CORS para producción
     */
    public static CorsMiddleware production(Set<String> allowedOrigins) {
        return new Builder()
            .allowedOrigins(allowedOrigins)
            .allowCredentials(true)
            .maxAge(86400) // 24 horas
            .build();
    }
}