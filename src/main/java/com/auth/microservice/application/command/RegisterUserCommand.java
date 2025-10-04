package com.auth.microservice.application.command;

import com.auth.microservice.common.cqrs.Command;

import java.util.Objects;
import java.util.Set;

/**
 * Command for user registration with optional automatic username generation.
 * Contains all necessary information to create a new user account.
 * 
 * When username is null or empty, the system will automatically generate a username
 * based on the firstName and lastName following the pattern "firstname.lastname".
 * When username is provided explicitly, it will be validated and used as-is.
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

    /**
     * Creates a new RegisterUserCommand with optional automatic username generation.
     * 
     * @param username Username for the account (can be null for automatic generation)
     * @param email User's email address (required)
     * @param password User's password (required)
     * @param firstName User's first name (required, especially when username is null)
     * @param lastName User's last name (required, especially when username is null)
     * @param roleNames Set of role names to assign to the user
     * @param ipAddress IP address of the registration request
     * @param userAgent User agent string from the registration request
     */
    public RegisterUserCommand(String username, String email, String password, 
                             String firstName, String lastName, Set<String> roleNames,
                             String ipAddress, String userAgent) {
        super(null); // No userId yet since we're creating the user
        
        // Username can be null for automatic generation, but if provided, it should not be empty
        this.username = username != null && !username.trim().isEmpty() ? username.trim() : null;
        
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.password = Objects.requireNonNull(password, "Password cannot be null");
        this.firstName = Objects.requireNonNull(firstName, "First name cannot be null");
        this.lastName = Objects.requireNonNull(lastName, "Last name cannot be null");
        
        // Validate that firstName and lastName are not empty when username is null
        if (this.username == null) {
            if (firstName.trim().isEmpty()) {
                throw new IllegalArgumentException("First name cannot be empty when username is not provided");
            }
            if (lastName.trim().isEmpty()) {
                throw new IllegalArgumentException("Last name cannot be empty when username is not provided");
            }
        }
        
        this.roleNames = roleNames != null ? Set.copyOf(roleNames) : Set.of();
        this.ipAddress = ipAddress; // Can be null
        this.userAgent = userAgent; // Can be null
    }

    public String getUsername() {
        return username;
    }

    /**
     * Checks if an explicit username was provided during command creation.
     * 
     * @return true if username was explicitly provided, false if it should be auto-generated
     */
    public boolean hasExplicitUsername() {
        return username != null;
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
        String usernameDisplay = username != null ? username : "[auto-generated]";
        return String.format("RegisterUserCommand{username='%s', email='%s', firstName='%s', lastName='%s', roles=%s, commandId=%s}", 
            usernameDisplay, email, firstName, lastName, roleNames, getCommandId());
    }
}