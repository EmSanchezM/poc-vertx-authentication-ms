package com.auth.microservice.application.command;

import com.auth.microservice.common.cqrs.Command;

import java.util.Objects;
import java.util.Set;

/**
 * Command for creating a new user by an administrator.
 * This differs from RegisterUserCommand as it's used for admin-initiated user creation.
 */
public class CreateUserCommand extends Command {
    private final String username;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final Set<String> roleNames;
    private final boolean isActive;
    private final String ipAddress;
    private final String userAgent;

    public CreateUserCommand(String adminUserId, String username, String email, 
                           String firstName, String lastName, Set<String> roleNames,
                           boolean isActive, String ipAddress, String userAgent) {
        super(adminUserId);
        this.username = Objects.requireNonNull(username, "Username cannot be null");
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.firstName = Objects.requireNonNull(firstName, "First name cannot be null");
        this.lastName = Objects.requireNonNull(lastName, "Last name cannot be null");
        this.roleNames = roleNames != null ? Set.copyOf(roleNames) : Set.of();
        this.isActive = isActive;
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

    public Set<String> getRoleNames() {
        return roleNames;
    }

    public boolean isActive() {
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
        return String.format("CreateUserCommand{username='%s', email='%s', firstName='%s', lastName='%s', roles=%s, active=%s, commandId=%s}", 
            username, email, firstName, lastName, roleNames, isActive, getCommandId());
    }
}