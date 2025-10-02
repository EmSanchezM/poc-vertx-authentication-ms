package com.auth.microservice.domain.event;

import java.util.Set;

/**
 * Domain event fired when permissions are assigned to a role.
 */
public class PermissionsAssignedEvent extends DomainEvent {
    private final String roleName;
    private final Set<String> assignedPermissions;
    private final Set<String> removedPermissions;
    private final boolean replaceExisting;
    private final String assignedByUserId;
    private final String ipAddress;
    private final String userAgent;

    public PermissionsAssignedEvent(String roleId, String roleName, Set<String> assignedPermissions, 
                                  Set<String> removedPermissions, boolean replaceExisting,
                                  String assignedByUserId, String ipAddress, String userAgent) {
        super(roleId);
        this.roleName = roleName;
        this.assignedPermissions = assignedPermissions != null ? Set.copyOf(assignedPermissions) : Set.of();
        this.removedPermissions = removedPermissions != null ? Set.copyOf(removedPermissions) : Set.of();
        this.replaceExisting = replaceExisting;
        this.assignedByUserId = assignedByUserId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public String getRoleName() {
        return roleName;
    }

    public Set<String> getAssignedPermissions() {
        return assignedPermissions;
    }

    public Set<String> getRemovedPermissions() {
        return removedPermissions;
    }

    public boolean isReplaceExisting() {
        return replaceExisting;
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
        return String.format("PermissionsAssignedEvent{roleId='%s', roleName='%s', assigned=%s, removed=%s, replace=%s, assignedBy='%s'}", 
            getAggregateId(), roleName, assignedPermissions, removedPermissions, replaceExisting, assignedByUserId);
    }
}