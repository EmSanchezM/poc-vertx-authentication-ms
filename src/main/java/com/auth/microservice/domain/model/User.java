package com.auth.microservice.domain.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * User domain entity representing a system user with authentication and authorization
 */
public class User {
    private UUID id;
    private String username;
    private Email email;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Set<Role> roles;
    
    // Constructor for new users
    public User(String username, Email email, String passwordHash, String firstName, String lastName) {
        this.id = UUID.randomUUID();
        this.username = validateUsername(username);
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.passwordHash = validatePasswordHash(passwordHash);
        this.firstName = validateName(firstName, "First name");
        this.lastName = validateName(lastName, "Last name");
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.roles = new HashSet<>();
    }
    
    // Constructor for existing users (from database)
    public User(UUID id, String username, Email email, String passwordHash, String firstName, 
                String lastName, boolean isActive, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "User ID cannot be null");
        this.username = validateUsername(username);
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.passwordHash = validatePasswordHash(passwordHash);
        this.firstName = validateName(firstName, "First name");
        this.lastName = validateName(lastName, "Last name");
        this.isActive = isActive;
        this.createdAt = Objects.requireNonNull(createdAt, "Created at cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Updated at cannot be null");
        this.roles = new HashSet<>();
    }
    
    private String validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        
        String trimmed = username.trim();
        if (trimmed.length() < 3 || trimmed.length() > 50) {
            throw new IllegalArgumentException("Username must be between 3 and 50 characters");
        }
        
        if (!trimmed.matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalArgumentException("Username can only contain letters, numbers, dots, underscores, and hyphens");
        }
        
        return trimmed;
    }
    
    private String validatePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.trim().isEmpty()) {
            throw new IllegalArgumentException("Password hash cannot be null or empty");
        }
        return passwordHash.trim();
    }
    
    private String validateName(String name, String fieldName) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        
        String trimmed = name.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException(fieldName + " cannot exceed 100 characters");
        }
        
        return trimmed;
    }
    
    public void addRole(Role role) {
        Objects.requireNonNull(role, "Role cannot be null");
        this.roles.add(role);
        updateTimestamp();
    }
    
    public void removeRole(Role role) {
        Objects.requireNonNull(role, "Role cannot be null");
        this.roles.remove(role);
        updateTimestamp();
    }
    
    public void updateProfile(String firstName, String lastName) {
        this.firstName = validateName(firstName, "First name");
        this.lastName = validateName(lastName, "Last name");
        updateTimestamp();
    }
    
    public void changePassword(String newPasswordHash) {
        this.passwordHash = validatePasswordHash(newPasswordHash);
        updateTimestamp();
    }
    
    public void activate() {
        this.isActive = true;
        updateTimestamp();
    }
    
    public void deactivate() {
        this.isActive = false;
        updateTimestamp();
    }
    
    private void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
    
    public Set<Permission> getAllPermissions() {
        Set<Permission> permissions = new HashSet<>();
        for (Role role : roles) {
            permissions.addAll(role.getPermissions());
        }
        return permissions;
    }
    
    public boolean hasPermission(String permissionName) {
        return getAllPermissions().stream()
                .anyMatch(permission -> permission.getName().equals(permissionName));
    }
    
    // Getters
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public Email getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public boolean isActive() { return isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Set<Role> getRoles() { 
        return Collections.unmodifiableSet(new HashSet<>(roles)); 
    }
    
    public String getFullName() {
        return firstName + " " + lastName;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        User user = (User) obj;
        return Objects.equals(id, user.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', email=" + email + 
               ", fullName='" + getFullName() + "', active=" + isActive + "}";
    }
}