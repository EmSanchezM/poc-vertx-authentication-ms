package com.auth.microservice.domain.event;

import java.util.Set;

/**
 * Domain event fired when a new user is created.
 * Contains information about the user creation for audit purposes.
 */
public class UserCreatedEvent extends DomainEvent {
    private final String username;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final Set<String> assignedRoles;
    private final String createdByUserId;
    private final String ipAddress;
    private final String userAgent;

    public UserCreatedEvent(String userId, String username, String email, 
                          String firstName, String lastName, Set<String> assignedRoles,
                          String createdByUserId, String ipAddress, String userAgent) {
        super(userId);
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.assignedRoles = assignedRoles != null ? Set.copyOf(assignedRoles) : Set.of();
        this.createdByUserId = createdByUserId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Set<String> getAssignedRoles() {
        return assignedRoles;
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
        return String.format("UserCreatedEvent{userId='%s', username='%s', email='%s', createdBy='%s', eventId=%s}", 
            getAggregateId(), username, email, createdByUserId, getEventId());
    }
}