package com.auth.microservice.infrastructure.adapter.web.config;

import com.auth.microservice.common.cqrs.CommandBus;
import com.auth.microservice.common.cqrs.QueryBus;
import com.auth.microservice.domain.service.GeoLocationService;
import com.auth.microservice.domain.service.JWTService;
import com.auth.microservice.domain.service.RateLimitService;
import com.auth.microservice.infrastructure.adapter.web.middleware.*;
import com.auth.microservice.infrastructure.metrics.MetricsService;
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
 * Configuración centralizada del router web y middleware.
 * Responsable únicamente de la configuración de rutas, middleware y handlers HTTP.
 * Separada de la lógica de inicialización de componentes.
 */
public class WebRouterConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(WebRouterConfiguration.class);
    
    private final Vertx vertx;
    private final JWTService jwtService;
    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final RateLimitService rateLimitService;
    private final GeoLocationService geoLocationService;
    private final MetricsService metricsService;
    
    public WebRouterConfiguration(Vertx vertx, JWTService jwtService, CommandBus commandBus, QueryBus queryBus, 
                                 RateLimitService rateLimitService, GeoLocationService geoLocationService,
                                 MetricsService metricsService) {
        this.vertx = vertx;
        this.jwtService = jwtService;
        this.commandBus = commandBus;
        this.queryBus = queryBus;
        this.rateLimitService = rateLimitService;
        this.geoLocationService = geoLocationService;
        this.metricsService = metricsService;
    }
    
    /**
     * Configura el router principal con todos los middleware globales.
     * Los controladores deben ser configurados por separado usando configureControllerRoutes().
     */
    public Router configureMainRouter() {
        Router router = Router.router(vertx);
        
        // Middleware globales (orden importa)
        configureGlobalMiddleware(router);
        
        // Configurar headers de seguridad
        configureSecurityHeaders(router);
        
        // Configurar rutas básicas de salud y métricas
        configureBasicHealthRoutes(router);
        
        // Configurar manejo de errores (debe ir al final)
        configureErrorHandlers(router);
        
        logger.info("Web router configuration completed successfully");
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
        
        // 6. Metrics collection (debe ir antes del security logging)
        router.route().handler(new MetricsMiddleware(metricsService));
        
        // 7. Security logging con geolocalización
        router.route().handler(new SecurityLoggingMiddleware(geoLocationService));
        
        // 8. Error handling global
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
     * Configura rutas básicas de salud (las rutas completas de monitoreo se configuran en MonitoringController)
     */
    private void configureBasicHealthRoutes(Router router) {
        // Endpoint raíz básico
        router.get("/").handler(ctx -> {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"message\":\"Auth Microservice is running\",\"status\":\"UP\",\"timestamp\":\"" + 
                     java.time.Instant.now().toString() + "\"}");
        });
        
        logger.info("Basic health routes configured");
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
     * Configura controladores específicos en el router proporcionado.
     * Los controladores deben ser creados e inyectados desde ApplicationBootstrap.
     */
    public void configureControllerRoutes(Router router, 
                                         com.auth.microservice.infrastructure.adapter.web.AuthController authController,
                                         com.auth.microservice.infrastructure.adapter.web.UserController userController,
                                         com.auth.microservice.infrastructure.adapter.rest.MonitoringController monitoringController,
                                         com.auth.microservice.infrastructure.adapter.web.DebugController debugController) {
        
        // Configurar rutas de autenticación (públicas)
        authController.configureRoutes(router);
        
        // Configurar rutas protegidas de usuarios
        Router protectedRouter = Router.router(vertx);
        protectedRouter.route().handler(createAuthenticationMiddleware());
        protectedRouter.route().handler(createAuthorizationMiddleware("users:read"));
        
        userController.configureRoutes(protectedRouter);
        router.mountSubRouter("/api", protectedRouter);
        
        // Configurar rutas de monitoreo
        monitoringController.configureRoutes(router);
        
        // Configurar rutas de debug (solo en desarrollo o con autenticación admin)
        configureDebugRoutes(router, debugController);
        
        logger.info("All controller routes configured successfully");
    }
    
    /**
     * Configura rutas de debug con seguridad apropiada
     */
    private void configureDebugRoutes(Router router, com.auth.microservice.infrastructure.adapter.web.DebugController debugController) {
        String environment = System.getenv().getOrDefault("APP_ENV", "development");
        
        if ("development".equalsIgnoreCase(environment)) {
            // En desarrollo, permitir acceso directo a debug endpoints
            debugController.configureRoutes(router);
            logger.info("Debug routes configured for development environment");
        } else {
            // En producción, requerir autenticación admin
            Router debugRouter = Router.router(vertx);
            debugRouter.route().handler(createAuthenticationMiddleware());
            debugRouter.route().handler(createAuthorizationMiddleware("admin:debug"));
            
            debugController.configureRoutes(debugRouter);
            router.mountSubRouter("", debugRouter);
            
            logger.info("Debug routes configured with admin authentication for {} environment", environment);
        }
    }
    

}