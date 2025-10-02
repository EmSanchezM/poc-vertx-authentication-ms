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
 * Audit logger that tracks all administrative and user management operations.
 * Includes geolocation information for compliance and security auditing.
 */
public class AuditLogger {
    
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    private final GeoLocationService geoLocationService;
    
    public AuditLogger(GeoLocationService geoLocationService) {
        this.geoLocationService = geoLocationService;
    }
    
    /**
     * Logs user creation events.
     */
    public Future<Void> logUserCreated(String adminUserId, String adminEmail, String createdUserId, 
                                      String createdUserEmail, String ipAddress, Map<String, String> userDetails) {
        return geoLocationService.getCountryByIp(ipAddress)
            .onSuccess(country -> {
                try {
                    MDC.put("event_type", "user_created");
                    MDC.put("admin_user_id", adminUserId);
                    MDC.put("admin_email", adminEmail);
                    MDC.put("created_user_id", createdUserId);
                    MDC.put("created_user_email", createdUserEmail);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("country", country);
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    // Add user details to MDC
                    if (userDetails != null) {
                        userDetails.forEach((key, value) -> MDC.put("user_" + key, value));
                    }
                    
                    AUDIT_LOG.info("User {} created by admin {} from {} ({})", 
                        createdUserEmail, adminEmail, ipAddress, country);
                } finally {
                    MDC.clear();
                }
            })
            .onFailure(throwable -> {
                try {
                    MDC.put("event_type", "user_created");
                    MDC.put("admin_user_id", adminUserId);
                    MDC.put("admin_email", adminEmail);
                    MDC.put("created_user_id", createdUserId);
                    MDC.put("created_user_email", createdUserEmail);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("country", "UNKNOWN");
                    MDC.put("geo_error", throwable.getMessage());
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    // Add user details to MDC
                    if (userDetails != null) {
                        userDetails.forEach((key, value) -> MDC.put("user_" + key, value));
                    }
                    
                    AUDIT_LOG.info("User {} created by admin {} from {} (country lookup failed)", 
                        createdUserEmail, adminEmail, ipAddress);
                } finally {
                    MDC.clear();
                }
            })
            .mapEmpty();
    }
    
    /**
     * Logs user modification events.
     */
    public Future<Void> logUserModified(String adminUserId, String adminEmail, String modifiedUserId, 
                                       String modifiedUserEmail, String ipAddress, String operation, 
                                       Map<String, String> changes) {
        return geoLocationService.getCountryByIp(ipAddress)
            .onSuccess(country -> {
                try {
                    MDC.put("event_type", "user_modified");
                    MDC.put("admin_user_id", adminUserId);
                    MDC.put("admin_email", adminEmail);
                    MDC.put("modified_user_id", modifiedUserId);
                    MDC.put("modified_user_email", modifiedUserEmail);
                    MDC.put("operation", operation);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("country", country);
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    // Add changes to MDC
                    if (changes != null) {
                        changes.forEach((key, value) -> MDC.put("change_" + key, value));
                    }
                    
                    AUDIT_LOG.info("User {} modified by admin {} from {} ({}): {}", 
                        modifiedUserEmail, adminEmail, ipAddress, country, operation);
                } finally {
                    MDC.clear();
                }
            })
            .onFailure(throwable -> {
                try {
                    MDC.put("event_type", "user_modified");
                    MDC.put("admin_user_id", adminUserId);
                    MDC.put("admin_email", adminEmail);
                    MDC.put("modified_user_id", modifiedUserId);
                    MDC.put("modified_user_email", modifiedUserEmail);
                    MDC.put("operation", operation);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("country", "UNKNOWN");
                    MDC.put("geo_error", throwable.getMessage());
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    // Add changes to MDC
                    if (changes != null) {
                        changes.forEach((key, value) -> MDC.put("change_" + key, value));
                    }
                    
                    AUDIT_LOG.info("User {} modified by admin {} from {} (country lookup failed): {}", 
                        modifiedUserEmail, adminEmail, ipAddress, operation);
                } finally {
                    MDC.clear();
                }
            })
            .mapEmpty();
    }
    
