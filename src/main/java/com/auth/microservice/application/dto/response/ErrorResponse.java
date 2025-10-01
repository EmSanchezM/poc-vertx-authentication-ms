package com.auth.microservice.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * DTO for error response
 */
public class ErrorResponse {
    @JsonProperty("error")
    private ErrorDetail error;
    
    // Default constructor for JSON serialization
    public ErrorResponse() {}
    
    public ErrorResponse(ErrorDetail error) {
        this.error = error;
    }
    
    public ErrorResponse(String code, String message, String path) {
        this.error = new ErrorDetail(code, message, LocalDateTime.now(), path);
    }
    
    public ErrorDetail getError() {
        return error;
    }
    
    public void setError(ErrorDetail error) {
        this.error = error;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ErrorResponse that = (ErrorResponse) obj;
        return Objects.equals(error, that.error);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(error);
    }
    
    @Override
    public String toString() {
        return "ErrorResponse{error=" + error + "}";
    }
    
    public static class ErrorDetail {
        @JsonProperty("code")
        private String code;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("timestamp")
        private LocalDateTime timestamp;
        
        @JsonProperty("path")
        private String path;
        
        // Default constructor for JSON serialization
        public ErrorDetail() {}
        
        public ErrorDetail(String code, String message, LocalDateTime timestamp, String path) {
            this.code = code;
            this.message = message;
            this.timestamp = timestamp;
            this.path = path;
        }
        
        public String getCode() {
            return code;
        }
        
        public void setCode(String code) {
            this.code = code;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
        
        public String getPath() {
            return path;
        }
        
        public void setPath(String path) {
            this.path = path;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ErrorDetail that = (ErrorDetail) obj;
            return Objects.equals(code, that.code) &&
                   Objects.equals(message, that.message) &&
                   Objects.equals(timestamp, that.timestamp) &&
                   Objects.equals(path, that.path);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(code, message, timestamp, path);
        }
        
        @Override
        public String toString() {
            return "ErrorDetail{code='" + code + "', message='" + message + 
                   "', timestamp=" + timestamp + ", path='" + path + "'}";
        }
    }
}