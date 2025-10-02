package com.auth.microservice.infrastructure.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servicio centralizado para métricas de Prometheus usando Micrometer
 */
public class MetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);
    
    private final PrometheusMeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Counter> counters;
    private final AtomicLong activeSessionsGauge;
    
    public MetricsService(Vertx vertx) {
        this.meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        this.counters = new ConcurrentHashMap<>();
        this.activeSessionsGauge = new AtomicLong(0);
        
        // Initialize active sessions gauge
        Gauge.builder("auth_session_active", activeSessionsGauge, AtomicLong::doubleValue)
            .description("Number of active sessions")
            .register(meterRegistry);
        
        logger.info("MetricsService initialized with Prometheus registry");
    }
    
    // Authentication metrics methods
    public void recordAuthenticationSuccess() {
        getOrCreateCounter("auth_authentication_success_total", "Total successful authentications").increment();
    }
    
    public void recordAuthenticationFailure() {
        getOrCreateCounter("auth_authentication_failure_total", "Total failed authentications").increment();
    }
    
    public void recordAuthenticationSuccess(String country) {
        recordAuthenticationSuccess();
        getOrCreateCounterWithTags("auth_authentication_by_country_total", "Authentication attempts by country", 
            "country", country, "result", "success").increment();
    }
    
    public void recordAuthenticationFailure(String country) {
        recordAuthenticationFailure();
        getOrCreateCounterWithTags("auth_authentication_by_country_total", "Authentication attempts by country", 
            "country", country, "result", "failure").increment();
    }
    
    public Timer.Sample startAuthenticationTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void recordAuthenticationDuration(Timer.Sample sample) {
        sample.stop(getOrCreateTimer("auth_authentication_duration_seconds", "Authentication duration in seconds"));
    }
    
    // Authorization metrics methods
    public void recordAuthorizationSuccess() {
        getOrCreateCounter("auth_authorization_success_total", "Total successful authorizations").increment();
    }
    
    public void recordAuthorizationFailure() {
        getOrCreateCounter("auth_authorization_failure_total", "Total failed authorizations").increment();
    }
    
    // Session metrics methods
    public void recordSessionCreated() {
        getOrCreateCounter("auth_session_created_total", "Total sessions created").increment();
        activeSessionsGauge.incrementAndGet();
    }
    
    public void recordSessionInvalidated() {
        getOrCreateCounter("auth_session_invalidated_total", "Total sessions invalidated").increment();
        activeSessionsGauge.decrementAndGet();
    }
    
    public void setActiveSessions(long count) {
        activeSessionsGauge.set(count);
    }
    
    // Security metrics methods
    public void recordRateLimitExceeded() {
        getOrCreateCounter("auth_ratelimit_exceeded_total", "Total rate limit violations").increment();
    }
    
    public void recordSuspiciousActivity(String country) {
        getOrCreateCounterWithTags("auth_security_suspicious_activity_total", "Total suspicious security activities", 
            "country", country).increment();
    }
    
    // API metrics methods
    public void recordApiRequest(String method, String endpoint) {
        getOrCreateCounterWithTags("auth_api_request_total", "Total API requests", 
            "method", method, "endpoint", endpoint).increment();
    }
    
    public Timer.Sample startApiTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void recordApiResponse(Timer.Sample sample, String method, String endpoint, int statusCode) {
        sample.stop(getOrCreateTimerWithTags("auth_api_request_duration_seconds", "API request duration in seconds",
            "method", method, "endpoint", endpoint, "status", String.valueOf(statusCode)));
    }
    
    public void recordApiError(String method, String endpoint, String errorType) {
        getOrCreateCounterWithTags("auth_api_error_total", "Total API errors",
            "method", method, "endpoint", endpoint, "error_type", errorType).increment();
    }
    
    // Cache metrics methods
    public void recordCacheHit() {
        getOrCreateCounter("auth_cache_hit_total", "Total cache hits").increment();
    }
    
    public void recordCacheMiss() {
        getOrCreateCounter("auth_cache_miss_total", "Total cache misses").increment();
    }
    
    // Database metrics methods
    public Timer.Sample startDatabaseTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void recordDatabaseQuery(Timer.Sample sample, String operation) {
        sample.stop(getOrCreateTimerWithTags("auth_database_query_duration_seconds", "Database query duration in seconds",
            "operation", operation));
    }
    
    public void recordDatabaseConnection() {
        getOrCreateCounter("auth_database_connection_total", "Total database connections").increment();
    }
    
    // Helper methods
    private Counter getOrCreateCounter(String name, String description) {
        return counters.computeIfAbsent(name, k -> 
            Counter.builder(name)
                .description(description)
                .register(meterRegistry));
    }
    
    private Counter getOrCreateCounterWithTags(String name, String description, String... tags) {
        String key = name + "_" + String.join("_", tags);
        return counters.computeIfAbsent(key, k -> 
            Counter.builder(name)
                .description(description)
                .tags(tags)
                .register(meterRegistry));
    }
    
    private Timer getOrCreateTimer(String name, String description) {
        return Timer.builder(name)
            .description(description)
            .register(meterRegistry);
    }
    
    private Timer getOrCreateTimerWithTags(String name, String description, String... tags) {
        return Timer.builder(name)
            .description(description)
            .tags(tags)
            .register(meterRegistry);
    }
    
    // Registry access
    public PrometheusMeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
    
    /**
     * Obtiene las métricas en formato Prometheus
     */
    public String getPrometheusMetrics() {
        return meterRegistry.scrape();
    }
    
    /**
     * Obtiene estadísticas de las métricas
     */
    public MetricsStats getStats() {
        Counter authSuccess = counters.get("auth_authentication_success_total");
        Counter authFailure = counters.get("auth_authentication_failure_total");
        Counter sessionCreated = counters.get("auth_session_created_total");
        Counter rateLimitExceeded = counters.get("auth_ratelimit_exceeded_total");
        Counter apiRequest = counters.get("auth_api_request_total");
        Counter apiError = counters.get("auth_api_error_total");
        
        return new MetricsStats(
            authSuccess != null ? (long) authSuccess.count() : 0,
            authFailure != null ? (long) authFailure.count() : 0,
            sessionCreated != null ? (long) sessionCreated.count() : 0,
            activeSessionsGauge.get(),
            rateLimitExceeded != null ? (long) rateLimitExceeded.count() : 0,
            apiRequest != null ? (long) apiRequest.count() : 0,
            apiError != null ? (long) apiError.count() : 0
        );
    }
    
    /**
     * Estadísticas de métricas
     */
    public static class MetricsStats {
        private final long authSuccessCount;
        private final long authFailureCount;
        private final long sessionCreatedCount;
        private final long activeSessionsCount;
        private final long rateLimitExceededCount;
        private final long apiRequestCount;
        private final long apiErrorCount;
        
        public MetricsStats(long authSuccessCount, long authFailureCount, long sessionCreatedCount,
                           long activeSessionsCount, long rateLimitExceededCount, long apiRequestCount,
                           long apiErrorCount) {
            this.authSuccessCount = authSuccessCount;
            this.authFailureCount = authFailureCount;
            this.sessionCreatedCount = sessionCreatedCount;
            this.activeSessionsCount = activeSessionsCount;
            this.rateLimitExceededCount = rateLimitExceededCount;
            this.apiRequestCount = apiRequestCount;
            this.apiErrorCount = apiErrorCount;
        }
        
        // Getters
        public long getAuthSuccessCount() { return authSuccessCount; }
        public long getAuthFailureCount() { return authFailureCount; }
        public long getSessionCreatedCount() { return sessionCreatedCount; }
        public long getActiveSessionsCount() { return activeSessionsCount; }
        public long getRateLimitExceededCount() { return rateLimitExceededCount; }
        public long getApiRequestCount() { return apiRequestCount; }
        public long getApiErrorCount() { return apiErrorCount; }
    }
}