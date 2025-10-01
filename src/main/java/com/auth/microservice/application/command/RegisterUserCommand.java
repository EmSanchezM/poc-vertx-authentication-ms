package com.auth.microservice.application.command;

import com.auth.microservice.common.cqrs.Command;

import java.util.Objects;
import java.util.Set;

/**
 * Command for user registration.
 * Contains all necessary information to create a new user account.
 */
public class RegisterUserCommand extends Command {
    private final String username;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;
    private final Set<String> roleNames;
    private final String ipAddress;
    private final String userAgent;

    public RegisterUserCommand(String username, String email, String password, 
                             String firstName, String lastName, Set<String> roleNames,
                             String ipAddress, String userAgent) {
        super(null); // No userId yet since we're creating the user
        this.username = Objects.requireNonNull(username, "Username cannot be null");
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.password = Objects.requireNonNull(password, "Password cannot be null");
        this.firstName = Objects.requireNonNull(firstName, "First name cannot be null");
        this.lastName = Objects.requireNonNull(lastName, "Last name cannot be null");
        this.roleNames = roleNames != null ? Set.copyOf(roleNames) : Set.of();
        this.ipAddress = ipAddress; // Can be null
        this.userAgent = userAgent; // Can be null
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
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

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public String toString() {
        return String.format("RegisterUserCommand{username='%s', email='%s', firstName='%s', lastName='%s', roles=%s, commandId=%s}", 
            username, email, firstName, lastName, roleNames, getCommandId());
    }
}