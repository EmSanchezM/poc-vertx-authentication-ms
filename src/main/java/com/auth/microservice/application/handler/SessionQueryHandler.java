package com.auth.microservice.application.handler;

import com.auth.microservice.domain.model.Session;
import com.auth.microservice.domain.port.SessionRepository;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import io.vertx.core.json.JsonObject;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class for session-related query handlers.
 * Provides common functionality and caching strategies for session queries.
 */
public abstract class SessionQueryHandler {
    
    protected final SessionRepository sessionRepository;
    protected final RedisAuthCacheService cacheService;
    
    protected SessionQueryHandler(SessionRepository sessionRepository, RedisAuthCacheService cacheService) {
        this.sessionRepository = sessionRepository;
        this.cacheService = cacheService;
    }
    
    /**
     * Filters sessions to only include valid (active and not expired) sessions
     */
    protected List<Session> filterValidSessions(List<Session> sessions) {
        return sessions.stream()
            .filter(Session::isValid)
            .collect(Collectors.toList());
    }
    
    /**
     * Creates a sanitized session representation for API responses
     */
    protected JsonObject createSessionSummary(Session session, boolean includeSensitiveData) {
        JsonObject summary = new JsonObject()
            .put("id", session.getId().toString())
            .put("userId", session.getUserId().toString())
            .put("createdAt", session.getCreatedAt().toString())
            .put("lastUsedAt", session.getLastUsedAt().toString())
            .put("expiresAt", session.getExpiresAt().toString())
            .put("isActive", session.isActive())
            .put("isExpired", session.isExpired())
            .put("isValid", session.isValid())
            .put("ipAddress", session.getIpAddress());
        
        if (includeSensitiveData) {
            // Only include sensitive data if explicitly requested and user has appropriate permissions
            summary.put("userAgent", session.getUserAgent());
        } else {
            // Provide limited user agent info for security
            String userAgent = session.getUserAgent();
            if (userAgent != null && !userAgent.isEmpty()) {
                // Extract basic browser/OS info without full user agent string
                String simplifiedUA = simplifyUserAgent(userAgent);
                summary.put("deviceInfo", simplifiedUA);
            }
        }
        
        return summary;
    }
    
    /**
     * Simplifies user agent string to show basic device/browser info without full fingerprinting data
     */
    private String simplifyUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }
        
        // Basic browser detection
        if (userAgent.contains("Chrome")) {
            return "Chrome Browser";
        } else if (userAgent.contains("Firefox")) {
            return "Firefox Browser";
        } else if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) {
            return "Safari Browser";
        } else if (userAgent.contains("Edge")) {
            return "Edge Browser";
        } else if (userAgent.contains("Mobile")) {
            return "Mobile Device";
        } else {
            return "Desktop Browser";
        }
    }
    
    /**
     * Checks if sessions show suspicious patterns that might indicate security issues
     */
    protected boolean detectSuspiciousActivity(List<Session> sessions) {
        if (sessions.size() > 10) {
            return true; // Too many concurrent sessions
        }
        
        // Check for sessions from multiple IPs in short time frame
        long recentSessions = sessions.stream()
            .filter(session -> session.getCreatedAt().isAfter(OffsetDateTime.now().minusHours(1)))
            .count();
        
        if (recentSessions > 3) {
            return true; // Rapid session creation
        }
        
        // Check for sessions from different geographic locations
        long distinctIPs = sessions.stream()
            .map(Session::getIpAddress)
            .filter(ip -> ip != null && !ip.equals("unknown"))
            .distinct()
            .count();
        
        return distinctIPs > 5; // Sessions from many different IPs
    }
}