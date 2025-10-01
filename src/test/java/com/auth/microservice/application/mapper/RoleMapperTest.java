package com.auth.microservice.application.mapper;

import com.auth.microservice.application.dto.request.CreateRoleRequest;
import com.auth.microservice.application.dto.response.RoleResponse;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoleMapperTest {
    
    @Test
    void shouldMapCreateRequestToEntity() {
        CreateRoleRequest request = new CreateRoleRequest("ADMIN", "Administrator role", 
                                                        List.of("perm1", "perm2"));
        
        Role role = RoleMapper.toEntity(request);
        
        assertNotNull(role);
        assertEquals("ADMIN", role.getName());
        assertEquals("Administrator role", role.getDescription());
        assertNotNull(role.getId());
        assertNotNull(role.getCreatedAt());
        assertTrue(role.getPermissions().isEmpty()); // Permissions are added separately
    }
    
    @Test
    void shouldReturnNullWhenCreateRequestIsNull() {
        Role role = RoleMapper.toEntity(null);
        assertNull(role);
    }
    
    @Test
    void shouldMapEntityToResponse() {
        Role role = new Role("ADMIN", "Administrator role");
        
        // Add permissions
        Permission permission1 = new Permission("READ_USERS", "users", "read", "Read users");
        Permission permission2 = new Permission("WRITE_USERS", "users", "write", "Write users");
        role.addPermission(permission1);
        role.addPermission(permission2);
        
        RoleResponse response = RoleMapper.toResponse(role);
        
        assertNotNull(response);
        assertEquals(role.getId().toString(), response.getId());
        assertEquals("ADMIN", response.getName());
        assertEquals("Administrator role", response.getDescription());
        assertEquals(role.getCreatedAt(), response.getCreatedAt());
        assertEquals(2, response.getPermissions().size());
    }
    
    @Test
    void shouldMapEntityToResponseWithoutPermissions() {
        Role role = new Role("ADMIN", "Administrator role");
        
        // Add permissions
        Permission permission = new Permission("READ_USERS", "users", "read", "Read users");
        role.addPermission(permission);
        
        RoleResponse response = RoleMapper.toResponseWithoutPermissions(role);
        
        assertNotNull(response);
        assertEquals(role.getId().toString(), response.getId());
        assertEquals("ADMIN", response.getName());
        assertEquals("Administrator role", response.getDescription());
        assertEquals(role.getCreatedAt(), response.getCreatedAt());
        assertTrue(response.getPermissions().isEmpty());
    }
    
    @Test
    void shouldReturnNullWhenEntityIsNull() {
        RoleResponse response = RoleMapper.toResponse(null);
        assertNull(response);
        
        RoleResponse responseWithoutPermissions = RoleMapper.toResponseWithoutPermissions(null);
        assertNull(responseWithoutPermissions);
    }
    
    @Test
    void shouldMapEntityListToResponseList() {
        Role role1 = new Role("ADMIN", "Administrator role");
        Role role2 = new Role("USER", "User role");
        
        List<Role> roles = List.of(role1, role2);
        List<RoleResponse> responses = RoleMapper.toResponseList(roles);
        
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("ADMIN", responses.get(0).getName());
        assertEquals("USER", responses.get(1).getName());
    }
    
    @Test
    void shouldMapEntityListToResponseListWithoutPermissions() {
        Role role1 = new Role("ADMIN", "Administrator role");
        Role role2 = new Role("USER", "User role");
        
        // Add permissions to verify they are excluded
        Permission permission = new Permission("READ_USERS", "users", "read", "Read users");
        role1.addPermission(permission);
        role2.addPermission(permission);
        
        List<Role> roles = List.of(role1, role2);
        List<RoleResponse> responses = RoleMapper.toResponseListWithoutPermissions(roles);
        
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("ADMIN", responses.get(0).getName());
        assertEquals("USER", responses.get(1).getName());
        assertTrue(responses.get(0).getPermissions().isEmpty());
        assertTrue(responses.get(1).getPermissions().isEmpty());
    }
    
    @Test
    void shouldReturnEmptyListWhenEntityListIsNull() {
        List<RoleResponse> responses = RoleMapper.toResponseList(null);
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
        
        List<RoleResponse> responsesWithoutPermissions = RoleMapper.toResponseListWithoutPermissions(null);
        assertNotNull(responsesWithoutPermissions);
        assertTrue(responsesWithoutPermissions.isEmpty());
    }
}