package com.auth.microservice.application.mapper;

import com.auth.microservice.application.dto.response.PermissionResponse;
import com.auth.microservice.domain.model.Permission;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for Permission entity and DTOs
 */
public class PermissionMapper {
    
    private PermissionMapper() {
        // Utility class
    }
    
    /**
     * Maps Permission entity to PermissionResponse DTO
     */
    public static PermissionResponse toResponse(Permission permission) {
        if (permission == null) {
            return null;
        }
        
        return new PermissionResponse(
            permission.getId().toString(),
            permission.getName(),
            permission.getResource(),
            permission.getAction(),
            permission.getDescription(),
            permission.getFullPermissionName()
        );
    }
    
    /**
     * Maps list of Permission entities to list of PermissionResponse DTOs
     */
    public static List<PermissionResponse> toResponseList(List<Permission> permissions) {
        if (permissions == null) {
            return List.of();
        }
        
        return permissions.stream()
            .map(PermissionMapper::toResponse)
            .collect(Collectors.toList());
    }
}