    /**
     * Logs role assignment events.
     */
    public Future<Void> logRoleAssigned(String adminUserId, String adminEmail, String targetUserId, 
                                       String targetUserEmail, String roleId, String roleName, String ipAddress) {
        return geoLocationService.getCountryByIp(ipAddress)
            .onSuccess(country -> {
                try {
                    MDC.put("event_type", "role_assigned");
                    MDC.put("admin_user_id", adminUserId);
                    MDC.put("admin_email", adminEmail);
                    MDC.put("target_user_id", targetUserId);
                    MDC.put("target_user_email", targetUserEmail);
                    MDC.put("role_id", roleId);
                    MDC.put("role_name", roleName);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("country", country);
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    AUDIT_LOG.info("Role '{}' assigned to user {} by admin {} from {} ({})", 
                        roleName, targetUserEmail, adminEmail, ipAddress, country);
                } finally {
                    MDC.clear();
                }
            })
            .onFailure(throwable -> {
                try {
                    MDC.put("event_type", "role_assigned");
                    MDC.put("admin_user_id", adminUserId);
                    MDC.put("admin_email", adminEmail);
                    MDC.put("target_user_id", targetUserId);
                    MDC.put("target_user_email", targetUserEmail);
                    MDC.put("role_id", roleId);
                    MDC.put("role_name", roleName);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("country", "UNKNOWN");
                    MDC.put("geo_error", throwable.getMessage());
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    AUDIT_LOG.info("Role '{}' assigned to user {} by admin {} from {} (country lookup failed)", 
                        roleName, targetUserEmail, adminEmail, ipAddress);
                } finally {
                    MDC.clear();
                }
            })
            .mapEmpty();
    }
    
    /**
     * Logs role removal events.
     */
    public Future<Void> logRoleRemoved(String adminUserId, String adminEmail, String targetUserId, 
                                      String targetUserEmail, String roleId, String roleName, String ipAddress) {
        return geoLocationService.getCountryByIp(ipAddress)
            .onSuccess(country -> {
                try {
                    MDC.put("event_type", "role_removed");
                    MDC.put("admin_user_id", adminUserId);
                    MDC.put("admin_email", adminEmail);
                    MDC.put("target_user_id", targetUserId);
                    MDC.put("target_user_email", targetUserEmail);
                    MDC.put("role_id", roleId);
                    MDC.put("role_name", roleName);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("country", country);
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    AUDIT_LOG.info("Role '{}' removed from user {} by admin {} from {} ({})", 
                        roleName, targetUserEmail, adminEmail, ipAddress, country);
                } finally {
                    MDC.clear();
                }
            })
            .onFailure(throwable -> {
                try {
                    MDC.put("event_type", "role_removed");
                    MDC.put("admin_user_id", adminUserId);
                    MDC.put("admin_email", adminEmail);
                    MDC.put("target_user_id", targetUserId);
                    MDC.put("target_user_email", targetUserEmail);
                    MDC.put("role_id", roleId);
                    MDC.put("role_name", roleName);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("country", "UNKNOWN");
                    MDC.put("geo_error", throwable.getMessage());
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    AUDIT_LOG.info("Role '{}' removed from user {} by admin {} from {} (country lookup failed)", 
                        roleName, targetUserEmail, adminEmail, ipAddress);
                } finally {
                    MDC.clear();
                }
            })
            .mapEmpty();
    }
    
    /**
     * Logs role creation events.
     */
    public Future<Void> logRoleCreated(String adminUserId, String adminEmail, String roleId, String roleName, 
                                      String ipAddress, Map<String, String> roleDetails) {
        return geoLocationService.getCountryByIp(ipAddress)
            .onSuccess(country -> {
                try {
                    MDC.put("event_type", "role_created");
                    MDC.put("admin_user_id", adminUserId);
                    MDC.put("admin_email", adminEmail);
                    MDC.put("role_id", roleId);
                    MDC.put("role_name", roleName);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("country", country);
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    // Add role details to MDC
                    if (roleDetails != null) {
                        roleDetails.forEach((key, value) -> MDC.put("role_" + key, value));
                    }
                    
                    AUDIT_LOG.info("Role '{}' created by admin {} from {} ({})", 
                        roleName, adminEmail, ipAddress, country);
                } finally {
                    MDC.clear();
                }
            })
            .onFailure(throwable -> {
                try {
                    MDC.put("event_type", "role_created");
                    MDC.put("admin_user_id", adminUserId);
                    MDC.put("admin_email", adminEmail);
                    MDC.put("role_id", roleId);
                    MDC.put("role_name", roleName);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("country", "UNKNOWN");
                    MDC.put("geo_error", throwable.getMessage());
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    // Add role details to MDC
                    if (roleDetails != null) {
                        roleDetails.forEach((key, value) -> MDC.put("role_" + key, value));
                    }
                    
                    AUDIT_LOG.info("Role '{}' created by admin {} from {} (country lookup failed)", 
                        roleName, adminEmail, ipAddress);
                } finally {
                    MDC.clear();
                }
            })
            .mapEmpty();
    }
    
