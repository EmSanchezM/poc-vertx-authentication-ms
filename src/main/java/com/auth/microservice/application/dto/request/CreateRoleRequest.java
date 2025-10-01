package com.auth.microservice.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * DTO for create role request
 */
public class CreateRoleRequest {
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("permissionIds")
    private List<String> permissionIds;
    
    // Default constructor for JSON deserialization
    public CreateRoleRequest() {}
    
    public CreateRoleRequest(String name, String description, List<String> permissionIds) {
        this.name = name;
        this.description = description;
        this.permissionIds = permissionIds;
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
    
    public List<String> getPermissionIds() {
        return permissionIds;
    }
    
    public void setPermissionIds(List<String> permissionIds) {
        this.permissionIds = permissionIds;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CreateRoleRequest that = (CreateRoleRequest) obj;
        return Objects.equals(name, that.name) &&
               Objects.equals(description, that.description) &&
               Objects.equals(permissionIds, that.permissionIds);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, description, permissionIds);
    }
    
    @Override
    public String toString() {
        return "CreateRoleRequest{name='" + name + "', description='" + description + 
               "', permissionIds=" + permissionIds + "}";
    }
}