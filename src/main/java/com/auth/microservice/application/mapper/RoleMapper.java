package com.auth.microservice.application.mapper;

import com.auth.microservice.application.dto.request.CreateRoleRequest;
import com.auth.microservice.application.dto.response.RoleResponse;
import com.auth.microservice.domain.model.Role;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for Role entity and DTOs
 */
public class RoleMapper {
    
    private RoleMapper() {
        // Utility class
    }
    
    /**
     * Maps CreateRoleRequest to Role domain entity
     */
    public static Role toEntity(CreateRoleRequest request) {
        if (request == null) {
            return null;
        }
        
        return new Role(request.getName(), request.getDescription());
    }
    
    /**
     * Maps Role entity to RoleResponse DTO
     */
    public static RoleResponse toResponse(Role role) {
        if (role == null) {
            return null;
        }
        
        return new RoleResponse(
            role.getId().toString(),
            role.getName(),
            role.getDescription(),
            role.getCreatedAt(),
            role.getPermissions().stream()
                .map(PermissionMapper::toResponse)
                .collect(Collectors.toList())
        );
    }
    
    /**
     * Maps Role entity to RoleResponse DTO without permissions (for performance)
     */
    public static RoleResponse toResponseWithoutPermissions(Role role) {
        if (role == null) {
            return null;
        }
        
        return new RoleResponse(
            role.getId().toString(),
            role.getName(),
            role.getDescription(),
            role.getCreatedAt(),
            List.of() // Empty permissions list
        );
    }
    
    /**
     * Maps list of Role entities to list of RoleResponse DTOs
     */
    public static List<RoleResponse> toResponseList(List<Role> roles) {
        if (roles == null) {
            return List.of();
        }
        
        return roles.stream()
            .map(RoleMapper::toResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Maps list of Role entities to list of RoleResponse DTOs without permissions
     */
    public static List<RoleResponse> toResponseListWithoutPermissions(List<Role> roles) {
        if (roles == null) {
            return List.of();
        }
        
        return roles.stream()
            .map(RoleMapper::toResponseWithoutPermissions)
            .collect(Collectors.toList());
    }
}