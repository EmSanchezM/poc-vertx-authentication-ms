package com.auth.microservice.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class EmailTest {
    
    @Test
    void shouldCreateValidEmail() {
        Email email = new Email("test@example.com");
        assertEquals("test@example.com", email.getValue());
    }
    
    @Test
    void shouldNormalizeEmailToLowercase() {
        Email email = new Email("TEST@EXAMPLE.COM");
        assertEquals("test@example.com", email.getValue());
    }
    
    @Test
    void shouldTrimWhitespace() {
        Email email = new Email("  test@example.com  ");
        assertEquals("test@example.com", email.getValue());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "user@domain.com",
        "user.name@domain.com",
        "user+tag@domain.co.uk",
        "user123@domain123.org",
        "a@b.co"
    })
    void shouldAcceptValidEmailFormats(String validEmail) {
        assertDoesNotThrow(() -> new Email(validEmail));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "invalid-email",
        "@domain.com",
        "user@",
        "user@domain",
        "user.domain.com",
        "user@domain.",
        "user@.domain.com"
    })
    void shouldRejectInvalidEmailFormats(String invalidEmail) {
        assertThrows(IllegalArgumentException.class, () -> new Email(invalidEmail));
    }
    
    @Test
    void shouldRejectNullEmail() {
        assertThrows(IllegalArgumentException.class, () -> new Email(null));
    }
    
    @Test
    void shouldRejectTooLongEmail() {
        StringBuilder longEmailBuilder = new StringBuilder();
        for (int i = 0; i < 250; i++) {
            longEmailBuilder.append("a");
        }
        String longEmail = longEmailBuilder.toString() + "@example.com";
        assertThrows(IllegalArgumentException.class, () -> new Email(longEmail));
    }
    
    @Test
    void shouldImplementEqualsAndHashCode() {
        Email email1 = new Email("test@example.com");
        Email email2 = new Email("TEST@EXAMPLE.COM");
        Email email3 = new Email("other@example.com");
        
        assertEquals(email1, email2);
        assertNotEquals(email1, email3);
        assertEquals(email1.hashCode(), email2.hashCode());
    }
    
    @Test
    void shouldImplementToString() {
        Email email = new Email("test@example.com");
        assertEquals("test@example.com", email.toString());
    }
}