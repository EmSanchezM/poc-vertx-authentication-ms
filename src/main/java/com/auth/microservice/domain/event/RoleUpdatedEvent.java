package com.auth.microservice.domain.event;

/**
 * Domain event fired when a role is updated in the system.
 */
public class RoleUpdatedEvent extends DomainEvent {
    private final String roleName;
    private final String oldDescription;
    private final String newDescription;
    private final String updatedByUserId;
    private final String ipAddress;
    private final String userAgent;

    public RoleUpdatedEvent(String roleId, String roleName, String oldDescription, 
                          String newDescription, String updatedByUserId, 
                          String ipAddress, String userAgent) {
        super(roleId);
        this.roleName = roleName;
        this.oldDescription = oldDescription;
        this.newDescription = newDescription;
        this.updatedByUserId = updatedByUserId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getOldDescription() {
        return oldDescription;
    }

    public String getNewDescription() {
        return newDescription;
    }

    public String getUpdatedByUserId() {
        return updatedByUserId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public String toString() {
        return String.format("RoleUpdatedEvent{roleId='%s', roleName='%s', oldDesc='%s', newDesc='%s', updatedBy='%s'}", 
            getAggregateId(), roleName, oldDescription, newDescription, updatedByUserId);
    }
}