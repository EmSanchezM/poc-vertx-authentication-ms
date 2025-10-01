package com.auth.microservice.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * DTO for user registration request
 */
public class RegisterRequest {
    @JsonProperty("username")
    private String username;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("password")
    private String password;
    
    @JsonProperty("firstName")
    private String firstName;
    
    @JsonProperty("lastName")
    private String lastName;
    
    // Default constructor for JSON deserialization
    public RegisterRequest() {}
    
    public RegisterRequest(String username, String email, String password, String firstName, String lastName) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getFirstName() {
        return firstName;
    }
    
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return lastName;
    }
    
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RegisterRequest that = (RegisterRequest) obj;
        return Objects.equals(username, that.username) &&
               Objects.equals(email, that.email) &&
               Objects.equals(password, that.password) &&
               Objects.equals(firstName, that.firstName) &&
               Objects.equals(lastName, that.lastName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(username, email, password, firstName, lastName);
    }
    
    @Override
    public String toString() {
        return "RegisterRequest{username='" + username + "', email='" + email + 
               "', firstName='" + firstName + "', lastName='" + lastName + "', password='***'}";
    }
}