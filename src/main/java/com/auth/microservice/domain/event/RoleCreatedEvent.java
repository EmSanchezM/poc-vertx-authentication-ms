package com.auth.microservice.domain.event;

import java.util.Set;

/**
 * Domain event fired when a new role is created in the system.
 */
public class RoleCreatedEvent extends DomainEvent {
    private final String roleName;
    private final String description;
    private final Set<String> initialPermissions;
    private final String createdByUserId;
    private final String ipAddress;
    private final String userAgent;

    public RoleCreatedEvent(String roleId, String roleName, String description, 
                          Set<String> initialPermissions, String createdByUserId, 
                          String ipAddress, String userAgent) {
        super(roleId);
        this.roleName = roleName;
        this.description = description;
        this.initialPermissions = initialPermissions != null ? Set.copyOf(initialPermissions) : Set.of();
        this.createdByUserId = createdByUserId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getInitialPermissions() {
        return initialPermissions;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public String toString() {
        return String.format("RoleCreatedEvent{roleId='%s', roleName='%s', description='%s', permissions=%s, createdBy='%s'}", 
            getAggregateId(), roleName, description, initialPermissions, createdByUserId);
    }
}