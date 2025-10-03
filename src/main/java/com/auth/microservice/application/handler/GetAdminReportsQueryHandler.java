package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetAdminReportsQuery;
import com.auth.microservice.application.result.AdminReportsResult;
import com.auth.microservice.common.cqrs.QueryHandler;
import com.auth.microservice.domain.port.RoleRepository;
import com.auth.microservice.domain.port.SessionRepository;
import com.auth.microservice.domain.port.UserRepository;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Handler for admin reports queries.
 * Generates various administrative reports and system metrics.
 */
public class GetAdminReportsQueryHandler implements QueryHandler<GetAdminReportsQuery, AdminReportsResult> {
    
    private static final Logger logger = LoggerFactory.getLogger(GetAdminReportsQueryHandler.class);
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SessionRepository sessionRepository;
    
    public GetAdminReportsQueryHandler(UserRepository userRepository, RoleRepository roleRepository, 
                                     SessionRepository sessionRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "UserRepository cannot be null");
        this.roleRepository = Objects.requireNonNull(roleRepository, "RoleRepository cannot be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "SessionRepository cannot be null");
    }
    
    @Override
    public Future<AdminReportsResult> handle(GetAdminReportsQuery query) {
        logger.debug("Processing admin reports query: {}", query);
        
        return switch (query.getReportType().toLowerCase()) {
            case "overview", "summary" -> generateOverviewReport(query);
            case "users" -> generateUsersReport(query);
            case "roles" -> generateRolesReport(query);
            case "sessions" -> generateSessionsReport(query);
            case "security" -> generateSecurityReport(query);
            default -> Future.failedFuture(new IllegalArgumentException("Unknown report type: " + query.getReportType()));
        };
    }
    
    private Future<AdminReportsResult> generateOverviewReport(GetAdminReportsQuery query) {
        logger.debug("Generating overview report for user: {}", query.getUserId());
        
        // Get basic counts from repositories
        Future<Long> totalUsersFuture = userRepository.countAll();
        Future<Long> activeUsersFuture = userRepository.countActive();
        Future<Long> totalRolesFuture = roleRepository.countAll();
        Future<Long> activeSessionsFuture = sessionRepository.countActiveSessions();
        
        return Future.all(totalUsersFuture, activeUsersFuture, totalRolesFuture, activeSessionsFuture)
            .map(compositeFuture -> {
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("totalUsers", compositeFuture.resultAt(0));
                metrics.put("activeUsers", compositeFuture.resultAt(1));
                metrics.put("totalRoles", compositeFuture.resultAt(2));
                metrics.put("activeSessions", compositeFuture.resultAt(3));
                metrics.put("systemUptime", getSystemUptime());
                metrics.put("lastUpdated", LocalDateTime.now());
                
                Map<String, Object> details = null;
                if (query.isIncludeDetails()) {
                    details = new HashMap<>();
                    details.put("inactiveUsers", (Long) compositeFuture.resultAt(0) - (Long) compositeFuture.resultAt(1));
                    details.put("userActivityRate", calculateActivityRate((Long) compositeFuture.resultAt(1), (Long) compositeFuture.resultAt(0)));
                }
                
                return new AdminReportsResult("overview", metrics, details);
            })
            .onSuccess(result -> logger.info("Overview report generated successfully for user: {}", query.getUserId()))
            .onFailure(throwable -> logger.error("Failed to generate overview report for user: {}", query.getUserId(), throwable));
    }
    
    private Future<AdminReportsResult> generateUsersReport(GetAdminReportsQuery query) {
        logger.debug("Generating users report for user: {}", query.getUserId());
        
        Future<Long> totalUsersFuture = userRepository.countAll();
        Future<Long> activeUsersFuture = userRepository.countActive();
        Future<Long> recentUsersFuture = userRepository.countCreatedSince(LocalDateTime.now().minusDays(30));
        
        return Future.all(totalUsersFuture, activeUsersFuture, recentUsersFuture)
            .map(compositeFuture -> {
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("totalUsers", compositeFuture.resultAt(0));
                metrics.put("activeUsers", compositeFuture.resultAt(1));
                metrics.put("inactiveUsers", (Long) compositeFuture.resultAt(0) - (Long) compositeFuture.resultAt(1));
                metrics.put("recentUsers", compositeFuture.resultAt(2));
                metrics.put("userGrowthRate", calculateGrowthRate((Long) compositeFuture.resultAt(2)));
                
                Map<String, Object> details = null;
                if (query.isIncludeDetails()) {
                    details = new HashMap<>();
                    details.put("userActivityRate", calculateActivityRate((Long) compositeFuture.resultAt(1), (Long) compositeFuture.resultAt(0)));
                    details.put("averageUsersPerDay", (Long) compositeFuture.resultAt(2) / 30.0);
                }
                
                return new AdminReportsResult("users", metrics, details);
            })
            .onSuccess(result -> logger.info("Users report generated successfully for user: {}", query.getUserId()))
            .onFailure(throwable -> logger.error("Failed to generate users report for user: {}", query.getUserId(), throwable));
    }
    
