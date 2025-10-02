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
 * Security-focused logger that includes geolocation information.
 * Logs authentication events, authorization failures, and security incidents.
 */
public class SecurityLogger {
    
    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("SECURITY");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    private final GeoLocationService geoLocationService;
    
    public SecurityLogger(GeoLocationService geoLocationService) {
        this.geoLocationService = geoLocationService;
    }
    
    /**
     * Logs a successful authentication event with geolocation.
     */
    public Future<Void> logSuccessfulAuthentication(String userId, String email, String ipAddress, 
                                                   String userAgent, String sessionId) {
        return geoLocationService.getCountryByIp(ipAddress)
            .onSuccess(country -> {
                try {
                    MDC.put("event_type", "authentication_success");
                    MDC.put("user_id", userId);
                    MDC.put("email", email);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("user_agent", userAgent);
                    MDC.put("session_id", sessionId);
                    MDC.put("country", country);
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    SECURITY_LOG.info("User {} successfully authenticated from {} ({})", 
                        email, ipAddress, country);
                } finally {
                    MDC.clear();
                }
            })
            .onFailure(throwable -> {
                try {
                    MDC.put("event_type", "authentication_success");
                    MDC.put("user_id", userId);
                    MDC.put("email", email);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("user_agent", userAgent);
                    MDC.put("session_id", sessionId);
                    MDC.put("country", "UNKNOWN");
                    MDC.put("geo_error", throwable.getMessage());
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    SECURITY_LOG.info("User {} successfully authenticated from {} (country lookup failed)", 
                        email, ipAddress);
                } finally {
                    MDC.clear();
                }
            })
            .mapEmpty();
    }
    
    /**
     * Logs a failed authentication attempt with geolocation.
     */
    public Future<Void> logFailedAuthentication(String email, String ipAddress, String userAgent, 
                                              String reason, int attemptCount) {
        return geoLocationService.getCountryByIp(ipAddress)
            .onSuccess(country -> {
                try {
                    MDC.put("event_type", "authentication_failure");
                    MDC.put("email", email);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("user_agent", userAgent);
                    MDC.put("failure_reason", reason);
                    MDC.put("attempt_count", String.valueOf(attemptCount));
                    MDC.put("country", country);
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    SECURITY_LOG.warn("Authentication failed for {} from {} ({}): {} (attempt {})", 
                        email, ipAddress, country, reason, attemptCount);
                } finally {
                    MDC.clear();
                }
            })
            .onFailure(throwable -> {
                try {
                    MDC.put("event_type", "authentication_failure");
                    MDC.put("email", email);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("user_agent", userAgent);
                    MDC.put("failure_reason", reason);
                    MDC.put("attempt_count", String.valueOf(attemptCount));
                    MDC.put("country", "UNKNOWN");
                    MDC.put("geo_error", throwable.getMessage());
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    SECURITY_LOG.warn("Authentication failed for {} from {} (country lookup failed): {} (attempt {})", 
                        email, ipAddress, reason, attemptCount);
                } finally {
                    MDC.clear();
                }
            })
            .mapEmpty();
    }
    
    /**
     * Logs authorization failures with geolocation.
     */
    public Future<Void> logAuthorizationFailure(String userId, String email, String ipAddress, 
                                               String resource, String action, String requiredPermission) {
        return geoLocationService.getCountryByIp(ipAddress)
            .onSuccess(country -> {
                try {
                    MDC.put("event_type", "authorization_failure");
                    MDC.put("user_id", userId);
                    MDC.put("email", email);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("resource", resource);
                    MDC.put("action", action);
                    MDC.put("required_permission", requiredPermission);
                    MDC.put("country", country);
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    SECURITY_LOG.warn("Authorization failed for user {} from {} ({}): missing permission {} for {}.{}", 
                        email, ipAddress, country, requiredPermission, resource, action);
                } finally {
                    MDC.clear();
                }
            })
            .onFailure(throwable -> {
                try {
                    MDC.put("event_type", "authorization_failure");
                    MDC.put("user_id", userId);
                    MDC.put("email", email);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("resource", resource);
                    MDC.put("action", action);
                    MDC.put("required_permission", requiredPermission);
                    MDC.put("country", "UNKNOWN");
                    MDC.put("geo_error", throwable.getMessage());
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    SECURITY_LOG.warn("Authorization failed for user {} from {} (country lookup failed): missing permission {} for {}.{}", 
                        email, ipAddress, requiredPermission, resource, action);
                } finally {
                    MDC.clear();
                }
            })
            .mapEmpty();
    }
    
