package com.auth.microservice.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PermissionTest {
    
    @Test
    void shouldCreateNewPermission() {
        Permission permission = new Permission("READ_USERS", "users", "read", "Read users permission");
        
        assertNotNull(permission.getId());
        assertEquals("READ_USERS", permission.getName());
        assertEquals("users", permission.getResource());
        assertEquals("read", permission.getAction());
        assertEquals("Read users permission", permission.getDescription());
        assertEquals("users:read", permission.getFullPermissionName());
    }
    
    @Test
    void shouldCreateExistingPermissionFromDatabase() {
        UUID id = UUID.randomUUID();
        Permission permission = new Permission(id, "WRITE_USERS", "users", "write", "Write users permission");
        
        assertEquals(id, permission.getId());
        assertEquals("WRITE_USERS", permission.getName());
        assertEquals("users", permission.getResource());
        assertEquals("write", permission.getAction());
    }
    
    @Test
    void shouldNormalizePermissionName() {
        Permission permission = new Permission("read_users", "users", "read", "Description");
        assertEquals("READ_USERS", permission.getName());
    }
    
    @Test
    void shouldNormalizeResourceAndAction() {
        Permission permission = new Permission("READ_USERS", "USERS", "READ", "Description");
        assertEquals("users", permission.getResource());
        assertEquals("read", permission.getAction());
    }
    
    @Test
    void shouldHandleNullDescription() {
        Permission permission = new Permission("READ_USERS", "users", "read", null);
        assertEquals("", permission.getDescription());
    }
    
    @Test
    void shouldTrimDescription() {
        Permission permission = new Permission("READ_USERS", "users", "read", "  Description  ");
        assertEquals("Description", permission.getDescription());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"READ_USERS", "WRITE_ADMIN", "DELETE_POSTS", "CREATE_COMMENTS"})
    void shouldAcceptValidPermissionNames(String validName) {
        assertDoesNotThrow(() -> new Permission(validName, "resource", "action", "Description"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "read-users", "read users", "123READ", "read@users"})
    void shouldRejectInvalidPermissionNames(String invalidName) {
        assertThrows(IllegalArgumentException.class, 
            () -> new Permission(invalidName, "resource", "action", "Description"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"users", "posts", "comments", "admin_panel"})
    void shouldAcceptValidResources(String validResource) {
        assertDoesNotThrow(() -> new Permission("READ_RESOURCE", validResource, "read", "Description"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "users-posts", "users posts", "123users", "users@admin"})
    void shouldRejectInvalidResources(String invalidResource) {
        assertThrows(IllegalArgumentException.class, 
            () -> new Permission("READ_RESOURCE", invalidResource, "read", "Description"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"read", "write", "delete", "create", "update"})
    void shouldAcceptValidActions(String validAction) {
        assertDoesNotThrow(() -> new Permission("PERMISSION", "resource", validAction, "Description"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "read-write", "read write", "123read", "read@write"})
    void shouldRejectInvalidActions(String invalidAction) {
        assertThrows(IllegalArgumentException.class, 
            () -> new Permission("PERMISSION", "resource", invalidAction, "Description"));
    }
    
    @Test
    void shouldRejectTooLongFields() {
        StringBuilder longNameBuilder = new StringBuilder();
        StringBuilder longResourceBuilder = new StringBuilder();
        StringBuilder longActionBuilder = new StringBuilder();
        
        for (int i = 0; i < 101; i++) {
            longNameBuilder.append("A");
            longResourceBuilder.append("a");
            if (i < 51) longActionBuilder.append("a");
        }
        
        String longName = longNameBuilder.toString();
        String longResource = longResourceBuilder.toString();
        String longAction = longActionBuilder.toString();
        
        assertThrows(IllegalArgumentException.class, 
            () -> new Permission(longName, "resource", "action", "Description"));
        assertThrows(IllegalArgumentException.class, 
            () -> new Permission("PERMISSION", longResource, "action", "Description"));
        assertThrows(IllegalArgumentException.class, 
            () -> new Permission("PERMISSION", "resource", longAction, "Description"));
    }
    
    @Test
    void shouldMatchResourceAndAction() {
        Permission permission = new Permission("READ_USERS", "users", "read", "Description");
        
        assertTrue(permission.matches("users", "read"));
        assertFalse(permission.matches("users", "write"));
        assertFalse(permission.matches("posts", "read"));
    }
    
    @Test
    void shouldGenerateFullPermissionName() {
        Permission permission = new Permission("READ_USERS", "users", "read", "Description");
        assertEquals("users:read", permission.getFullPermissionName());
    }
    
    @Test
    void shouldImplementEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        Permission permission1 = new Permission(id, "READ_USERS", "users", "read", "Description 1");
        Permission permission2 = new Permission(id, "WRITE_USERS", "posts", "write", "Description 2");
        Permission permission3 = new Permission(UUID.randomUUID(), "READ_USERS", "users", "read", "Description 1");
        
        assertEquals(permission1, permission2); // Same ID
        assertNotEquals(permission1, permission3); // Different ID
        assertEquals(permission1.hashCode(), permission2.hashCode());
    }
    
    @Test
    void shouldImplementToString() {
        Permission permission = new Permission("READ_USERS", "users", "read", "Description");
        String toString = permission.toString();
        
        assertTrue(toString.contains("READ_USERS"));
        assertTrue(toString.contains("users"));
        assertTrue(toString.contains("read"));
    }
}