    private Future<AdminReportsResult> generateRolesReport(GetAdminReportsQuery query) {
        logger.debug("Generating roles report for user: {}", query.getUserId());
        
        Future<Long> totalRolesFuture = roleRepository.countAll();
        Future<Map<String, Long>> roleDistributionFuture = roleRepository.getRoleDistribution();
        
        return Future.all(totalRolesFuture, roleDistributionFuture)
            .map(compositeFuture -> {
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("totalRoles", compositeFuture.resultAt(0));
                
                @SuppressWarnings("unchecked")
                Map<String, Long> distribution = (Map<String, Long>) compositeFuture.resultAt(1);
                metrics.put("roleDistribution", distribution);
                
                if (distribution != null && !distribution.isEmpty()) {
                    String mostCommonRole = distribution.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("N/A");
                    metrics.put("mostCommonRole", mostCommonRole);
                }
                
                Map<String, Object> details = null;
                if (query.isIncludeDetails()) {
                    details = new HashMap<>();
                    details.put("averageUsersPerRole", distribution != null ? 
                        distribution.values().stream().mapToLong(Long::longValue).average().orElse(0.0) : 0.0);
                }
                
                return new AdminReportsResult("roles", metrics, details);
            })
            .onSuccess(result -> logger.info("Roles report generated successfully for user: {}", query.getUserId()))
            .onFailure(throwable -> logger.error("Failed to generate roles report for user: {}", query.getUserId(), throwable));
    }
    
    private Future<AdminReportsResult> generateSessionsReport(GetAdminReportsQuery query) {
        logger.debug("Generating sessions report for user: {}", query.getUserId());
        
        Future<Long> activeSessionsFuture = sessionRepository.countActiveSessions();
        Future<Long> totalSessionsFuture = sessionRepository.countTotalSessions();
        Future<Long> recentSessionsFuture = sessionRepository.countSessionsCreatedSince(LocalDateTime.now().minusDays(1));
        
        return Future.all(activeSessionsFuture, totalSessionsFuture, recentSessionsFuture)
            .map(compositeFuture -> {
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("activeSessions", compositeFuture.resultAt(0));
                metrics.put("totalSessions", compositeFuture.resultAt(1));
                metrics.put("recentSessions", compositeFuture.resultAt(2));
                metrics.put("sessionUtilizationRate", calculateSessionUtilization((Long) compositeFuture.resultAt(0), (Long) compositeFuture.resultAt(1)));
                
                Map<String, Object> details = null;
                if (query.isIncludeDetails()) {
                    details = new HashMap<>();
                    details.put("expiredSessions", (Long) compositeFuture.resultAt(1) - (Long) compositeFuture.resultAt(0));
                    details.put("averageSessionsPerDay", (Long) compositeFuture.resultAt(2));
                }
                
                return new AdminReportsResult("sessions", metrics, details);
            })
            .onSuccess(result -> logger.info("Sessions report generated successfully for user: {}", query.getUserId()))
            .onFailure(throwable -> logger.error("Failed to generate sessions report for user: {}", query.getUserId(), throwable));
    }
    
    private Future<AdminReportsResult> generateSecurityReport(GetAdminReportsQuery query) {
        logger.debug("Generating security report for user: {}", query.getUserId());
        
        // For now, return basic security metrics
        // In a real implementation, you would query security logs, failed login attempts, etc.
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("reportType", "security");
        metrics.put("status", "healthy");
        metrics.put("lastSecurityScan", LocalDateTime.now().minusHours(1));
        metrics.put("securityLevel", "high");
        
        Map<String, Object> details = null;
        if (query.isIncludeDetails()) {
            details = new HashMap<>();
            details.put("failedLoginAttempts", 0L); // Would come from security logs
            details.put("suspiciousActivities", 0L); // Would come from security monitoring
            details.put("blockedIPs", 0L); // Would come from rate limiting service
        }
        
        AdminReportsResult result = new AdminReportsResult("security", metrics, details);
        
        logger.info("Security report generated successfully for user: {}", query.getUserId());
        return Future.succeededFuture(result);
    }
    
    // Helper methods for calculations
    
    private String getSystemUptime() {
        long uptimeMillis = System.currentTimeMillis() - getStartTime();
        long hours = uptimeMillis / (1000 * 60 * 60);
        return hours + " hours";
    }
    
    private long getStartTime() {
        // In a real implementation, this would be stored when the application starts
        return System.currentTimeMillis() - (24 * 60 * 60 * 1000); // Mock: 24 hours ago
    }
    
    private double calculateActivityRate(long activeUsers, long totalUsers) {
        if (totalUsers == 0) return 0.0;
        return (double) activeUsers / totalUsers * 100.0;
    }
    
    private double calculateGrowthRate(long recentUsers) {
        // Simple growth rate calculation based on recent users
        return recentUsers * 12.0; // Annualized growth estimate
    }
    
    private double calculateSessionUtilization(long activeSessions, long totalSessions) {
        if (totalSessions == 0) return 0.0;
        return (double) activeSessions / totalSessions * 100.0;
    }
    
    @Override
    public Class<GetAdminReportsQuery> getQueryType() {
        return GetAdminReportsQuery.class;
    }
}