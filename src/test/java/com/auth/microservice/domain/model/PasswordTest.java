package com.auth.microservice.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PasswordTest {
    
    @Test
    void shouldCreateValidPassword() {
        Password password = new Password("Password123!");
        assertEquals("Password123!", password.getValue());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "Password123!",
        "MySecure@Pass1",
        "Complex$Pass9",
        "Strong#123Pwd"
    })
    void shouldAcceptValidPasswords(String validPassword) {
        assertDoesNotThrow(() -> new Password(validPassword));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "short",
        "password", // no uppercase, no digit, no special char
        "PASSWORD", // no lowercase, no digit, no special char
        "Password", // no digit, no special char
        "Password123", // no special char
        "password123!", // no uppercase
        "PASSWORD123!" // no lowercase
    })
    void shouldRejectInvalidPasswords(String invalidPassword) {
        assertThrows(IllegalArgumentException.class, () -> new Password(invalidPassword));
    }
    
    @Test
    void shouldRejectNullPassword() {
        assertThrows(IllegalArgumentException.class, () -> new Password(null));
    }
    
    @Test
    void shouldRejectTooShortPassword() {
        assertThrows(IllegalArgumentException.class, () -> new Password("Pass1!"));
    }
    
    @Test
    void shouldRejectTooLongPassword() {
        StringBuilder longPasswordBuilder = new StringBuilder("Password123!");
        for (int i = 0; i < 120; i++) {
            longPasswordBuilder.append("a");
        }
        String longPassword = longPasswordBuilder.toString();
        assertThrows(IllegalArgumentException.class, () -> new Password(longPassword));
    }
    
    @Test
    void shouldImplementEqualsAndHashCode() {
        Password password1 = new Password("Password123!");
        Password password2 = new Password("Password123!");
        Password password3 = new Password("Different123!");
        
        assertEquals(password1, password2);
        assertNotEquals(password1, password3);
        assertEquals(password1.hashCode(), password2.hashCode());
    }
    
    @Test
    void shouldNotExposePasswordInToString() {
        Password password = new Password("Password123!");
        String toString = password.toString();
        assertFalse(toString.contains("Password123!"));
        assertTrue(toString.contains("Password{***}"));
    }
}