package com.auth.microservice.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoleTest {
    
    @Test
    void shouldCreateNewRole() {
        Role role = new Role("ADMIN", "Administrator role");
        
        assertNotNull(role.getId());
        assertEquals("ADMIN", role.getName());
        assertEquals("Administrator role", role.getDescription());
        assertNotNull(role.getCreatedAt());
        assertTrue(role.getPermissions().isEmpty());
    }
    
    @Test
    void shouldCreateExistingRoleFromDatabase() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        Role role = new Role(id, "USER", "Regular user role", createdAt);
        
        assertEquals(id, role.getId());
        assertEquals("USER", role.getName());
        assertEquals("Regular user role", role.getDescription());
        assertEquals(createdAt, role.getCreatedAt());
    }
    
    @Test
    void shouldNormalizeRoleName() {
        Role role = new Role("admin", "Administrator role");
        assertEquals("ADMIN", role.getName());
    }
    
    @Test
    void shouldHandleNullDescription() {
        Role role = new Role("ADMIN", null);
        assertEquals("", role.getDescription());
    }
    
    @Test
    void shouldTrimDescription() {
        Role role = new Role("ADMIN", "  Administrator role  ");
        assertEquals("Administrator role", role.getDescription());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "USER", "MODERATOR", "SUPER_ADMIN", "GUEST_USER"})
    void shouldAcceptValidRoleNames(String validName) {
        assertDoesNotThrow(() -> new Role(validName, "Description"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "admin-user", "admin user", "123admin", "admin@user"})
    void shouldRejectInvalidRoleNames(String invalidName) {
        assertThrows(IllegalArgumentException.class, () -> new Role(invalidName, "Description"));
    }
    
    @Test
    void shouldRejectTooLongRoleName() {
        StringBuilder longNameBuilder = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            longNameBuilder.append("A");
        }
        String longName = longNameBuilder.toString();
        assertThrows(IllegalArgumentException.class, () -> new Role(longName, "Description"));
    }
    
    @Test
    void shouldAddAndRemovePermissions() {
        Role role = new Role("ADMIN", "Administrator role");
        Permission readPerm = new Permission("READ_USERS", "users", "read", "Read users");
        Permission writePerm = new Permission("WRITE_USERS", "users", "write", "Write users");
        
        role.addPermission(readPerm);
        assertTrue(role.getPermissions().contains(readPerm));
        
        role.addPermission(writePerm);
        assertEquals(2, role.getPermissions().size());
        
        role.removePermission(readPerm);
        assertFalse(role.getPermissions().contains(readPerm));
        assertTrue(role.getPermissions().contains(writePerm));
    }
    
    @Test
    void shouldAddMultiplePermissions() {
        Role role = new Role("ADMIN", "Administrator role");
        Permission readPerm = new Permission("READ_USERS", "users", "read", "Read users");
        Permission writePerm = new Permission("WRITE_USERS", "users", "write", "Write users");
        
        role.addPermissions(Set.of(readPerm, writePerm));
        assertEquals(2, role.getPermissions().size());
        assertTrue(role.getPermissions().contains(readPerm));
        assertTrue(role.getPermissions().contains(writePerm));
    }
    
    @Test
    void shouldClearPermissions() {
        Role role = new Role("ADMIN", "Administrator role");
        Permission readPerm = new Permission("READ_USERS", "users", "read", "Read users");
        Permission writePerm = new Permission("WRITE_USERS", "users", "write", "Write users");
        
        role.addPermission(readPerm);
        role.addPermission(writePerm);
        assertEquals(2, role.getPermissions().size());
        
        role.clearPermissions();
        assertTrue(role.getPermissions().isEmpty());
    }
    
    @Test
    void shouldCheckPermissionByName() {
        Role role = new Role("ADMIN", "Administrator role");
        Permission readPerm = new Permission("READ_USERS", "users", "read", "Read users");
        
        role.addPermission(readPerm);
        
        assertTrue(role.hasPermission("READ_USERS"));
        assertFalse(role.hasPermission("WRITE_USERS"));
    }
    
    @Test
    void shouldCheckPermissionByResourceAndAction() {
        Role role = new Role("ADMIN", "Administrator role");
        Permission readPerm = new Permission("READ_USERS", "users", "read", "Read users");
        
        role.addPermission(readPerm);
        
        assertTrue(role.hasPermission("users", "read"));
        assertFalse(role.hasPermission("users", "write"));
        assertFalse(role.hasPermission("posts", "read"));
    }
    
    @Test
    void shouldUpdateDescription() {
        Role role = new Role("ADMIN", "Old description");
        role.updateDescription("New description");
        assertEquals("New description", role.getDescription());
    }
    
    @Test
    void shouldRejectNullPermissionOperations() {
        Role role = new Role("ADMIN", "Administrator role");
        
        assertThrows(NullPointerException.class, () -> role.addPermission(null));
        assertThrows(NullPointerException.class, () -> role.removePermission(null));
        assertThrows(NullPointerException.class, () -> role.addPermissions(null));
    }
    
    @Test
    void shouldReturnImmutablePermissionsSet() {
        Role role = new Role("ADMIN", "Administrator role");
        Permission readPerm = new Permission("READ_USERS", "users", "read", "Read users");
        
        role.addPermission(readPerm);
        Set<Permission> permissions = role.getPermissions();
        
        // Should not be able to modify the returned set
        assertThrows(UnsupportedOperationException.class, 
            () -> permissions.add(new Permission("WRITE_USERS", "users", "write", "Write users")));
    }
    
    @Test
    void shouldImplementEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        
        Role role1 = new Role(id, "ADMIN", "Description 1", createdAt);
        Role role2 = new Role(id, "USER", "Description 2", createdAt.plusDays(1));
        Role role3 = new Role(UUID.randomUUID(), "ADMIN", "Description 1", createdAt);
        
        assertEquals(role1, role2); // Same ID
        assertNotEquals(role1, role3); // Different ID
        assertEquals(role1.hashCode(), role2.hashCode());
    }
    
    @Test
    void shouldImplementToString() {
        Role role = new Role("ADMIN", "Administrator role");
        Permission readPerm = new Permission("READ_USERS", "users", "read", "Read users");
        role.addPermission(readPerm);
        
        String toString = role.toString();
        assertTrue(toString.contains("ADMIN"));
        assertTrue(toString.contains("Administrator role"));
        assertTrue(toString.contains("permissions=1"));
    }
}