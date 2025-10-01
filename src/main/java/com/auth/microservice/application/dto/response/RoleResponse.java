package com.auth.microservice.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * DTO for role response
 */
public class RoleResponse {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
    
    @JsonProperty("permissions")
    private List<PermissionResponse> permissions;
    
    // Default constructor for JSON serialization
    public RoleResponse() {}
    
    public RoleResponse(String id, String name, String description, LocalDateTime createdAt,
                       List<PermissionResponse> permissions) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.permissions = permissions;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public List<PermissionResponse> getPermissions() {
        return permissions;
    }
    
    public void setPermissions(List<PermissionResponse> permissions) {
        this.permissions = permissions;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RoleResponse that = (RoleResponse) obj;
        return Objects.equals(id, that.id) &&
               Objects.equals(name, that.name) &&
               Objects.equals(description, that.description) &&
               Objects.equals(createdAt, that.createdAt) &&
               Objects.equals(permissions, that.permissions);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, createdAt, permissions);
    }
    
    @Override
    public String toString() {
        return "RoleResponse{id='" + id + "', name='" + name + "', description='" + description + 
               "', createdAt=" + createdAt + ", permissions=" + permissions + "}";
    }
}