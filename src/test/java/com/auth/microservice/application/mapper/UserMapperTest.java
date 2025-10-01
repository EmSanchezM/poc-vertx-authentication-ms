package com.auth.microservice.application.mapper;

import com.auth.microservice.application.dto.request.RegisterRequest;
import com.auth.microservice.application.dto.request.UpdateProfileRequest;
import com.auth.microservice.application.dto.response.UserResponse;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserMapperTest {
    
    @Test
    void shouldMapRegisterRequestToEntity() {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", 
                                                    "password123", "John", "Doe");
        String passwordHash = "hashedPassword";
        
        User user = UserMapper.toEntity(request, passwordHash);
        
        assertNotNull(user);
        assertEquals("testuser", user.getUsername());
        assertEquals("test@example.com", user.getEmail().getValue());
        assertEquals("hashedPassword", user.getPasswordHash());
        assertEquals("John", user.getFirstName());
        assertEquals("Doe", user.getLastName());
        assertTrue(user.isActive());
        assertNotNull(user.getId());
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
    }
    
    @Test
    void shouldReturnNullWhenRegisterRequestIsNull() {
        User user = UserMapper.toEntity(null, "hashedPassword");
        assertNull(user);
    }
    
    @Test
    void shouldMapEntityToResponse() {
        Email email = new Email("test@example.com");
        User user = new User("testuser", email, "hashedPassword", "John", "Doe");
        
        // Add a role with permission
        Permission permission = new Permission("READ_USERS", "users", "read", "Read users");
        Role role = new Role("USER", "User role");
        role.addPermission(permission);
        user.addRole(role);
        
        UserResponse response = UserMapper.toResponse(user);
        
        assertNotNull(response);
        assertEquals(user.getId().toString(), response.getId());
        assertEquals("testuser", response.getUsername());
        assertEquals("test@example.com", response.getEmail());
        assertEquals("John", response.getFirstName());
        assertEquals("Doe", response.getLastName());
        assertEquals("John Doe", response.getFullName());
        assertTrue(response.isActive());
        assertEquals(user.getCreatedAt(), response.getCreatedAt());
        assertEquals(user.getUpdatedAt(), response.getUpdatedAt());
        assertEquals(1, response.getRoles().size());
        assertEquals("USER", response.getRoles().get(0).getName());
    }
    
    @Test
    void shouldMapEntityToResponseWithoutRoles() {
        Email email = new Email("test@example.com");
        User user = new User("testuser", email, "hashedPassword", "John", "Doe");
        
        UserResponse response = UserMapper.toResponseWithoutRoles(user);
        
        assertNotNull(response);
        assertEquals(user.getId().toString(), response.getId());
        assertEquals("testuser", response.getUsername());
        assertEquals("test@example.com", response.getEmail());
        assertEquals("John", response.getFirstName());
        assertEquals("Doe", response.getLastName());
        assertTrue(response.getRoles().isEmpty());
    }
    
    @Test
    void shouldReturnNullWhenEntityIsNull() {
        UserResponse response = UserMapper.toResponse(null);
        assertNull(response);
        
        UserResponse responseWithoutRoles = UserMapper.toResponseWithoutRoles(null);
        assertNull(responseWithoutRoles);
    }
    
    @Test
    void shouldMapEntityListToResponseList() {
        Email email1 = new Email("test1@example.com");
        Email email2 = new Email("test2@example.com");
        User user1 = new User("user1", email1, "hash1", "John", "Doe");
        User user2 = new User("user2", email2, "hash2", "Jane", "Smith");
        
        List<User> users = List.of(user1, user2);
        List<UserResponse> responses = UserMapper.toResponseList(users);
        
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("user1", responses.get(0).getUsername());
        assertEquals("user2", responses.get(1).getUsername());
    }
    
    @Test
    void shouldReturnEmptyListWhenEntityListIsNull() {
        List<UserResponse> responses = UserMapper.toResponseList(null);
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }
    
    @Test
    void shouldUpdateEntityFromRequest() {
        Email email = new Email("test@example.com");
        User user = new User("testuser", email, "hashedPassword", "John", "Doe");
        LocalDateTime originalUpdatedAt = user.getUpdatedAt();
        
        UpdateProfileRequest request = new UpdateProfileRequest("Jane", "Smith");
        
        // Small delay to ensure timestamp difference
        try { Thread.sleep(1); } catch (InterruptedException e) {}
        
        UserMapper.updateFromRequest(user, request);
        
        assertEquals("Jane", user.getFirstName());
        assertEquals("Smith", user.getLastName());
        assertEquals("Jane Smith", user.getFullName());
        assertTrue(user.getUpdatedAt().isAfter(originalUpdatedAt));
    }
    
    @Test
    void shouldHandleNullParametersInUpdate() {
        Email email = new Email("test@example.com");
        User user = new User("testuser", email, "hashedPassword", "John", "Doe");
        String originalFirstName = user.getFirstName();
        String originalLastName = user.getLastName();
        
        // Should not throw exception and not modify user
        UserMapper.updateFromRequest(null, new UpdateProfileRequest("Jane", "Smith"));
        UserMapper.updateFromRequest(user, null);
        
        assertEquals(originalFirstName, user.getFirstName());
        assertEquals(originalLastName, user.getLastName());
    }
}