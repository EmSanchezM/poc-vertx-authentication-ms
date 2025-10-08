package com.auth.microservice.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {
    
    @Test
    void shouldCreateNewUserWithValidData() {
        Email email = new Email("test@example.com");
        User user = new User("testuser", email, "hashedPassword", "John", "Doe");
        
        assertNotNull(user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals(email, user.getEmail());
        assertEquals("hashedPassword", user.getPasswordHash());
        assertEquals("John", user.getFirstName());
        assertEquals("Doe", user.getLastName());
        assertTrue(user.isActive());
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
        assertTrue(user.getRoles().isEmpty());
    }
    
    @Test
    void shouldCreateExistingUserFromDatabase() {
        UUID id = UUID.randomUUID();
        Email email = new Email("test@example.com");
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);
        OffsetDateTime updatedAt = OffsetDateTime.now();
        
        User user = new User(id, "testuser", email, "hashedPassword", "John", "Doe", 
                           true, createdAt, updatedAt);
        
        assertEquals(id, user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals(email, user.getEmail());
        assertEquals("John Doe", user.getFullName());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"user123", "test_user", "user-name", "user.name"})
    void shouldAcceptValidUsernames(String validUsername) {
        Email email = new Email("test@example.com");
        assertDoesNotThrow(() -> new User(validUsername, email, "hashedPassword", "John", "Doe"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "ab", "user@name", "user name"})
    void shouldRejectInvalidUsernames(String invalidUsername) {
        Email email = new Email("test@example.com");
        assertThrows(IllegalArgumentException.class, 
            () -> new User(invalidUsername, email, "hashedPassword", "John", "Doe"));
    }
    
    @Test
    void shouldRejectTooLongUsername() {
        Email email = new Email("test@example.com");
        String longUsername = "a".repeat(51);
        assertThrows(IllegalArgumentException.class, 
            () -> new User(longUsername, email, "hashedPassword", "John", "Doe"));
    }
    
    @Test
    void shouldRejectNullEmail() {
        assertThrows(NullPointerException.class, 
            () -> new User("testuser", null, "hashedPassword", "John", "Doe"));
    }
    
    @Test
    void shouldRejectInvalidNames() {
        Email email = new Email("test@example.com");
        
        assertThrows(IllegalArgumentException.class, 
            () -> new User("testuser", email, "hashedPassword", "", "Doe"));
        assertThrows(IllegalArgumentException.class, 
            () -> new User("testuser", email, "hashedPassword", "John", ""));
        
        String longName = "a".repeat(101);
        assertThrows(IllegalArgumentException.class, 
            () -> new User("testuser", email, "hashedPassword", longName, "Doe"));
    }
    
    @Test
    void shouldAddAndRemoveRoles() {
        Email email = new Email("test@example.com");
        User user = new User("testuser", email, "hashedPassword", "John", "Doe");
        Role role = new Role("ADMIN", "Administrator role");
        
        user.addRole(role);
        assertTrue(user.getRoles().contains(role));
        
        user.removeRole(role);
        assertFalse(user.getRoles().contains(role));
    }
    
    @Test
    void shouldUpdateProfile() {
        Email email = new Email("test@example.com");
        User user = new User("testuser", email, "hashedPassword", "John", "Doe");
        OffsetDateTime originalUpdatedAt = user.getUpdatedAt();
        
        // Small delay to ensure timestamp difference
        try { Thread.sleep(1); } catch (InterruptedException e) {}
        
        user.updateProfile("Jane", "Smith");
        
        assertEquals("Jane", user.getFirstName());
        assertEquals("Smith", user.getLastName());
        assertEquals("Jane Smith", user.getFullName());
        assertTrue(user.getUpdatedAt().isAfter(originalUpdatedAt));
    }
    
    @Test
    void shouldChangePassword() {
        Email email = new Email("test@example.com");
        User user = new User("testuser", email, "hashedPassword", "John", "Doe");
        
        user.changePassword("newHashedPassword");
        assertEquals("newHashedPassword", user.getPasswordHash());
    }
    
    @Test
    void shouldActivateAndDeactivateUser() {
        Email email = new Email("test@example.com");
        User user = new User("testuser", email, "hashedPassword", "John", "Doe");
        
        assertTrue(user.isActive());
        
        user.deactivate();
        assertFalse(user.isActive());
        
        user.activate();
        assertTrue(user.isActive());
    }
    
    @Test
    void shouldGetAllPermissionsFromRoles() {
        Email email = new Email("test@example.com");
        User user = new User("testuser", email, "hashedPassword", "John", "Doe");
        
        Permission readPerm = new Permission("READ_USERS", "users", "read", "Read users");
        Permission writePerm = new Permission("WRITE_USERS", "users", "write", "Write users");
        
        Role adminRole = new Role("ADMIN", "Admin role");
        adminRole.addPermission(readPerm);
        adminRole.addPermission(writePerm);
        
        Role userRole = new Role("USER", "User role");
        userRole.addPermission(readPerm);
        
        user.addRole(adminRole);
        user.addRole(userRole);
        
        Set<Permission> allPermissions = user.getAllPermissions();
        assertTrue(allPermissions.contains(readPerm));
        assertTrue(allPermissions.contains(writePerm));
        assertEquals(2, allPermissions.size()); // Should not duplicate permissions
    }
    
    @Test
    void shouldCheckPermissions() {
        Email email = new Email("test@example.com");
        User user = new User("testuser", email, "hashedPassword", "John", "Doe");
        
        Permission readPerm = new Permission("READ_USERS", "users", "read", "Read users");
        Role role = new Role("USER", "User role");
        role.addPermission(readPerm);
        user.addRole(role);
        
        assertTrue(user.hasPermission("READ_USERS"));
        assertFalse(user.hasPermission("WRITE_USERS"));
    }
    
    @Test
    void shouldImplementEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        Email email1 = new Email("test1@example.com");
        Email email2 = new Email("test2@example.com");
        
        User user1 = new User(id, "user1", email1, "hash1", "John", "Doe", 
                            true, OffsetDateTime.now(), OffsetDateTime.now());
        User user2 = new User(id, "user2", email2, "hash2", "Jane", "Smith", 
                            false, OffsetDateTime.now(), OffsetDateTime.now());
        User user3 = new User(UUID.randomUUID(), "user3", email1, "hash1", "John", "Doe", 
                            true, OffsetDateTime.now(), OffsetDateTime.now());

        assertEquals(user1, user2); // Same ID
        assertNotEquals(user1, user3); // Different ID
        assertEquals(user1.hashCode(), user2.hashCode());
    }
    
    @Test
    void shouldImplementToString() {
        Email email = new Email("test@example.com");
        User user = new User("testuser", email, "hashedPassword", "John", "Doe");
        String toString = user.toString();
        
        assertTrue(toString.contains("testuser"));
        assertTrue(toString.contains("test@example.com"));
        assertTrue(toString.contains("John Doe"));
        assertTrue(toString.contains("active=true"));
    }
}