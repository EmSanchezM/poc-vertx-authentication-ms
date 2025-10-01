package com.auth.microservice.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TokenTest {
    
    @Test
    void shouldCreateValidAccessToken() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        Token token = new Token("jwt.access.token", expiresAt, Token.TokenType.ACCESS);
        
        assertEquals("jwt.access.token", token.getValue());
        assertEquals(expiresAt, token.getExpiresAt());
        assertEquals(Token.TokenType.ACCESS, token.getType());
        assertFalse(token.isExpired());
        assertTrue(token.isValid());
    }
    
    @Test
    void shouldCreateValidRefreshToken() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        Token token = new Token("jwt.refresh.token", expiresAt, Token.TokenType.REFRESH);
        
        assertEquals("jwt.refresh.token", token.getValue());
        assertEquals(expiresAt, token.getExpiresAt());
        assertEquals(Token.TokenType.REFRESH, token.getType());
        assertFalse(token.isExpired());
        assertTrue(token.isValid());
    }
    
    @Test
    void shouldTrimTokenValue() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        Token token = new Token("  jwt.token  ", expiresAt, Token.TokenType.ACCESS);
        assertEquals("jwt.token", token.getValue());
    }
    
    @Test
    void shouldRejectNullTokenValue() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        assertThrows(IllegalArgumentException.class, 
            () -> new Token(null, expiresAt, Token.TokenType.ACCESS));
    }
    
    @Test
    void shouldRejectEmptyTokenValue() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        assertThrows(IllegalArgumentException.class, 
            () -> new Token("", expiresAt, Token.TokenType.ACCESS));
    }
    
    @Test
    void shouldRejectNullExpiration() {
        assertThrows(IllegalArgumentException.class, 
            () -> new Token("jwt.token", null, Token.TokenType.ACCESS));
    }
    
    @Test
    void shouldRejectNullTokenType() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        assertThrows(IllegalArgumentException.class, 
            () -> new Token("jwt.token", expiresAt, null));
    }
    
    @Test
    void shouldDetectExpiredToken() {
        LocalDateTime pastTime = LocalDateTime.now().minusHours(1);
        Token token = new Token("jwt.token", pastTime, Token.TokenType.ACCESS);
        
        assertTrue(token.isExpired());
        assertFalse(token.isValid());
    }
    
    @Test
    void shouldDetectValidToken() {
        LocalDateTime futureTime = LocalDateTime.now().plusHours(1);
        Token token = new Token("jwt.token", futureTime, Token.TokenType.ACCESS);
        
        assertFalse(token.isExpired());
        assertTrue(token.isValid());
    }
    
    @Test
    void shouldImplementEqualsAndHashCode() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        Token token1 = new Token("jwt.token", expiresAt, Token.TokenType.ACCESS);
        Token token2 = new Token("jwt.token", expiresAt, Token.TokenType.ACCESS);
        Token token3 = new Token("different.token", expiresAt, Token.TokenType.ACCESS);
        
        assertEquals(token1, token2);
        assertNotEquals(token1, token3);
        assertEquals(token1.hashCode(), token2.hashCode());
    }
    
    @Test
    void shouldImplementToString() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        Token token = new Token("jwt.token", expiresAt, Token.TokenType.ACCESS);
        String toString = token.toString();
        
        assertTrue(toString.contains("ACCESS"));
        assertTrue(toString.contains("expired=false"));
        assertFalse(toString.contains("jwt.token")); // Should not expose token value
    }
}