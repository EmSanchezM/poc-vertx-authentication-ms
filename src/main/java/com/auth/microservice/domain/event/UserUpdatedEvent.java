package com.auth.microservice.domain.event;

import java.util.Map;

/**
 * Domain event fired when a user is updated.
 * Contains information about what fields were changed for audit purposes.
 */
public class UserUpdatedEvent extends DomainEvent {
    private final String username;
    private final String email;
    private final Map<String, Object> changedFields;
    private final String updatedByUserId;
    private final String ipAddress;
    private final String userAgent;

    public UserUpdatedEvent(String userId, String username, String email,
                          Map<String, Object> changedFields, String updatedByUserId,
                          String ipAddress, String userAgent) {
        super(userId);
        this.username = username;
        this.email = email;
        this.changedFields = changedFields != null ? Map.copyOf(changedFields) : Map.of();
        this.updatedByUserId = updatedByUserId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public Map<String, Object> getChangedFields() {
        return changedFields;
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
        return String.format("UserUpdatedEvent{userId='%s', username='%s', changedFields=%s, updatedBy='%s', eventId=%s}", 
            getAggregateId(), username, changedFields.keySet(), updatedByUserId, getEventId());
    }
}