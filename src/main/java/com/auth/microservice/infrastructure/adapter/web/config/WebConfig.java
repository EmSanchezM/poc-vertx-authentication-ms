package com.auth.microservice.infrastructure.adapter.web.config;

import com.auth.microservice.common.cqrs.QueryBus;
import com.auth.microservice.domain.service.GeoLocationService;
import com.auth.microservice.domain.service.JWTService;
import com.auth.microservice.domain.service.RateLimitService;
import com.auth.microservice.infrastructure.adapter.web.middleware.*;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.ResponseTimeHandler;
import io.vertx.ext.web.handler.TimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Configuración de la capa web y middleware
 * Centraliza la configuración de todos los middleware y handlers
 */
public class WebConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);
    
    private final Vertx vertx;
    private final JWTService jwtService;
    private final QueryBus queryBus;
    private final RateLimitService rateLimitService;
    private final GeoLocationService geoLocationService;
    
    public WebConfig(Vertx vertx, JWTService jwtService, QueryBus queryBus, 
                    RateLimitService rateLimitService, GeoLocationService geoLocationService) {
        this.vertx = vertx;
        this.jwtService = jwtService;
        this.queryBus = queryBus;
        this.rateLimitService = rateLimitService;
        this.geoLocationService = geoLocationService;
    }
    
    /**
     * Configura el router principal con todos los middleware globales
     */
    public Router configureMainRouter() {
        Router router = Router.router(vertx);
        
        // Middleware globales (orden importa)
        configureGlobalMiddleware(router);
        
        logger.info("Web configuration completed successfully");
        return router;
    }
    
    /**
     * Configura middleware globales que se aplican a todas las rutas
     */
    private void configureGlobalMiddleware(Router router) {
        // 1. Response time tracking
        router.route().handler(ResponseTimeHandler.create());
        
        // 2. Request timeout (30 segundos)
        router.route().handler(TimeoutHandler.create(30000));
        
        // 3. Request logging
        router.route().handler(LoggerHandler.create());
        
        // 4. CORS handling (debe ir antes que otros handlers)
        router.route().handler(createCorsMiddleware());
        
        // 5. Body parsing
        router.route().handler(BodyHandler.create()
            .setBodyLimit(1024 * 1024) // 1MB limit
            .setMergeFormAttributes(true)
            .setDeleteUploadedFilesOnEnd(true));
        
        // 6. Security logging con geolocalización
        router.route().handler(new SecurityLoggingMiddleware(geoLocationService));
        
        // 7. Error handling global
        router.route().failureHandler(new ErrorHandlerMiddleware());
        
        logger.info("Global middleware configured successfully");
    }
    
    /**
     * Crea middleware de autenticación JWT
     */
    public AuthenticationMiddleware createAuthenticationMiddleware() {
        return new AuthenticationMiddleware(jwtService);
    }
    
    /**
     * Crea middleware de autorización para un permiso específico
     */
    public AuthorizationMiddleware createAuthorizationMiddleware(String permission) {
        return AuthorizationMiddleware.requirePermission(queryBus, permission);
    }
    
    /**
     * Crea middleware de autorización para resource y action
     */
    public AuthorizationMiddleware createAuthorizationMiddleware(String resource, String action) {
        return AuthorizationMiddleware.requirePermission(queryBus, resource, action);
    }
    
    /**
     * Crea middleware de rate limiting para login
     */
    public RateLimitMiddleware createLoginRateLimitMiddleware() {
        return RateLimitMiddleware.forLogin(rateLimitService);
    }
    
    /**
     * Crea middleware de rate limiting para registro
     */
    public RateLimitMiddleware createRegistrationRateLimitMiddleware() {
        return RateLimitMiddleware.forRegistration(rateLimitService);
    }
    
    /**
     * Crea middleware de rate limiting general
     */
    public RateLimitMiddleware createGeneralRateLimitMiddleware(String endpoint) {
        return RateLimitMiddleware.forGeneral(rateLimitService, endpoint);
    }
    
    /**
     * Crea middleware de rate limiting para endpoints administrativos
     */
    public RateLimitMiddleware createAdminRateLimitMiddleware(String endpoint) {
        return RateLimitMiddleware.forAdmin(rateLimitService, endpoint);
    }
    
    /**
     * Configura CORS basado en el entorno
     */
    private CorsMiddleware createCorsMiddleware() {
        String environment = System.getenv().getOrDefault("ENVIRONMENT", "development");
        
        if ("production".equalsIgnoreCase(environment)) {
            // En producción, usar orígenes específicos
            String allowedOriginsEnv = System.getenv().getOrDefault("ALLOWED_ORIGINS", "");
            Set<String> allowedOrigins = Set.of(allowedOriginsEnv.split(","));
            
            if (allowedOrigins.isEmpty() || allowedOrigins.contains("")) {
                logger.warn("No ALLOWED_ORIGINS configured for production, using restrictive defaults");
                allowedOrigins = Set.of("https://localhost:3000", "https://127.0.0.1:3000");
            }
            
            return CorsMiddleware.production(allowedOrigins);
        } else {
            // En desarrollo, permitir todos los orígenes
            return CorsMiddleware.development();
        }
    }
    
    /**
     * Configura rutas de salud y métricas
     */
    public void configureHealthRoutes(Router router) {
        // Health check básico
        router.get("/health").handler(ctx -> {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"status\":\"UP\",\"service\":\"auth-microservice\",\"timestamp\":\"" + 
                     java.time.Instant.now().toString() + "\"}");
        });
        
        // Health check detallado
        router.get("/health/detailed").handler(ctx -> {
            // TODO: Implementar checks de base de datos, Redis, etc.
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"status\":\"UP\",\"service\":\"auth-microservice\",\"checks\":{\"database\":\"UP\",\"redis\":\"UP\"}}");
        });
        
        // Endpoint de métricas (sin autenticación para Prometheus)
        router.get("/metrics").handler(ctx -> {
            // TODO: Integrar con Micrometer/Prometheus
            ctx.response()
                .putHeader("Content-Type", "text/plain")
                .end("# Metrics endpoint - TODO: implement Prometheus metrics");
        });
        
        logger.info("Health and metrics routes configured");
    }
    
    /**
     * Configura handlers para errores específicos
     */
    public void configureErrorHandlers(Router router) {
        // 404 - Not Found
        router.route().last().handler(ErrorHandlerMiddleware.notFoundHandler());
        
        logger.info("Error handlers configured");
    }
    
    /**
     * Configuración de seguridad adicional
     */
    public void configureSecurityHeaders(Router router) {
        router.route().handler(ctx -> {
            // Security headers
            ctx.response()
                .putHeader("X-Content-Type-Options", "nosniff")
                .putHeader("X-Frame-Options", "DENY")
                .putHeader("X-XSS-Protection", "1; mode=block")
                .putHeader("Referrer-Policy", "strict-origin-when-cross-origin")
                .putHeader("Content-Security-Policy", "default-src 'self'");
            
            // Remove server header for security
            ctx.response().headers().remove("Server");
            
            ctx.next();
        });
        
        logger.info("Security headers configured");
    }
    
    /**
     * Configura las rutas de autenticación con middleware apropiado
     */
    public void configureAuthRoutes(Router router, com.auth.microservice.infrastructure.adapter.web.AuthController authController) {
        // Subrouter para rutas de autenticación
        Router authRouter = Router.router(vertx);
        
        // POST /auth/login - con rate limiting específico para login
        authRouter.post("/login")
            .handler(createLoginRateLimitMiddleware())
            .handler(authController::login);
        
        // POST /auth/register - con rate limiting específico para registro
        authRouter.post("/register")
            .handler(createRegistrationRateLimitMiddleware())
            .handler(authController::register);
        
        // POST /auth/refresh - con rate limiting general
        authRouter.post("/refresh")
            .handler(createGeneralRateLimitMiddleware("refresh"))
            .handler(authController::refresh);
        
        // POST /auth/logout - requiere autenticación
        authRouter.post("/logout")
            .handler(createAuthenticationMiddleware())
            .handler(createGeneralRateLimitMiddleware("logout"))
            .handler(authController::logout);
        
        // Montar el subrouter en /auth
        router.mountSubRouter("/auth", authRouter);
        
        logger.info("Authentication routes configured with appropriate middleware");
    }
}