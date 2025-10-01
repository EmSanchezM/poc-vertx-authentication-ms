package com.auth.microservice.application.command;

import com.auth.microservice.common.cqrs.Command;

import java.util.Objects;
import java.util.Optional;

/**
 * Command for updating user profile information.
 * Allows updating basic user information like names and active status.
 */
public class UpdateUserCommand extends Command {
    private final String targetUserId;
    private final Optional<String> firstName;
    private final Optional<String> lastName;
    private final Optional<Boolean> isActive;
    private final String ipAddress;
    private final String userAgent;

    public UpdateUserCommand(String executorUserId, String targetUserId, 
                           Optional<String> firstName, Optional<String> lastName,
                           Optional<Boolean> isActive, String ipAddress, String userAgent) {
        super(executorUserId);
        this.targetUserId = Objects.requireNonNull(targetUserId, "Target user ID cannot be null");
        this.firstName = Objects.requireNonNull(firstName, "First name optional cannot be null");
        this.lastName = Objects.requireNonNull(lastName, "Last name optional cannot be null");
        this.isActive = Objects.requireNonNull(isActive, "Active status optional cannot be null");
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public Optional<String> getFirstName() {
        return firstName;
    }

    public Optional<String> getLastName() {
        return lastName;
    }

    public Optional<Boolean> getIsActive() {
        return isActive;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public String toString() {
        return String.format("UpdateUserCommand{targetUserId='%s', firstName=%s, lastName=%s, isActive=%s, commandId=%s}", 
            targetUserId, firstName, lastName, isActive, getCommandId());
    }
}