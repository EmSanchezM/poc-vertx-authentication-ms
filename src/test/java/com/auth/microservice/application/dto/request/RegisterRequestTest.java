package com.auth.microservice.application.dto.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegisterRequestTest {
    
    @Test
    void shouldCreateRegisterRequestWithValidData() {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", 
                                                    "password123", "John", "Doe");
        
        assertEquals("testuser", request.getUsername());
        assertEquals("test@example.com", request.getEmail());
        assertEquals("password123", request.getPassword());
        assertEquals("John", request.getFirstName());
        assertEquals("Doe", request.getLastName());
    }
    
    @Test
    void shouldCreateEmptyRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        
        assertNull(request.getUsername());
        assertNull(request.getEmail());
        assertNull(request.getPassword());
        assertNull(request.getFirstName());
        assertNull(request.getLastName());
    }
    
    @Test
    void shouldSetAllFields() {
        RegisterRequest request = new RegisterRequest();
        
        request.setUsername("testuser");
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setFirstName("John");
        request.setLastName("Doe");
        
        assertEquals("testuser", request.getUsername());
        assertEquals("test@example.com", request.getEmail());
        assertEquals("password123", request.getPassword());
        assertEquals("John", request.getFirstName());
        assertEquals("Doe", request.getLastName());
    }
    
    @Test
    void shouldImplementEqualsAndHashCode() {
        RegisterRequest request1 = new RegisterRequest("testuser", "test@example.com", 
                                                     "password123", "John", "Doe");
        RegisterRequest request2 = new RegisterRequest("testuser", "test@example.com", 
                                                     "password123", "John", "Doe");
        RegisterRequest request3 = new RegisterRequest("otheruser", "test@example.com", 
                                                     "password123", "John", "Doe");
        
        assertEquals(request1, request2);
        assertNotEquals(request1, request3);
        assertEquals(request1.hashCode(), request2.hashCode());
    }
    
    @Test
    void shouldNotExposePasswordInToString() {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", 
                                                    "password123", "John", "Doe");
        String toString = request.toString();
        
        assertTrue(toString.contains("testuser"));
        assertTrue(toString.contains("test@example.com"));
        assertTrue(toString.contains("John"));
        assertTrue(toString.contains("Doe"));
        assertFalse(toString.contains("password123"));
        assertTrue(toString.contains("***"));
    }
}