    /**
     * Logs suspicious activity with geolocation.
     */
    public Future<Void> logSuspiciousActivity(String userId, String email, String ipAddress, 
                                             String activityType, String description, Map<String, String> metadata) {
        return geoLocationService.getCountryByIp(ipAddress)
            .onSuccess(country -> {
                try {
                    MDC.put("event_type", "suspicious_activity");
                    MDC.put("user_id", userId);
                    MDC.put("email", email);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("activity_type", activityType);
                    MDC.put("description", description);
                    MDC.put("country", country);
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    // Add metadata to MDC
                    if (metadata != null) {
                        metadata.forEach(MDC::put);
                    }
                    
                    SECURITY_LOG.error("Suspicious activity detected for user {} from {} ({}): {} - {}", 
                        email, ipAddress, country, activityType, description);
                } finally {
                    MDC.clear();
                }
            })
            .onFailure(throwable -> {
                try {
                    MDC.put("event_type", "suspicious_activity");
                    MDC.put("user_id", userId);
                    MDC.put("email", email);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("activity_type", activityType);
                    MDC.put("description", description);
                    MDC.put("country", "UNKNOWN");
                    MDC.put("geo_error", throwable.getMessage());
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    // Add metadata to MDC
                    if (metadata != null) {
                        metadata.forEach(MDC::put);
                    }
                    
                    SECURITY_LOG.error("Suspicious activity detected for user {} from {} (country lookup failed): {} - {}", 
                        email, ipAddress, activityType, description);
                } finally {
                    MDC.clear();
                }
            })
            .mapEmpty();
    }
    
    /**
     * Logs rate limiting events with geolocation.
     */
    public Future<Void> logRateLimitExceeded(String identifier, String ipAddress, String endpoint, 
                                            int attemptCount, long blockDurationMs) {
        return geoLocationService.getCountryByIp(ipAddress)
            .onSuccess(country -> {
                try {
                    MDC.put("event_type", "rate_limit_exceeded");
                    MDC.put("identifier", identifier);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("endpoint", endpoint);
                    MDC.put("attempt_count", String.valueOf(attemptCount));
                    MDC.put("block_duration_ms", String.valueOf(blockDurationMs));
                    MDC.put("country", country);
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    SECURITY_LOG.warn("Rate limit exceeded for {} from {} ({}) on endpoint {}: {} attempts, blocked for {}ms", 
                        identifier, ipAddress, country, endpoint, attemptCount, blockDurationMs);
                } finally {
                    MDC.clear();
                }
            })
            .onFailure(throwable -> {
                try {
                    MDC.put("event_type", "rate_limit_exceeded");
                    MDC.put("identifier", identifier);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("endpoint", endpoint);
                    MDC.put("attempt_count", String.valueOf(attemptCount));
                    MDC.put("block_duration_ms", String.valueOf(blockDurationMs));
                    MDC.put("country", "UNKNOWN");
                    MDC.put("geo_error", throwable.getMessage());
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    SECURITY_LOG.warn("Rate limit exceeded for {} from {} (country lookup failed) on endpoint {}: {} attempts, blocked for {}ms", 
                        identifier, ipAddress, endpoint, attemptCount, blockDurationMs);
                } finally {
                    MDC.clear();
                }
            })
            .mapEmpty();
    }
    
    /**
     * Logs session events with geolocation.
     */
    public Future<Void> logSessionEvent(String eventType, String userId, String email, String sessionId, 
                                       String ipAddress, String userAgent, String reason) {
        return geoLocationService.getCountryByIp(ipAddress)
            .onSuccess(country -> {
                try {
                    MDC.put("event_type", "session_" + eventType);
                    MDC.put("user_id", userId);
                    MDC.put("email", email);
                    MDC.put("session_id", sessionId);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("user_agent", userAgent);
                    MDC.put("country", country);
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    if (reason != null) {
                        MDC.put("reason", reason);
                    }
                    
                    SECURITY_LOG.info("Session {} for user {} from {} ({}): {}", 
                        eventType, email, ipAddress, country, reason != null ? reason : "normal operation");
                } finally {
                    MDC.clear();
                }
            })
            .onFailure(throwable -> {
                try {
                    MDC.put("event_type", "session_" + eventType);
                    MDC.put("user_id", userId);
                    MDC.put("email", email);
                    MDC.put("session_id", sessionId);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("user_agent", userAgent);
                    MDC.put("country", "UNKNOWN");
                    MDC.put("geo_error", throwable.getMessage());
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    if (reason != null) {
                        MDC.put("reason", reason);
                    }
                    
                    SECURITY_LOG.info("Session {} for user {} from {} (country lookup failed): {}", 
                        eventType, email, ipAddress, reason != null ? reason : "normal operation");
                } finally {
                    MDC.clear();
                }
            })
            .mapEmpty();
    }
}