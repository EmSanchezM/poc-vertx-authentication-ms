package com.auth.microservice.domain.port;

import java.util.Objects;

/**
 * Pagination parameters for repository queries
 */
public class Pagination {
    private final int page;
    private final int size;
    private final String sortBy;
    private final SortDirection direction;
    
    public enum SortDirection {
        ASC, DESC
    }
    
    public Pagination(int page, int size) {
        this(page, size, "id", SortDirection.ASC);
    }
    
    public Pagination(int page, int size, String sortBy, SortDirection direction) {
        if (page < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Page size must be positive");
        }
        if (size > 1000) {
            throw new IllegalArgumentException("Page size cannot exceed 1000");
        }
        
        this.page = page;
        this.size = size;
        this.sortBy = Objects.requireNonNull(sortBy, "Sort field cannot be null");
        this.direction = Objects.requireNonNull(direction, "Sort direction cannot be null");
    }
    
    public int getPage() {
        return page;
    }
    
    public int getSize() {
        return size;
    }
    
    public String getSortBy() {
        return sortBy;
    }
    
    public SortDirection getDirection() {
        return direction;
    }
    
    public int getOffset() {
        return page * size;
    }
    
    public static Pagination of(int page, int size) {
        return new Pagination(page, size);
    }
    
    public static Pagination of(int page, int size, String sortBy, SortDirection direction) {
        return new Pagination(page, size, sortBy, direction);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Pagination that = (Pagination) obj;
        return page == that.page && size == that.size && 
               Objects.equals(sortBy, that.sortBy) && direction == that.direction;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(page, size, sortBy, direction);
    }
    
    @Override
    public String toString() {
        return "Pagination{page=" + page + ", size=" + size + 
               ", sortBy='" + sortBy + "', direction=" + direction + "}";
    }
}