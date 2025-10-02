package com.auth.microservice.infrastructure.config;

import com.auth.microservice.domain.service.GeoLocationService;
import com.auth.microservice.infrastructure.adapter.logging.StructuredLogger;
import com.auth.microservice.infrastructure.adapter.web.middleware.LoggingMiddleware;
import io.vertx.core.Vertx;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Configuration class for the logging system.
 * Sets up structured logging with geolocation integration.
 */
public class LoggingConfig {
    
    private final StructuredLogger structuredLogger;
    private final LoggingMiddleware loggingMiddleware;
    
    public LoggingConfig(GeoLocationService geoLocationService) {
        this.structuredLogger = new StructuredLogger(geoLocationService);
        this.loggingMiddleware = new LoggingMiddleware(structuredLogger);
        
        // Initialize logging directories
        initializeLoggingDirectories();
        
        // Log system startup
        logSystemStartup();
    }
    
    /**
     * Gets the structured logger instance.
     */
    public StructuredLogger getStructuredLogger() {
        return structuredLogger;
    }
    
    /**
     * Gets the logging middleware instance.
     */
    public LoggingMiddleware getLoggingMiddleware() {
        return loggingMiddleware;
    }
    
    /**
     * Initializes logging directories if they don't exist.
     */
    private void initializeLoggingDirectories() {
        try {
            String logDir = System.getProperty("LOG_DIR", "logs");
            Path logPath = Paths.get(logDir);
            
            if (!Files.exists(logPath)) {
                Files.createDirectories(logPath);
                structuredLogger.logApplicationEvent("directory_created", 
                    "Created logging directory: " + logPath.toAbsolutePath(), 
                    Map.of("directory", logPath.toString()));
            }
        } catch (Exception e) {
            structuredLogger.logApplicationError("logging_init_error", 
                "Failed to initialize logging directories", e, null);
        }
    }
    
    /**
     * Logs system startup information.
     */
    private void logSystemStartup() {
        Map<String, String> context = Map.of(
            "java_version", System.getProperty("java.version"),
            "vertx_version", getVertxVersion(),
            "environment", System.getProperty("ENVIRONMENT", "development"),
            "hostname", System.getProperty("HOSTNAME", "localhost"),
            "log_level", System.getProperty("LOG_LEVEL", "INFO")
        );
        
        structuredLogger.logLifecycleEvent("started", "logging_system", context);
    }
    
    /**
     * Gets the Vert.x version from the system.
     */
    private String getVertxVersion() {
        try {
            return Vertx.class.getPackage().getImplementationVersion();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * Logs system shutdown.
     */
    public void logSystemShutdown() {
        Map<String, String> context = Map.of(
            "environment", System.getProperty("ENVIRONMENT", "development"),
            "hostname", System.getProperty("HOSTNAME", "localhost")
        );
        
        structuredLogger.logLifecycleEvent("stopped", "logging_system", context);
    }
}