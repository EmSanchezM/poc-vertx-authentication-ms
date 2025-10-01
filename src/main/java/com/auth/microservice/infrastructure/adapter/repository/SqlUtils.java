package com.auth.microservice.infrastructure.adapter.repository;

import io.vertx.sqlclient.Row;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Utility class for SQL operations and row mapping
 */
public final class SqlUtils {
    
    private SqlUtils() {
        // Utility class
    }
    
    /**
     * Safely get UUID from row
     * @param row Database row
     * @param columnName Column name
     * @return UUID value or null
     */
    public static UUID getUUID(Row row, String columnName) {
        Object value = row.getValue(columnName);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID) {
            return (UUID) value;
        }
        if (value instanceof String) {
            return UUID.fromString((String) value);
        }
        throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to UUID");
    }
    
    /**
     * Safely get String from row
     * @param row Database row
     * @param columnName Column name
     * @return String value or null
     */
    public static String getString(Row row, String columnName) {
        return row.getString(columnName);
    }
    
    /**
     * Safely get Boolean from row
     * @param row Database row
     * @param columnName Column name
     * @return Boolean value or false if null
     */
    public static Boolean getBoolean(Row row, String columnName) {
        Boolean value = row.getBoolean(columnName);
        return value != null ? value : false;
    }
    
    /**
     * Safely get LocalDateTime from row
     * @param row Database row
     * @param columnName Column name
     * @return LocalDateTime value or null
     */
    public static LocalDateTime getLocalDateTime(Row row, String columnName) {
        return row.getLocalDateTime(columnName);
    }
    
    /**
     * Safely get Integer from row
     * @param row Database row
     * @param columnName Column name
     * @return Integer value or null
     */
    public static Integer getInteger(Row row, String columnName) {
        return row.getInteger(columnName);
    }
    
    /**
     * Safely get Long from row
     * @param row Database row
     * @param columnName Column name
     * @return Long value or null
     */
    public static Long getLong(Row row, String columnName) {
        return row.getLong(columnName);
    }
    
    /**
     * Build IN clause for SQL queries
     * @param paramCount Number of parameters
     * @return IN clause string
     */
    public static String buildInClause(int paramCount) {
        if (paramCount <= 0) {
            return "()";
        }
        
        StringBuilder sb = new StringBuilder("(");
        for (int i = 1; i <= paramCount; i++) {
            sb.append("$").append(i);
            if (i < paramCount) {
                sb.append(", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }
    
    /**
     * Validate and sanitize column name for ORDER BY clause
     * @param columnName Column name to validate
     * @return Sanitized column name
     */
    public static String sanitizeColumnName(String columnName) {
        if (columnName == null || columnName.trim().isEmpty()) {
            throw new IllegalArgumentException("Column name cannot be null or empty");
        }
        
        String sanitized = columnName.trim().toLowerCase();
        
        // Allow only alphanumeric characters and underscores
        if (!sanitized.matches("^[a-z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid column name: " + columnName);
        }
        
        return sanitized;
    }
    
    /**
     * Validate sort direction
     * @param direction Sort direction
     * @return Validated direction (ASC or DESC)
     */
    public static String validateSortDirection(String direction) {
        if (direction == null) {
            return "ASC";
        }
        
        String upperDirection = direction.trim().toUpperCase();
        if ("ASC".equals(upperDirection) || "DESC".equals(upperDirection)) {
            return upperDirection;
        }
        
        throw new IllegalArgumentException("Invalid sort direction: " + direction + ". Must be ASC or DESC");
    }
}