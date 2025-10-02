package com.auth.microservice.infrastructure.health;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Servicio centralizado para health checks del microservicio
 */
public class HealthCheckService {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckService.class);
    
    private final Vertx vertx;
    
    public HealthCheckService(Vertx vertx, Object databaseConfig, Object redisAPI) {
        this.vertx = vertx;
        logger.info("HealthCheckService initialized");
    }
    
    /**
     * Ejecuta todos los health checks
     */
    public Future<OverallHealthStatus> checkAll() {
        long startTime = System.currentTimeMillis();
        
        return checkApplication()
            .map(appResult -> {
                long totalTime = System.currentTimeMillis() - startTime;
                
                boolean isHealthy = appResult.isHealthy();
                String status = isHealthy ? "UP" : "DOWN";
                
                JsonObject details = new JsonObject()
                    .put("status", status)
                    .put("timestamp", Instant.now().toString())
                    .put("totalCheckTimeMs", totalTime)
                    .put("components", new JsonObject()
                        .put("application", appResult.getDetails()));
                
                return new OverallHealthStatus(status, isHealthy, details, totalTime);
            })
            .recover(throwable -> {
                logger.error("Error during health check", throwable);
                
                JsonObject errorDetails = new JsonObject()
                    .put("status", "DOWN")
                    .put("timestamp", Instant.now().toString())
                    .put("error", throwable.getMessage())
                    .put("errorClass", throwable.getClass().getSimpleName());
                
                return Future.succeededFuture(
                    new OverallHealthStatus("DOWN", false, errorDetails, System.currentTimeMillis() - startTime));
            });
    }
    
    /**
     * Health check de la aplicación
     */
    public Future<HealthCheckResult> checkApplication() {
        long startTime = System.currentTimeMillis();
        
        return vertx.executeBlocking(promise -> {
            try {
                // Verificar memoria JVM
                Runtime runtime = Runtime.getRuntime();
                long totalMemory = runtime.totalMemory();
                long freeMemory = runtime.freeMemory();
                long usedMemory = totalMemory - freeMemory;
                long maxMemory = runtime.maxMemory();
                
                double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
                
                // Verificar threads
                int activeThreads = Thread.activeCount();
                
                long responseTime = System.currentTimeMillis() - startTime;
                
                JsonObject details = new JsonObject()
                    .put("status", "UP")
                    .put("responseTimeMs", responseTime)
                    .put("timestamp", Instant.now().toString())
                    .put("service", "Application")
                    .put("jvm", new JsonObject()
                        .put("memoryUsedMB", usedMemory / 1024 / 1024)
                        .put("memoryTotalMB", totalMemory / 1024 / 1024)
                        .put("memoryMaxMB", maxMemory / 1024 / 1024)
                        .put("memoryUsagePercent", Math.round(memoryUsagePercent * 100.0) / 100.0)
                        .put("activeThreads", activeThreads));
                
                HealthCheckResult result = new HealthCheckResult(
                    HealthStatus.UP,
                    responseTime,
                    LocalDateTime.now(),
                    details
                );
                
                promise.complete(result);
                
            } catch (Exception e) {
                long responseTime = System.currentTimeMillis() - startTime;
                
                JsonObject details = new JsonObject()
                    .put("status", "DOWN")
                    .put("responseTimeMs", responseTime)
                    .put("timestamp", Instant.now().toString())
                    .put("service", "Application")
                    .put("error", e.getMessage())
                    .put("errorClass", e.getClass().getSimpleName());
                
                HealthCheckResult result = new HealthCheckResult(
                    HealthStatus.DOWN,
                    responseTime,
                    LocalDateTime.now(),
                    details
                );
                
                promise.complete(result);
            }
        });
    }
    
    /**
     * Health check específico para readiness probe
     */
    public Future<Boolean> isReady() {
        return Future.succeededFuture(true);
    }
    
    /**
     * Health check específico para liveness probe
     */
    public Future<Boolean> isAlive() {
        return checkApplication()
            .map(HealthCheckResult::isHealthy)
            .recover(throwable -> {
                logger.warn("Liveness check failed", throwable);
                return Future.succeededFuture(false);
            });
    }
    
    /**
     * Estados posibles del health check
     */
    public enum HealthStatus {
        UP, DOWN, UNKNOWN
    }
    
    /**
     * Estado general de salud del sistema
     */
    public static class OverallHealthStatus {
        private final String status;
        private final boolean healthy;
        private final JsonObject details;
        private final long responseTimeMs;
        
        public OverallHealthStatus(String status, boolean healthy, JsonObject details, long responseTimeMs) {
            this.status = status;
            this.healthy = healthy;
            this.details = details;
            this.responseTimeMs = responseTimeMs;
        }
        
        public String getStatus() { return status; }
        public boolean isHealthy() { return healthy; }
        public JsonObject getDetails() { return details; }
        public long getResponseTimeMs() { return responseTimeMs; }
        
        @Override
        public String toString() {
            return String.format("OverallHealthStatus{status=%s, healthy=%s, responseTime=%dms}", 
                               status, healthy, responseTimeMs);
        }
    }
    
    /**
     * Resultado de health check individual
     */
    public static class HealthCheckResult {
        private final HealthStatus status;
        private final long responseTimeMs;
        private final LocalDateTime timestamp;
        private final JsonObject details;
        
        public HealthCheckResult(HealthStatus status, long responseTimeMs, 
                               LocalDateTime timestamp, JsonObject details) {
            this.status = status;
            this.responseTimeMs = responseTimeMs;
            this.timestamp = timestamp;
            this.details = details;
        }
        
        public HealthStatus getStatus() { return status; }
        public long getResponseTimeMs() { return responseTimeMs; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public JsonObject getDetails() { return details; }
        
        public boolean isHealthy() {
            return status == HealthStatus.UP;
        }
        
        @Override
        public String toString() {
            return String.format("HealthCheckResult{status=%s, responseTime=%dms, timestamp=%s}", 
                               status, responseTimeMs, timestamp);
        }
    }
}