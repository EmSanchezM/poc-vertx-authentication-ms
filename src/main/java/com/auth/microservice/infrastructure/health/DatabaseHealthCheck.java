package com.auth.microservice.infrastructure.health;

import com.auth.microservice.infrastructure.config.DatabaseConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Health check para la base de datos PostgreSQL
 */
public class DatabaseHealthCheck {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseHealthCheck.class);
    
    private final DatabaseConfig databaseConfig;
    private final Vertx vertx;
    private volatile HealthStatus lastStatus;
    private volatile LocalDateTime lastCheck;
    
    public DatabaseHealthCheck(DatabaseConfig databaseConfig, Vertx vertx) {
        this.databaseConfig = databaseConfig;
        this.vertx = vertx;
        this.lastStatus = HealthStatus.UNKNOWN;
        this.lastCheck = LocalDateTime.now();
    }
    
    /**
     * Ejecuta el health check de la base de datos
     */
    public Future<HealthCheckResult> check() {
        long startTime = System.currentTimeMillis();
        
        return databaseConfig.healthCheck()
            .map(isHealthy -> {
                long responseTime = System.currentTimeMillis() - startTime;
                HealthStatus status = isHealthy ? HealthStatus.UP : HealthStatus.DOWN;
                
                this.lastStatus = status;
                this.lastCheck = LocalDateTime.now();
                
                HealthCheckResult result = new HealthCheckResult(
                    status,
                    responseTime,
                    LocalDateTime.now(),
                    createDetails(status, responseTime)
                );
                
                if (status == HealthStatus.UP) {
                    logger.debug("Database health check exitoso - Tiempo de respuesta: {}ms", responseTime);
                } else {
                    logger.warn("Database health check falló - Tiempo de respuesta: {}ms", responseTime);
                }
                
                return result;
            })
            .recover(throwable -> {
                long responseTime = System.currentTimeMillis() - startTime;
                this.lastStatus = HealthStatus.DOWN;
                this.lastCheck = LocalDateTime.now();
                
                logger.error("Error en database health check", throwable);
                
                HealthCheckResult result = new HealthCheckResult(
                    HealthStatus.DOWN,
                    responseTime,
                    LocalDateTime.now(),
                    createErrorDetails(throwable, responseTime)
                );
                
                return Future.succeededFuture(result);
            });
    }
    
    /**
     * Obtiene el último estado conocido sin ejecutar un nuevo check
     */
    public HealthCheckResult getLastKnownStatus() {
        return new HealthCheckResult(
            lastStatus,
            0L,
            lastCheck,
            createDetails(lastStatus, 0L)
        );
    }
    
    /**
     * Inicia el health check periódico
     */
    public void startPeriodicHealthCheck(int intervalMs) {
        vertx.setPeriodic(intervalMs, timerId -> {
            check().onComplete(result -> {
                if (result.succeeded()) {
                    HealthCheckResult healthResult = result.result();
                    if (healthResult.getStatus() == HealthStatus.DOWN) {
                        logger.warn("Database health check periódico falló: {}", healthResult.getDetails());
                    }
                } else {
                    logger.error("Error en health check periódico", result.cause());
                }
            });
        });
        
        logger.info("Health check periódico iniciado con intervalo de {}ms", intervalMs);
    }
    
    private JsonObject createDetails(HealthStatus status, long responseTime) {
        DatabaseConfig.PoolStatus poolStatus = databaseConfig.getPoolStatus();
        
        return new JsonObject()
            .put("status", status.name())
            .put("responseTimeMs", responseTime)
            .put("timestamp", Instant.now().toString())
            .put("database", "PostgreSQL")
            .put("poolStatus", new JsonObject()
                .put("totalConnections", poolStatus.getTotalConnections())
                .put("activeConnections", poolStatus.getActiveConnections())
                .put("idleConnections", poolStatus.getIdleConnections()));
    }
    
    private JsonObject createErrorDetails(Throwable throwable, long responseTime) {
        return new JsonObject()
            .put("status", HealthStatus.DOWN.name())
            .put("responseTimeMs", responseTime)
            .put("timestamp", Instant.now().toString())
            .put("database", "PostgreSQL")
            .put("error", throwable.getMessage())
            .put("errorClass", throwable.getClass().getSimpleName());
    }
    
    /**
     * Estados posibles del health check
     */
    public enum HealthStatus {
        UP, DOWN, UNKNOWN
    }
    
    /**
     * Resultado del health check
     */
    public static class HealthCheckResult {
        private final HealthStatus status;
        private final long responseTimeMs;
        private final LocalDateTime timestamp;
        private final JsonObject details;
        
        public HealthCheckResult(HealthStatus status, long responseTimeMs, LocalDateTime timestamp, JsonObject details) {
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