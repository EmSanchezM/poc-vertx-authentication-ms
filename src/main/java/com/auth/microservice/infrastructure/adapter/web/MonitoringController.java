package com.auth.microservice.infrastructure.adapter.rest;

import com.auth.microservice.infrastructure.health.HealthCheckService;
import com.auth.microservice.infrastructure.metrics.MetricsService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller para endpoints de monitoreo, métricas y health checks
 */
public class MonitoringController {
    
    private static final Logger logger = LoggerFactory.getLogger(MonitoringController.class);
    
    private final HealthCheckService healthCheckService;
    private final MetricsService metricsService;
    private final com.auth.microservice.infrastructure.config.ConfigurationFactory configFactory;
    
    public MonitoringController(HealthCheckService healthCheckService, MetricsService metricsService, 
                               com.auth.microservice.infrastructure.config.ConfigurationFactory configFactory) {
        this.healthCheckService = healthCheckService;
        this.metricsService = metricsService;
        this.configFactory = configFactory;
    }
    
    /**
     * Configura las rutas de monitoreo
     */
    public void configureRoutes(Router router) {
        // Health check endpoints
        router.get("/health").handler(this::handleHealthCheck);
        router.get("/health/ready").handler(this::handleReadinessCheck);
        router.get("/health/live").handler(this::handleLivenessCheck);
        
        // Metrics endpoints
        router.get("/metrics").handler(this::handleMetrics);
        router.get("/metrics/stats").handler(this::handleMetricsStats);
        
        // Info endpoint
        router.get("/info").handler(this::handleInfo);
        
        logger.info("Monitoring routes configured");
    }
    
    /**
     * Health check completo del sistema
     */
    private void handleHealthCheck(RoutingContext context) {
        HttpServerResponse response = context.response();
        
        healthCheckService.checkAll()
            .onSuccess(healthStatus -> {
                int statusCode = healthStatus.isHealthy() ? 200 : 503;
                
                response
                    .setStatusCode(statusCode)
                    .putHeader("Content-Type", "application/json")
                    .end(healthStatus.getDetails().encodePrettily());
                    
                if (!healthStatus.isHealthy()) {
                    logger.warn("Health check failed: {}", healthStatus.getDetails());
                }
            })
            .onFailure(throwable -> {
                logger.error("Error during health check", throwable);
                
                JsonObject errorResponse = new JsonObject()
                    .put("status", "DOWN")
                    .put("error", throwable.getMessage())
                    .put("timestamp", java.time.Instant.now().toString());
                
                response
                    .setStatusCode(503)
                    .putHeader("Content-Type", "application/json")
                    .end(errorResponse.encodePrettily());
            });
    }
    
    /**
     * Readiness probe - indica si el servicio está listo para recibir tráfico
     */
    private void handleReadinessCheck(RoutingContext context) {
        HttpServerResponse response = context.response();
        
        healthCheckService.isReady()
            .onSuccess(isReady -> {
                int statusCode = isReady ? 200 : 503;
                String status = isReady ? "UP" : "DOWN";
                
                JsonObject readinessResponse = new JsonObject()
                    .put("status", status)
                    .put("ready", isReady)
                    .put("timestamp", java.time.Instant.now().toString());
                
                response
                    .setStatusCode(statusCode)
                    .putHeader("Content-Type", "application/json")
                    .end(readinessResponse.encodePrettily());
            })
            .onFailure(throwable -> {
                logger.error("Error during readiness check", throwable);
                
                JsonObject errorResponse = new JsonObject()
                    .put("status", "DOWN")
                    .put("ready", false)
                    .put("error", throwable.getMessage())
                    .put("timestamp", java.time.Instant.now().toString());
                
                response
                    .setStatusCode(503)
                    .putHeader("Content-Type", "application/json")
                    .end(errorResponse.encodePrettily());
            });
    }
    
