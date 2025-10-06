package com.auth.microservice.domain.model;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Role domain entity representing a role in the RBAC system
 */
public class Role {
    private UUID id;
    private String name;
    private String description;
    private OffsetDateTime createdAt;
    private Set<Permission> permissions;
    
    // Constructor for new roles
    public Role(String name, String description) {
        this.id = UUID.randomUUID();
        this.name = validateName(name);
        this.description = description != null ? description.trim() : "";
        this.createdAt = OffsetDateTime.now();
        this.permissions = new HashSet<>();
    }
    
    // Constructor for existing roles (from database)
    public Role(UUID id, String name, String description, OffsetDateTime createdAt) {
        this.id = Objects.requireNonNull(id, "Role ID cannot be null");
        this.name = validateName(name);
        this.description = description != null ? description.trim() : "";
        this.createdAt = Objects.requireNonNull(createdAt, "Created at cannot be null");
        this.permissions = new HashSet<>();
    }
    
    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Role name cannot be null or empty");
        }
        
        String trimmed = name.trim().toUpperCase();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("Role name cannot exceed 100 characters");
        }
        
        if (!trimmed.matches("^[A-Z_]+$")) {
            throw new IllegalArgumentException("Role name can only contain uppercase letters and underscores");
        }
        
        return trimmed;
    }
    
    public void addPermission(Permission permission) {
        Objects.requireNonNull(permission, "Permission cannot be null");
        this.permissions.add(permission);
    }
    
    public void removePermission(Permission permission) {
        Objects.requireNonNull(permission, "Permission cannot be null");
        this.permissions.remove(permission);
    }
    
    public void addPermissions(Set<Permission> permissions) {
        Objects.requireNonNull(permissions, "Permissions set cannot be null");
        this.permissions.addAll(permissions);
    }
    
    public void clearPermissions() {
        this.permissions.clear();
    }
    
    public boolean hasPermission(String permissionName) {
        return permissions.stream()
                .anyMatch(permission -> permission.getName().equals(permissionName));
    }
    
    public boolean hasPermission(String resource, String action) {
        return permissions.stream()
                .anyMatch(permission -> permission.matches(resource, action));
    }
    
    public void updateDescription(String description) {
        this.description = description != null ? description.trim() : "";
    }
    
    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public Set<Permission> getPermissions() { 
        return Collections.unmodifiableSet(new HashSet<>(permissions)); 
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Role role = (Role) obj;
        return Objects.equals(id, role.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Role{id=" + id + ", name='" + name + "', description='" + description + 
               "', permissions=" + permissions.size() + "}";
    }
}