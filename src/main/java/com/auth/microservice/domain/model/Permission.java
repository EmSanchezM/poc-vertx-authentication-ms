package com.auth.microservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Permission domain entity representing a specific permission in the RBAC system
 */
public class Permission {
    private UUID id;
    private String name;
    private String resource;
    private String action;
    private String description;
    
    // Constructor for new permissions
    public Permission(String name, String resource, String action, String description) {
        this.id = UUID.randomUUID();
        this.name = validateName(name);
        this.resource = validateResource(resource);
        this.action = validateAction(action);
        this.description = description != null ? description.trim() : "";
    }
    
    // Constructor for existing permissions (from database)
    public Permission(UUID id, String name, String resource, String action, String description) {
        this.id = Objects.requireNonNull(id, "Permission ID cannot be null");
        this.name = validateName(name);
        this.resource = validateResource(resource);
        this.action = validateAction(action);
        this.description = description != null ? description.trim() : "";
    }
    
    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Permission name cannot be null or empty");
        }
        
        String trimmed = name.trim().toUpperCase();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("Permission name cannot exceed 100 characters");
        }
        
        if (!trimmed.matches("^[A-Z_]+$")) {
            throw new IllegalArgumentException("Permission name can only contain uppercase letters and underscores");
        }
        
        return trimmed;
    }
    
    private String validateResource(String resource) {
        if (resource == null || resource.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource cannot be null or empty");
        }
        
        String trimmed = resource.trim().toLowerCase();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("Resource cannot exceed 100 characters");
        }
        
        if (!trimmed.matches("^[a-z_]+$")) {
            throw new IllegalArgumentException("Resource can only contain lowercase letters and underscores");
        }
        
        return trimmed;
    }
    
    private String validateAction(String action) {
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }
        
        String trimmed = action.trim().toLowerCase();
        if (trimmed.length() > 50) {
            throw new IllegalArgumentException("Action cannot exceed 50 characters");
        }
        
        if (!trimmed.matches("^[a-z_]+$")) {
            throw new IllegalArgumentException("Action can only contain lowercase letters and underscores");
        }
        
        return trimmed;
    }
    
    public String getFullPermissionName() {
        return resource + ":" + action;
    }
    
    public boolean matches(String resource, String action) {
        return this.resource.equals(resource) && this.action.equals(action);
    }
    
    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getResource() { return resource; }
    public String getAction() { return action; }
    public String getDescription() { return description; }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Permission permission = (Permission) obj;
        return Objects.equals(id, permission.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Permission{id=" + id + ", name='" + name + "', resource='" + resource + 
               "', action='" + action + "'}";
    }
}