package com.auth.microservice.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionTest {
    
    @Test
    void shouldCreateNewSession() {
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        Session session = new Session(userId, "accessTokenHash", "refreshTokenHash", 
                                    expiresAt, "192.168.1.1", "Mozilla/5.0");
        
        assertNotNull(session.getId());
        assertEquals(userId, session.getUserId());
        assertEquals("accessTokenHash", session.getAccessTokenHash());
        assertEquals("refreshTokenHash", session.getRefreshTokenHash());
        assertEquals(expiresAt, session.getExpiresAt());
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getLastUsedAt());
        assertEquals("192.168.1.1", session.getIpAddress());
        assertEquals("Mozilla/5.0", session.getUserAgent());
        assertTrue(session.isActive());
        assertFalse(session.isExpired());
        assertTrue(session.isValid());
    }
    
    @Test
    void shouldCreateExistingSessionFromDatabase() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(30);
        LocalDateTime lastUsedAt = LocalDateTime.now().minusMinutes(5);
        
        Session session = new Session(id, userId, "accessTokenHash", "refreshTokenHash",
                                    expiresAt, createdAt, lastUsedAt, "192.168.1.1", 
                                    "Mozilla/5.0", true);
        
        assertEquals(id, session.getId());
        assertEquals(userId, session.getUserId());
        assertEquals(createdAt, session.getCreatedAt());
        assertEquals(lastUsedAt, session.getLastUsedAt());
    }
    
    @Test
    void shouldTrimTokenHashes() {
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        Session session = new Session(userId, "  accessTokenHash  ", "  refreshTokenHash  ", 
                                    expiresAt, "192.168.1.1", "Mozilla/5.0");
        
        assertEquals("accessTokenHash", session.getAccessTokenHash());
        assertEquals("refreshTokenHash", session.getRefreshTokenHash());
    }
    
    @Test
    void shouldHandleNullUserAgent() {
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        Session session = new Session(userId, "accessTokenHash", "refreshTokenHash", 
                                    expiresAt, "192.168.1.1", null);
        
        assertEquals("", session.getUserAgent());
    }
    
    @Test
    void shouldTrimUserAgent() {
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        Session session = new Session(userId, "accessTokenHash", "refreshTokenHash", 
                                    expiresAt, "192.168.1.1", "  Mozilla/5.0  ");
        
        assertEquals("Mozilla/5.0", session.getUserAgent());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"192.168.1.1", "10.0.0.1", "2001:0db8:85a3:0000:0000:8a2e:0370:7334", "unknown"})
    void shouldAcceptValidIpAddresses(String validIp) {
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        assertDoesNotThrow(() -> new Session(userId, "accessTokenHash", "refreshTokenHash", 
                                           expiresAt, validIp, "Mozilla/5.0"));
    }
    
    @Test
    void shouldHandleNullIpAddress() {
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        Session session = new Session(userId, "accessTokenHash", "refreshTokenHash", 
                                    expiresAt, null, "Mozilla/5.0");
        
        assertEquals("unknown", session.getIpAddress());
    }
    
    @Test
    void shouldRejectInvalidIpAddress() {
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        assertThrows(IllegalArgumentException.class, 
            () -> new Session(userId, "accessTokenHash", "refreshTokenHash", 
                            expiresAt, "invalid.ip.address", "Mozilla/5.0"));
    }
    
    @Test
    void shouldRejectNullRequiredFields() {
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        assertThrows(NullPointerException.class, 
            () -> new Session(null, "accessTokenHash", "refreshTokenHash", 
                            expiresAt, "192.168.1.1", "Mozilla/5.0"));
        
        assertThrows(IllegalArgumentException.class, 
            () -> new Session(userId, null, "refreshTokenHash", 
                            expiresAt, "192.168.1.1", "Mozilla/5.0"));
        
        assertThrows(IllegalArgumentException.class, 
            () -> new Session(userId, "accessTokenHash", null, 
                            expiresAt, "192.168.1.1", "Mozilla/5.0"));
        
        assertThrows(NullPointerException.class, 
            () -> new Session(userId, "accessTokenHash", "refreshTokenHash", 
                            null, "192.168.1.1", "Mozilla/5.0"));
    }
    
    @Test
    void shouldRejectPastExpirationTime() {
        UUID userId = UUID.randomUUID();
        LocalDateTime pastTime = LocalDateTime.now().minusHours(1);
        
        assertThrows(IllegalArgumentException.class, 
            () -> new Session(userId, "accessTokenHash", "refreshTokenHash", 
                            pastTime, "192.168.1.1", "Mozilla/5.0"));
    }
    
    @Test
    void shouldUpdateLastUsed() {
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        Session session = new Session(userId, "accessTokenHash", "refreshTokenHash", 
                                    expiresAt, "192.168.1.1", "Mozilla/5.0");
        
        LocalDateTime originalLastUsed = session.getLastUsedAt();
        
        // Small delay to ensure timestamp difference
        try { Thread.sleep(1); } catch (InterruptedException e) {}
        
        session.updateLastUsed();
        assertTrue(session.getLastUsedAt().isAfter(originalLastUsed));
    }
    
    @Test
    void shouldInvalidateAndActivateSession() {
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        Session session = new Session(userId, "accessTokenHash", "refreshTokenHash", 
                                    expiresAt, "192.168.1.1", "Mozilla/5.0");
        
        assertTrue(session.isActive());
        assertTrue(session.isValid());
        
        session.invalidate();
        assertFalse(session.isActive());
        assertFalse(session.isValid());
        
        session.activate();
        assertTrue(session.isActive());
        assertTrue(session.isValid());
    }
    
    @Test
    void shouldDetectExpiredSession() {
        UUID userId = UUID.randomUUID();
        LocalDateTime pastTime = LocalDateTime.now().minusHours(1);
        
        // Create with existing constructor to bypass validation
        Session session = new Session(UUID.randomUUID(), userId, "accessTokenHash", "refreshTokenHash",
                                    pastTime, LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1),
                                    "192.168.1.1", "Mozilla/5.0", true);
        
        assertTrue(session.isExpired());
        assertFalse(session.isValid());
    }
    
    @Test
    void shouldUpdateTokens() {
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        Session session = new Session(userId, "oldAccessToken", "oldRefreshToken", 
                                    expiresAt, "192.168.1.1", "Mozilla/5.0");
        
        LocalDateTime newExpiresAt = LocalDateTime.now().plusHours(2);
        LocalDateTime originalLastUsed = session.getLastUsedAt();
        
        // Small delay to ensure timestamp difference
        try { Thread.sleep(1); } catch (InterruptedException e) {}
        
        session.updateTokens("newAccessToken", "newRefreshToken", newExpiresAt);
        
        assertEquals("newAccessToken", session.getAccessTokenHash());
        assertEquals("newRefreshToken", session.getRefreshTokenHash());
        assertEquals(newExpiresAt, session.getExpiresAt());
        assertTrue(session.getLastUsedAt().isAfter(originalLastUsed));
    }
    
    @Test
    void shouldRejectInvalidTokenUpdate() {
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        Session session = new Session(userId, "accessTokenHash", "refreshTokenHash", 
                                    expiresAt, "192.168.1.1", "Mozilla/5.0");
        
        LocalDateTime pastTime = LocalDateTime.now().minusHours(1);
        
        assertThrows(IllegalArgumentException.class, 
            () -> session.updateTokens("newAccessToken", "newRefreshToken", pastTime));
    }
    
    @Test
    void shouldImplementEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime lastUsedAt = LocalDateTime.now();
        
        Session session1 = new Session(id, userId1, "token1", "refresh1", expiresAt, 
                                     createdAt, lastUsedAt, "192.168.1.1", "Mozilla/5.0", true);
        Session session2 = new Session(id, userId2, "token2", "refresh2", expiresAt, 
                                     createdAt, lastUsedAt, "192.168.1.2", "Chrome", false);
        Session session3 = new Session(UUID.randomUUID(), userId1, "token1", "refresh1", expiresAt, 
                                     createdAt, lastUsedAt, "192.168.1.1", "Mozilla/5.0", true);
        
        assertEquals(session1, session2); // Same ID
        assertNotEquals(session1, session3); // Different ID
        assertEquals(session1.hashCode(), session2.hashCode());
    }
    
    @Test
    void shouldImplementToString() {
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        Session session = new Session(userId, "accessTokenHash", "refreshTokenHash", 
                                    expiresAt, "192.168.1.1", "Mozilla/5.0");
        
        String toString = session.toString();
        assertTrue(toString.contains(userId.toString()));
        assertTrue(toString.contains("active=true"));
        assertTrue(toString.contains("expired=false"));
        assertTrue(toString.contains("192.168.1.1"));
    }
}