    /**
     * Logs permission assignment events.
     */
    public Future<Void> logPermissionAssigned(String adminUserId, String adminEmail, String roleId, String roleName, 
                                             String permissionId, String permissionName, String ipAddress) {
        return geoLocationService.getCountryByIp(ipAddress)
            .onSuccess(country -> {
                try {
                    MDC.put("event_type", "permission_assigned");
                    MDC.put("admin_user_id", adminUserId);
                    MDC.put("admin_email", adminEmail);
                    MDC.put("role_id", roleId);
                    MDC.put("role_name", roleName);
                    MDC.put("permission_id", permissionId);
                    MDC.put("permission_name", permissionName);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("country", country);
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    AUDIT_LOG.info("Permission '{}' assigned to role '{}' by admin {} from {} ({})", 
                        permissionName, roleName, adminEmail, ipAddress, country);
                } finally {
                    MDC.clear();
                }
            })
            .onFailure(throwable -> {
                try {
                    MDC.put("event_type", "permission_assigned");
                    MDC.put("admin_user_id", adminUserId);
                    MDC.put("admin_email", adminEmail);
                    MDC.put("role_id", roleId);
                    MDC.put("role_name", roleName);
                    MDC.put("permission_id", permissionId);
                    MDC.put("permission_name", permissionName);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("country", "UNKNOWN");
                    MDC.put("geo_error", throwable.getMessage());
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    AUDIT_LOG.info("Permission '{}' assigned to role '{}' by admin {} from {} (country lookup failed)", 
                        permissionName, roleName, adminEmail, ipAddress);
                } finally {
                    MDC.clear();
                }
            })
            .mapEmpty();
    }
    
    /**
     * Logs data access events for sensitive operations.
     */
    public Future<Void> logDataAccess(String userId, String userEmail, String ipAddress, String resource, 
                                     String operation, String recordId, Map<String, String> metadata) {
        return geoLocationService.getCountryByIp(ipAddress)
            .onSuccess(country -> {
                try {
                    MDC.put("event_type", "data_access");
                    MDC.put("user_id", userId);
                    MDC.put("user_email", userEmail);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("resource", resource);
                    MDC.put("operation", operation);
                    MDC.put("record_id", recordId);
                    MDC.put("country", country);
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    // Add metadata to MDC
                    if (metadata != null) {
                        metadata.forEach(MDC::put);
                    }
                    
                    AUDIT_LOG.info("Data access by user {} from {} ({}): {} operation on {} (record: {})", 
                        userEmail, ipAddress, country, operation, resource, recordId);
                } finally {
                    MDC.clear();
                }
            })
            .onFailure(throwable -> {
                try {
                    MDC.put("event_type", "data_access");
                    MDC.put("user_id", userId);
                    MDC.put("user_email", userEmail);
                    MDC.put("ip_address", ipAddress);
                    MDC.put("resource", resource);
                    MDC.put("operation", operation);
                    MDC.put("record_id", recordId);
                    MDC.put("country", "UNKNOWN");
                    MDC.put("geo_error", throwable.getMessage());
                    MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
                    MDC.put("event_id", UUID.randomUUID().toString());
                    
                    // Add metadata to MDC
                    if (metadata != null) {
                        metadata.forEach(MDC::put);
                    }
                    
                    AUDIT_LOG.info("Data access by user {} from {} (country lookup failed): {} operation on {} (record: {})", 
                        userEmail, ipAddress, operation, resource, recordId);
                } finally {
                    MDC.clear();
                }
            })
            .mapEmpty();
    }
}