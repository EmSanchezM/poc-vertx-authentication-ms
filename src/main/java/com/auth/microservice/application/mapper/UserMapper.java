package com.auth.microservice.application.mapper;

import com.auth.microservice.application.dto.request.RegisterRequest;
import com.auth.microservice.application.dto.request.UpdateProfileRequest;
import com.auth.microservice.application.dto.response.UserResponse;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.User;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for User entity and DTOs
 */
public class UserMapper {
    
    private UserMapper() {
        // Utility class
    }
    
    /**
     * Maps RegisterRequest to User domain entity
     */
    public static User toEntity(RegisterRequest request, String passwordHash) {
        if (request == null) {
            return null;
        }
        
        Email email = new Email(request.getEmail());
        return new User(
            request.getUsername(),
            email,
            passwordHash,
            request.getFirstName(),
            request.getLastName()
        );
    }
    
    /**
     * Maps User entity to UserResponse DTO
     */
    public static UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        
        return new UserResponse(
            user.getId().toString(),
            user.getUsername(),
            user.getEmail().getValue(),
            user.getFirstName(),
            user.getLastName(),
            user.getFullName(),
            user.isActive(),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            user.getRoles().stream()
                .map(RoleMapper::toResponse)
                .collect(Collectors.toList())
        );
    }
    
    /**
     * Maps User entity to UserResponse DTO without roles (for performance)
     */
    public static UserResponse toResponseWithoutRoles(User user) {
        if (user == null) {
            return null;
        }
        
        return new UserResponse(
            user.getId().toString(),
            user.getUsername(),
            user.getEmail().getValue(),
            user.getFirstName(),
            user.getLastName(),
            user.getFullName(),
            user.isActive(),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            List.of() // Empty roles list
        );
    }
    
    /**
     * Maps list of User entities to list of UserResponse DTOs
     */
    public static List<UserResponse> toResponseList(List<User> users) {
        if (users == null) {
            return List.of();
        }
        
        return users.stream()
            .map(UserMapper::toResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Updates User entity with data from UpdateProfileRequest
     */
    public static void updateFromRequest(User user, UpdateProfileRequest request) {
        if (user == null || request == null) {
            return;
        }
        
        user.updateProfile(request.getFirstName(), request.getLastName());
    }
}