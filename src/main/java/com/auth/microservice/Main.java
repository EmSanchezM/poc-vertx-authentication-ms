package com.auth.microservice;

import com.auth.microservice.infrastructure.health.HealthCheckService;
import com.auth.microservice.infrastructure.metrics.MetricsService;
import com.auth.microservice.infrastructure.adapter.rest.MonitoringController;
import com.auth.microservice.infrastructure.adapter.rest.middleware.MetricsMiddleware;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        
        try {
            // Initialize monitoring services
            MetricsService metricsService = new MetricsService(vertx);
            HealthCheckService healthCheckService = createMockHealthCheckService(vertx);
            MonitoringController monitoringController = new MonitoringController(healthCheckService, metricsService, vertx);
            
            // Create HTTP server
            HttpServer server = vertx.createHttpServer();
            
            // Create router
            Router router = Router.router(vertx);
            router.route().handler(BodyHandler.create());
            
            // Add metrics middleware to all routes
            MetricsMiddleware metricsMiddleware = new MetricsMiddleware(metricsService);
            router.route().handler(metricsMiddleware);
            
            // Configure monitoring routes
            monitoringController.configureRoutes(router);
            
            // Root endpoint
            router.get("/").handler(ctx -> {
                ctx.response()
                    .putHeader("content-type", "application/json")
                    .end("{\"message\":\"Auth Microservice is running\",\"version\":\"1.0.0\"}");
            });
            
            // Test endpoints to generate metrics
            router.post("/auth/login").handler(ctx -> {
                // Simulate authentication
                String country = ctx.request().getHeader("X-Country");
                if (country == null) country = "Unknown";
                
                // Simulate success/failure
                boolean success = Math.random() > 0.3; // 70% success rate
                
                if (success) {
                    metricsService.recordAuthenticationSuccess(country);
                    metricsService.recordSessionCreated();
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("content-type", "application/json")
                        .end("{\"status\":\"success\",\"token\":\"mock-jwt-token\"}");
                } else {
                    metricsService.recordAuthenticationFailure(country);
                    ctx.response()
                        .setStatusCode(401)
                        .putHeader("content-type", "application/json")
                        .end("{\"status\":\"error\",\"message\":\"Invalid credentials\"}");
                }
            });
            
            router.post("/auth/logout").handler(ctx -> {
                metricsService.recordSessionInvalidated();
                ctx.response()
                    .setStatusCode(200)
                    .putHeader("content-type", "application/json")
                    .end("{\"status\":\"success\",\"message\":\"Logged out\"}");
            });
            
            // Test endpoint for rate limiting
            router.get("/test/rate-limit").handler(ctx -> {
                metricsService.recordRateLimitExceeded();
                ctx.response()
                    .setStatusCode(429)
                    .putHeader("content-type", "application/json")
                    .end("{\"status\":\"error\",\"message\":\"Rate limit exceeded\"}");
            });
            
            // Test endpoint for suspicious activity
            router.get("/test/suspicious").handler(ctx -> {
                String country = ctx.request().getParam("country");
                if (country == null) country = "Unknown";
                metricsService.recordSuspiciousActivity(country);
                ctx.response()
                    .setStatusCode(200)
                    .putHeader("content-type", "application/json")
                    .end("{\"status\":\"logged\",\"message\":\"Suspicious activity recorded\"}");
            });
            
            // Start server
            int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
            
            server.requestHandler(router).listen(port, result -> {
                if (result.succeeded()) {
                    logger.info("Auth Microservice started on port {} with monitoring enabled", port);
                    logger.info("Monitoring endpoints:");
                    logger.info("  - Health: http://localhost:{}/health", port);
                    logger.info("  - Metrics: http://localhost:{}/metrics", port);
                    logger.info("  - Info: http://localhost:{}/info", port);
                    logger.info("External monitoring:");
                    logger.info("  - Prometheus: http://localhost:9090");
                    logger.info("  - Grafana: http://localhost:3000 (admin/admin)");
                    logger.info("  - AlertManager: http://localhost:9093");
                } else {
                    logger.error("Failed to start server: {}", result.cause().getMessage());
                    vertx.close();
                }
            });
            
        } catch (Exception e) {
            logger.error("Failed to initialize application", e);
            vertx.close();
        }
    }
    
    /**
     * Creates a mock health check service for demonstration purposes
     */
    private static HealthCheckService createMockHealthCheckService(Vertx vertx) {
        return new HealthCheckService(vertx, null, null) {
            @Override
            public io.vertx.core.Future<OverallHealthStatus> checkAll() {
                io.vertx.core.json.JsonObject details = new io.vertx.core.json.JsonObject()
                    .put("status", "UP")
                    .put("timestamp", java.time.Instant.now().toString())
                    .put("components", new io.vertx.core.json.JsonObject()
                        .put("application", new io.vertx.core.json.JsonObject()
                            .put("status", "UP")
                            .put("responseTimeMs", 5))
                        .put("jvm", new io.vertx.core.json.JsonObject()
                            .put("status", "UP")
                            .put("memoryUsagePercent", 45.2)
                            .put("activeThreads", Thread.activeCount())));
                
                return io.vertx.core.Future.succeededFuture(
                    new OverallHealthStatus("UP", true, details, 10));
            }
            
            @Override
            public io.vertx.core.Future<Boolean> isReady() {
                return io.vertx.core.Future.succeededFuture(true);
            }
            
            @Override
            public io.vertx.core.Future<Boolean> isAlive() {
                return io.vertx.core.Future.succeededFuture(true);
            }
        };
    }
}