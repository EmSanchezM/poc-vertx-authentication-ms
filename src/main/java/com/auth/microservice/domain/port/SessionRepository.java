package com.auth.microservice.domain.port;

import com.auth.microservice.domain.model.Session;
import io.vertx.core.Future;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Session entity operations
 */
public interface SessionRepository extends Repository<Session, UUID> {
    
    /**
     * Find session by access token hash
     * @param accessTokenHash Access token hash
     * @return Future containing optional session
     */
    Future<Optional<Session>> findByAccessTokenHash(String accessTokenHash);
    
    /**
     * Find session by refresh token hash
     * @param refreshTokenHash Refresh token hash
     * @return Future containing optional session
     */
    Future<Optional<Session>> findByRefreshTokenHash(String refreshTokenHash);
    
    /**
     * Find all active sessions for a user
     * @param userId User ID
     * @return Future containing list of active sessions
     */
    Future<List<Session>> findActiveSessionsByUserId(UUID userId);
    
    /**
     * Find all sessions for a user (active and inactive)
     * @param userId User ID
     * @return Future containing list of all user sessions
     */
    Future<List<Session>> findAllSessionsByUserId(UUID userId);
    
    /**
     * Invalidate all sessions for a user
     * @param userId User ID
     * @return Future indicating completion
     */
    Future<Void> invalidateAllUserSessions(UUID userId);
    
    /**
     * Invalidate session by access token hash
     * @param accessTokenHash Access token hash
     * @return Future indicating completion
     */
    Future<Void> invalidateByAccessTokenHash(String accessTokenHash);
    
    /**
     * Invalidate session by refresh token hash
     * @param refreshTokenHash Refresh token hash
     * @return Future indicating completion
     */
    Future<Void> invalidateByRefreshTokenHash(String refreshTokenHash);
    
    /**
     * Clean up expired sessions
     * @return Future containing number of cleaned sessions
     */
    Future<Integer> cleanupExpiredSessions();
    
    /**
     * Find sessions that will expire within the given time
     * @param expirationTime Time threshold
     * @return Future containing list of sessions expiring soon
     */
    Future<List<Session>> findSessionsExpiringBefore(OffsetDateTime expirationTime);
    
    /**
     * Count active sessions for a user
     * @param userId User ID
     * @return Future containing count of active sessions
     */
    Future<Long> countActiveSessionsByUserId(UUID userId);
    
    /**
     * Count all active sessions in the system
     * @return Future containing total active session count
     */
    Future<Long> countActiveSessions();
    
    /**
     * Count all sessions in the system (active and inactive)
     * @return Future containing total session count
     */
    Future<Long> countTotalSessions();
    
    /**
     * Count sessions created since a specific date
     * @param since Date to count from
     * @return Future containing count of sessions created since the date
     */
    Future<Long> countSessionsCreatedSince(OffsetDateTime since);
}