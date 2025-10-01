package com.auth.microservice.application.dto.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginRequestTest {
    
    @Test
    void shouldCreateLoginRequestWithValidData() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        
        assertEquals("test@example.com", request.getEmail());
        assertEquals("password123", request.getPassword());
    }
    
    @Test
    void shouldCreateEmptyLoginRequest() {
        LoginRequest request = new LoginRequest();
        
        assertNull(request.getEmail());
        assertNull(request.getPassword());
    }
    
    @Test
    void shouldSetEmailAndPassword() {
        LoginRequest request = new LoginRequest();
        
        request.setEmail("test@example.com");
        request.setPassword("password123");
        
        assertEquals("test@example.com", request.getEmail());
        assertEquals("password123", request.getPassword());
    }
    
    @Test
    void shouldImplementEqualsAndHashCode() {
        LoginRequest request1 = new LoginRequest("test@example.com", "password123");
        LoginRequest request2 = new LoginRequest("test@example.com", "password123");
        LoginRequest request3 = new LoginRequest("other@example.com", "password123");
        
        assertEquals(request1, request2);
        assertNotEquals(request1, request3);
        assertEquals(request1.hashCode(), request2.hashCode());
    }
    
    @Test
    void shouldNotExposePasswordInToString() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        String toString = request.toString();
        
        assertTrue(toString.contains("test@example.com"));
        assertFalse(toString.contains("password123"));
        assertTrue(toString.contains("***"));
    }
}