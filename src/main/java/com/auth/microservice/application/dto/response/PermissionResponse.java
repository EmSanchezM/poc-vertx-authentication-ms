package com.auth.microservice.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * DTO for permission response
 */
public class PermissionResponse {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("resource")
    private String resource;
    
    @JsonProperty("action")
    private String action;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("fullPermissionName")
    private String fullPermissionName;
    
    // Default constructor for JSON serialization
    public PermissionResponse() {}
    
    public PermissionResponse(String id, String name, String resource, String action, 
                             String description, String fullPermissionName) {
        this.id = id;
        this.name = name;
        this.resource = resource;
        this.action = action;
        this.description = description;
        this.fullPermissionName = fullPermissionName;
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
    
    public String getResource() {
        return resource;
    }
    
    public void setResource(String resource) {
        this.resource = resource;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getFullPermissionName() {
        return fullPermissionName;
    }
    
    public void setFullPermissionName(String fullPermissionName) {
        this.fullPermissionName = fullPermissionName;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PermissionResponse that = (PermissionResponse) obj;
        return Objects.equals(id, that.id) &&
               Objects.equals(name, that.name) &&
               Objects.equals(resource, that.resource) &&
               Objects.equals(action, that.action) &&
               Objects.equals(description, that.description) &&
               Objects.equals(fullPermissionName, that.fullPermissionName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, name, resource, action, description, fullPermissionName);
    }
    
    @Override
    public String toString() {
        return "PermissionResponse{id='" + id + "', name='" + name + "', resource='" + resource + 
               "', action='" + action + "', fullPermissionName='" + fullPermissionName + "'}";
    }
}