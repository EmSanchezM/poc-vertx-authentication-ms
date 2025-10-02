package com.auth.microservice.infrastructure.adapter.logging;

import com.auth.microservice.domain.service.GeoLocationService;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Structured logger that provides consistent logging format across the application.
 * Integrates with GeoLocationService for enhanced security logging.
 */
public class StructuredLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(StructuredLogger.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    private final SecurityLogger securityLogger;
    private final AuditLogger auditLogger;
    private final GeoLocationService geoLocationService;
    
    public StructuredLogger(GeoLocationService geoLocationService) {
        this.geoLocationService = geoLocationService;
        this.securityLogger = new SecurityLogger(geoLocationService);
        this.auditLogger = new AuditLogger(geoLocationService);
    }
    
    /**
     * Gets the security logger instance.
     */
    public SecurityLogger security() {
        return securityLogger;
    }
    
    /**
     * Gets the audit logger instance.
     */
    public AuditLogger audit() {
        return auditLogger;
    }
    
    /**
     * Logs application events with structured format.
     */
    public void logApplicationEvent(String eventType, String message, Map<String, String> context) {
        try {
            MDC.put("event_type", eventType);
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            if (context != null) {
                context.forEach(MDC::put);
            }
            
            logger.info(message);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Logs application errors with structured format.
     */
    public void logApplicationError(String errorType, String message, Throwable throwable, Map<String, String> context) {
        try {
            MDC.put("error_type", errorType);
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            if (throwable != null) {
                MDC.put("exception_class", throwable.getClass().getSimpleName());
                MDC.put("exception_message", throwable.getMessage());
            }
            
            if (context != null) {
                context.forEach(MDC::put);
            }
            
            if (throwable != null) {
                logger.error(message, throwable);
            } else {
                logger.error(message);
            }
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Logs performance metrics with structured format.
     */
    public void logPerformanceMetric(String operation, long durationMs, Map<String, String> context) {
        try {
            MDC.put("event_type", "performance_metric");
            MDC.put("operation", operation);
            MDC.put("duration_ms", String.valueOf(durationMs));
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            if (context != null) {
                context.forEach(MDC::put);
            }
            
            logger.info("Operation '{}' completed in {}ms", operation, durationMs);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Logs API requests with geolocation information.
     */
    public Future<Void> logApiRequest(String method, String path, String ipAddress, String userAgent, 
                                     String userId, int statusCode, long durationMs) {
        return geoLocationService.getCountryByIp(ipAddress)
            .onSuccess(country -> {
                try {
                    MDC.put("event_type", "api_request");
                    MDC.put("http_method", method);
                    MDC.put("http_path", path);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("user_agent", userAgent);
                    MDC.put("user_id", userId != null ? userId : "anonymous");
                    MDC.put("status_code", String.valueOf(statusCode));
                    MDC.put("duration_ms", String.valueOf(durationMs));
                    MDC.put("country", country);
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    logger.info("{} {} from {} ({}) - {} in {}ms", 
                        method, path, ipAddress, country, statusCode, durationMs);
                } finally {
                    MDC.clear();
                }
            })
            .onFailure(throwable -> {
                try {
                    MDC.put("event_type", "api_request");
                    MDC.put("http_method", method);
                    MDC.put("http_path", path);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("user_agent", userAgent);
                    MDC.put("user_id", userId != null ? userId : "anonymous");
                    MDC.put("status_code", String.valueOf(statusCode));
                    MDC.put("duration_ms", String.valueOf(durationMs));
                    MDC.put("country", "UNKNOWN");
                    MDC.put("geo_error", throwable.getMessage());
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    logger.info("{} {} from {} (country lookup failed) - {} in {}ms", 
                        method, path, ipAddress, statusCode, durationMs);
                } finally {
                    MDC.clear();
                }
            })
            .mapEmpty();
    }
    
    /**
     * Logs database operations with performance metrics.
     */
    public void logDatabaseOperation(String operation, String table, long durationMs, int recordCount, 
                                   Map<String, String> context) {
        try {
            MDC.put("event_type", "database_operation");
            MDC.put("db_operation", operation);
            MDC.put("db_table", table);
            MDC.put("duration_ms", String.valueOf(durationMs));
            MDC.put("record_count", String.valueOf(recordCount));
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            if (context != null) {
                context.forEach(MDC::put);
            }
            
            logger.debug("Database {} on {} completed in {}ms (records: {})", 
                operation, table, durationMs, recordCount);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Logs cache operations with hit/miss information.
     */
    public void logCacheOperation(String operation, String key, boolean hit, long durationMs) {
        try {
            MDC.put("event_type", "cache_operation");
            MDC.put("cache_operation", operation);
            MDC.put("cache_key", key);
            MDC.put("cache_hit", String.valueOf(hit));
            MDC.put("duration_ms", String.valueOf(durationMs));
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            logger.debug("Cache {} for key '{}': {} in {}ms", 
                operation, key, hit ? "HIT" : "MISS", durationMs);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Logs external service calls with response information.
     */
    public void logExternalServiceCall(String service, String operation, int statusCode, long durationMs, 
                                     Map<String, String> context) {
        try {
            MDC.put("event_type", "external_service_call");
            MDC.put("external_service", service);
            MDC.put("service_operation", operation);
            MDC.put("status_code", String.valueOf(statusCode));
            MDC.put("duration_ms", String.valueOf(durationMs));
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            if (context != null) {
                context.forEach(MDC::put);
            }
            
            logger.info("External service call to {} ({}) - {} in {}ms", 
                service, operation, statusCode, durationMs);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Logs startup and shutdown events.
     */
    public void logLifecycleEvent(String event, String component, Map<String, String> context) {
        try {
            MDC.put("event_type", "lifecycle_event");
            MDC.put("lifecycle_event", event);
            MDC.put("component", component);
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            MDC.put("event_id", UUID.randomUUID().toString());
            
            if (context != null) {
                context.forEach(MDC::put);
            }
            
            logger.info("Component '{}' {}", component, event);
        } finally {
            MDC.clear();
        }
    }
}