    /**
     * Liveness probe - indica si el servicio está vivo
     */
    private void handleLivenessCheck(RoutingContext context) {
        HttpServerResponse response = context.response();
        
        healthCheckService.isAlive()
            .onSuccess(isAlive -> {
                int statusCode = isAlive ? 200 : 503;
                String status = isAlive ? "UP" : "DOWN";
                
                JsonObject livenessResponse = new JsonObject()
                    .put("status", status)
                    .put("alive", isAlive)
                    .put("timestamp", java.time.Instant.now().toString());
                
                response
                    .setStatusCode(statusCode)
                    .putHeader("Content-Type", "application/json")
                    .end(livenessResponse.encodePrettily());
            })
            .onFailure(throwable -> {
                logger.error("Error during liveness check", throwable);
                
                JsonObject errorResponse = new JsonObject()
                    .put("status", "DOWN")
                    .put("alive", false)
                    .put("error", throwable.getMessage())
                    .put("timestamp", java.time.Instant.now().toString());
                
                response
                    .setStatusCode(503)
                    .putHeader("Content-Type", "application/json")
                    .end(errorResponse.encodePrettily());
            });
    }
    
    /**
     * Endpoint de métricas en formato Prometheus
     */
    private void handleMetrics(RoutingContext context) {
        HttpServerResponse response = context.response();
        
        try {
            String prometheusMetrics = metricsService.getPrometheusMetrics();
            
            response
                .setStatusCode(200)
                .putHeader("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
                .end(prometheusMetrics);
                
        } catch (Exception e) {
            logger.error("Error generating Prometheus metrics", e);
            
            response
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "Failed to generate metrics")
                    .put("message", e.getMessage())
                    .encodePrettily());
        }
    }
    
    /**
     * Estadísticas de métricas en formato JSON
     */
    private void handleMetricsStats(RoutingContext context) {
        HttpServerResponse response = context.response();
        
        try {
            MetricsService.MetricsStats stats = metricsService.getStats();
            
            JsonObject statsResponse = new JsonObject()
                .put("timestamp", java.time.Instant.now().toString())
                .put("authentication", new JsonObject()
                    .put("successCount", stats.getAuthSuccessCount())
                    .put("failureCount", stats.getAuthFailureCount())
                    .put("totalCount", stats.getAuthSuccessCount() + stats.getAuthFailureCount()))
                .put("sessions", new JsonObject()
                    .put("createdCount", stats.getSessionCreatedCount())
                    .put("activeCount", stats.getActiveSessionsCount()))
                .put("security", new JsonObject()
                    .put("rateLimitExceededCount", stats.getRateLimitExceededCount()))
                .put("api", new JsonObject()
                    .put("requestCount", stats.getApiRequestCount())
                    .put("errorCount", stats.getApiErrorCount()));
            
            response
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(statsResponse.encodePrettily());
                
        } catch (Exception e) {
            logger.error("Error generating metrics stats", e);
            
            response
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "Failed to generate metrics stats")
                    .put("message", e.getMessage())
                    .encodePrettily());
        }
    }
    
    /**
     * Información general del servicio
     */
    private void handleInfo(RoutingContext context) {
        HttpServerResponse response = context.response();
        
        try {
            // Usar la información de configuración del factory
            var configInfo = configFactory.getConfigurationInfo();
            
            // Información del sistema
            Runtime runtime = Runtime.getRuntime();
            
            JsonObject infoResponse = new JsonObject()
                .put("application", configInfo)
                .put("system", new JsonObject()
                    .put("javaVersion", System.getProperty("java.version"))
                    .put("javaVendor", System.getProperty("java.vendor"))
                    .put("osName", System.getProperty("os.name"))
                    .put("osVersion", System.getProperty("os.version"))
                    .put("osArch", System.getProperty("os.arch"))
                    .put("availableProcessors", runtime.availableProcessors())
                    .put("maxMemoryMB", runtime.maxMemory() / 1024 / 1024)
                    .put("totalMemoryMB", runtime.totalMemory() / 1024 / 1024)
                    .put("freeMemoryMB", runtime.freeMemory() / 1024 / 1024))
                .put("features", new JsonObject()
                    .put("authentication", true)
                    .put("authorization", true)
                    .put("rbac", true)
                    .put("jwt", true)
                    .put("rateLimiting", true)
                    .put("geolocation", true)
                    .put("metrics", true)
                    .put("healthChecks", true))
                .put("timestamp", java.time.Instant.now().toString());
            
            response
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(infoResponse.encodePrettily());
                
        } catch (Exception e) {
            logger.error("Error generating info response", e);
            
            response
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "Failed to generate info")
                    .put("message", e.getMessage())
                    .encodePrettily());
        }
    }
}