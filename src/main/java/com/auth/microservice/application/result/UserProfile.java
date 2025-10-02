package com.auth.microservice.application.result;

import com.auth.microservice.domain.model.Role;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * User profile result containing essential user information for profile views.
 * This is a projection that excludes sensitive data like password hashes.
 */
public class UserProfile {
    private final UUID id;
    private final String username;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final boolean isActive;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final Set<String> roleNames;
    private final Set<String> permissions;

    public UserProfile(UUID id, String username, String email, String firstName, String lastName,
                      boolean isActive, LocalDateTime createdAt, LocalDateTime updatedAt,
                      Set<String> roleNames, Set<String> permissions) {
        this.id = Objects.requireNonNull(id, "User ID cannot be null");
        this.username = username;
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.firstName = Objects.requireNonNull(firstName, "First name cannot be null");
        this.lastName = Objects.requireNonNull(lastName, "Last name cannot be null");
        this.isActive = isActive;
        this.createdAt = Objects.requireNonNull(createdAt, "Created at cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Updated at cannot be null");
        this.roleNames = roleNames != null ? Set.copyOf(roleNames) : Set.of();
        this.permissions = permissions != null ? Set.copyOf(permissions) : Set.of();
    }

    public UUID getId() {
        return id;
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

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isActive() {
        return isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Set<String> getRoleNames() {
        return roleNames;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        UserProfile that = (UserProfile) obj;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("UserProfile{id=%s, username='%s', email='%s', fullName='%s', active=%s, roles=%s}", 
            id, username, email, getFullName(), isActive, roleNames);
    }
}