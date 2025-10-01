package com.auth.microservice.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * DTO for update profile request
 */
public class UpdateProfileRequest {
    @JsonProperty("firstName")
    private String firstName;
    
    @JsonProperty("lastName")
    private String lastName;
    
    // Default constructor for JSON deserialization
    public UpdateProfileRequest() {}
    
    public UpdateProfileRequest(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
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
        UpdateProfileRequest that = (UpdateProfileRequest) obj;
        return Objects.equals(firstName, that.firstName) && Objects.equals(lastName, that.lastName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(firstName, lastName);
    }
    
    @Override
    public String toString() {
        return "UpdateProfileRequest{firstName='" + firstName + "', lastName='" + lastName + "'}";
    }
}