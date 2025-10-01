package com.auth.microservice.domain.port;

import io.vertx.core.Future;
import java.util.List;
import java.util.Optional;

/**
 * Base repository interface defining common CRUD operations
 * @param <T> Entity type
 * @param <ID> ID type
 */
public interface Repository<T, ID> {
    
    /**
     * Save an entity
     * @param entity Entity to save
     * @return Future containing the saved entity
     */
    Future<T> save(T entity);
    
    /**
     * Find entity by ID
     * @param id Entity ID
     * @return Future containing optional entity
     */
    Future<Optional<T>> findById(ID id);
    
    /**
     * Find all entities
     * @return Future containing list of all entities
     */
    Future<List<T>> findAll();
    
    /**
     * Update an existing entity
     * @param entity Entity to update
     * @return Future containing the updated entity
     */
    Future<T> update(T entity);
    
    /**
     * Delete entity by ID
     * @param id Entity ID
     * @return Future indicating completion
     */
    Future<Void> deleteById(ID id);
    
    /**
     * Check if entity exists by ID
     * @param id Entity ID
     * @return Future containing boolean result
     */
    Future<Boolean> existsById(ID id);
    
    /**
     * Count total number of entities
     * @return Future containing count
     */
    Future<Long> count();
}