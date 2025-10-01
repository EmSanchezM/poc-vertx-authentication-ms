package com.auth.microservice.domain.event;

/**
 * Domain event fired when a role is assigned to a user.
 * Contains information about the role assignment for audit purposes.
 */
public class RoleAssignedEvent extends DomainEvent {
    private final String username;
    private final String email;
    private final String roleId;
    private final String roleName;
    private final String assignedByUserId;
    private final String ipAddress;
    private final String userAgent;

    public RoleAssignedEvent(String userId, String username, String email,
                           String roleId, String roleName, String assignedByUserId,
                           String ipAddress, String userAgent) {
        super(userId);
        this.username = username;
        this.email = email;
        this.roleId = roleId;
        this.roleName = roleName;
        this.assignedByUserId = assignedByUserId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getRoleId() {
        return roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getAssignedByUserId() {
        return assignedByUserId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public String toString() {
        return String.format("RoleAssignedEvent{userId='%s', username='%s', role='%s', assignedBy='%s', eventId=%s}", 
            getAggregateId(), username, roleName, assignedByUserId, getEventId());
    }
}