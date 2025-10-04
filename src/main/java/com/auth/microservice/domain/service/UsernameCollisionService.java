package com.auth.microservice.domain.service;

import com.auth.microservice.domain.exception.UsernameGenerationException;
import io.vertx.core.Future;

/**
 * Service for resolving username collisions by generating unique variants.
 */
public interface UsernameCollisionService {
    
    /**
     * Resolves username collisions by appending numeric suffixes.
     * 
     * @param baseUsername Base username to resolve collisions for
     * @return Future containing unique username
     * @throws UsernameGenerationException if unable to resolve collision after max attempts
     */
    Future<String> resolveCollision(String baseUsername);
    
    /**
     * Generates a username with UUID suffix as fallback when numeric suffixes are exhausted.
     * 
     * @param baseUsername Base username
     * @return Username with UUID suffix
     */
    String generateWithUuidSuffix(String baseUsername);
}