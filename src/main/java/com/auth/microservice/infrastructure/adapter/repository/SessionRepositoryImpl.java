package com.auth.microservice.infrastructure.adapter.repository;

import com.auth.microservice.domain.model.Session;
import com.auth.microservice.domain.port.SessionRepository;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL implementation of SessionRepository
 */
public class SessionRepositoryImpl extends AbstractRepository<Session, UUID> implements SessionRepository {
    
    private static final String TABLE_NAME = "sessions";
    private static final String ID_COLUMN = "id";
    
    private static final String INSERT_SQL = """
        INSERT INTO sessions (id, user_id, access_token_hash, refresh_token_hash, expires_at, 
                             created_at, last_used_at, ip_address, user_agent, country_code, is_active)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
        RETURNING *
        """;
    
    private static final String UPDATE_SQL = """
        UPDATE sessions 
        SET access_token_hash = $2, refresh_token_hash = $3, expires_at = $4, 
            last_used_at = $5, is_active = $6
        WHERE id = $1
        RETURNING *
        """;
    
    private static final String FIND_BY_ACCESS_TOKEN_HASH_SQL = """
        SELECT * FROM sessions WHERE access_token_hash = $1 AND is_active = true
        """;
    
    private static final String FIND_BY_REFRESH_TOKEN_HASH_SQL = """
        SELECT * FROM sessions WHERE refresh_token_hash = $1 AND is_active = true
        """;
    
    private static final String FIND_ACTIVE_SESSIONS_BY_USER_ID_SQL = """
        SELECT * FROM sessions 
        WHERE user_id = $1 AND is_active = true AND expires_at > NOW()
        ORDER BY last_used_at DESC
        """;
    
    private static final String FIND_ALL_SESSIONS_BY_USER_ID_SQL = """
        SELECT * FROM sessions 
        WHERE user_id = $1
        ORDER BY created_at DESC
        """;
    
    private static final String INVALIDATE_ALL_USER_SESSIONS_SQL = """
        UPDATE sessions 
        SET is_active = false 
        WHERE user_id = $1 AND is_active = true
        """;
    
    private static final String INVALIDATE_BY_ACCESS_TOKEN_HASH_SQL = """
        UPDATE sessions 
        SET is_active = false 
        WHERE access_token_hash = $1
        """;
    
    private static final String INVALIDATE_BY_REFRESH_TOKEN_HASH_SQL = """
        UPDATE sessions 
        SET is_active = false 
        WHERE refresh_token_hash = $1
        """;
    
    private static final String CLEANUP_EXPIRED_SESSIONS_SQL = """
        DELETE FROM sessions 
        WHERE expires_at < NOW() OR (is_active = false AND created_at < NOW() - INTERVAL '30 days')
        """;
    
    private static final String FIND_SESSIONS_EXPIRING_BEFORE_SQL = """
        SELECT * FROM sessions 
        WHERE expires_at < $1 AND is_active = true
        ORDER BY expires_at ASC
        """;
    
    private static final String COUNT_ACTIVE_SESSIONS_BY_USER_ID_SQL = """
        SELECT COUNT(*) FROM sessions 
        WHERE user_id = $1 AND is_active = true AND expires_at > NOW()
        """;
    
    public SessionRepositoryImpl(Pool pool, TransactionManager transactionManager) {
        super(pool, transactionManager);
    }
    
    @Override
    protected String getTableName() {
        return TABLE_NAME;
    }
    
    @Override
    protected String getIdColumnName() {
        return ID_COLUMN;
    }
    
    @Override
    protected Session mapRowToEntity(Row row) {
        UUID id = SqlUtils.getUUID(row, "id");
        UUID userId = SqlUtils.getUUID(row, "user_id");
        String accessTokenHash = SqlUtils.getString(row, "access_token_hash");
        String refreshTokenHash = SqlUtils.getString(row, "refresh_token_hash");
        OffsetDateTime expiresAt = SqlUtils.getOffsetDateTime(row, "expires_at");
        OffsetDateTime createdAt = SqlUtils.getOffsetDateTime(row, "created_at");
        OffsetDateTime lastUsedAt = SqlUtils.getOffsetDateTime(row, "last_used_at");
        String ipAddress = SqlUtils.getString(row, "ip_address");
        String userAgent = SqlUtils.getString(row, "user_agent");
        String countryCode = SqlUtils.getString(row, "country_code");
        Boolean isActive = SqlUtils.getBoolean(row, "is_active");
        
        return new Session(id, userId, accessTokenHash, refreshTokenHash, expiresAt,
                          createdAt, lastUsedAt, ipAddress, userAgent, countryCode, isActive);
    }
    
    @Override
    protected String getInsertSql() {
        return INSERT_SQL;
    }
    
    @Override
    protected String getUpdateSql() {
        return UPDATE_SQL;
    }
    
    @Override
    protected Tuple createInsertTuple(Session session) {
        return Tuple.of(
            session.getId(),
            session.getUserId(),
            session.getAccessTokenHash(),
            session.getRefreshTokenHash(),
            session.getExpiresAt(),
            session.getCreatedAt(),
            session.getLastUsedAt(),
            session.getIpAddress(),
            session.getUserAgent(),
            session.getCountryCode(),
            session.isActive()
        );
    }
    
    @Override
    protected Tuple createUpdateTuple(Session session) {
        return Tuple.of(
            session.getId(),
            session.getAccessTokenHash(),
            session.getRefreshTokenHash(),
            session.getExpiresAt(),
            session.getLastUsedAt(),
            session.isActive()
        );
    }
    
    @Override
    protected UUID extractId(Session session) {
        return session.getId();
    }
    
    @Override
    public Future<Optional<Session>> findByAccessTokenHash(String accessTokenHash) {
        return executeQuery(FIND_BY_ACCESS_TOKEN_HASH_SQL, Tuple.of(accessTokenHash))
            .map(rows -> {
                if (rows.size() == 0) {
                    return Optional.<Session>empty();
                }
                return Optional.of(mapRowToEntity(rows.iterator().next()));
            })
            .onFailure(error -> logger.error("Failed to find session by access token hash", error));
    }
    
    @Override
    public Future<Optional<Session>> findByRefreshTokenHash(String refreshTokenHash) {
        return executeQuery(FIND_BY_REFRESH_TOKEN_HASH_SQL, Tuple.of(refreshTokenHash))
            .map(rows -> {
                if (rows.size() == 0) {
                    return Optional.<Session>empty();
                }
                return Optional.of(mapRowToEntity(rows.iterator().next()));
            })
            .onFailure(error -> logger.error("Failed to find session by refresh token hash", error));
    }
    
    @Override
    public Future<List<Session>> findActiveSessionsByUserId(UUID userId) {
        return executeQuery(FIND_ACTIVE_SESSIONS_BY_USER_ID_SQL, Tuple.of(userId))
            .map(this::mapRowsToEntities)
            .onFailure(error -> logger.error("Failed to find active sessions by user ID: {}", userId, error));
    }
    
    @Override
    public Future<List<Session>> findAllSessionsByUserId(UUID userId) {
        return executeQuery(FIND_ALL_SESSIONS_BY_USER_ID_SQL, Tuple.of(userId))
            .map(this::mapRowsToEntities)
            .onFailure(error -> logger.error("Failed to find all sessions by user ID: {}", userId, error));
    }
    
    @Override
    public Future<Void> invalidateAllUserSessions(UUID userId) {
        return executeQuery(INVALIDATE_ALL_USER_SESSIONS_SQL, Tuple.of(userId))
            .map(rows -> {
                logger.info("Invalidated {} sessions for user: {}", rows.rowCount(), userId);
                return (Void) null;
            })
            .onFailure(error -> logger.error("Failed to invalidate all user sessions: {}", userId, error));
    }
    
    @Override
    public Future<Void> invalidateByAccessTokenHash(String accessTokenHash) {
        return executeQuery(INVALIDATE_BY_ACCESS_TOKEN_HASH_SQL, Tuple.of(accessTokenHash))
            .map(rows -> {
                if (rows.rowCount() == 0) {
                    logger.warn("No session found to invalidate with access token hash");
                } else {
                    logger.info("Invalidated session by access token hash");
                }
                return (Void) null;
            })
            .onFailure(error -> logger.error("Failed to invalidate session by access token hash", error));
    }
    
    @Override
    public Future<Void> invalidateByRefreshTokenHash(String refreshTokenHash) {
        return executeQuery(INVALIDATE_BY_REFRESH_TOKEN_HASH_SQL, Tuple.of(refreshTokenHash))
            .map(rows -> {
                if (rows.rowCount() == 0) {
                    logger.warn("No session found to invalidate with refresh token hash");
                } else {
                    logger.info("Invalidated session by refresh token hash");
                }
                return (Void) null;
            })
            .onFailure(error -> logger.error("Failed to invalidate session by refresh token hash", error));
    }
    
    @Override
    public Future<Integer> cleanupExpiredSessions() {
        return executeQuery(CLEANUP_EXPIRED_SESSIONS_SQL)
            .map(rows -> {
                int deletedCount = rows.rowCount();
                logger.info("Cleaned up {} expired sessions", deletedCount);
                return deletedCount;
            })
            .onFailure(error -> logger.error("Failed to cleanup expired sessions", error));
    }
    
    @Override
    public Future<List<Session>> findSessionsExpiringBefore(OffsetDateTime expirationTime) {
        return executeQuery(FIND_SESSIONS_EXPIRING_BEFORE_SQL, Tuple.of(expirationTime))
            .map(this::mapRowsToEntities)
            .onFailure(error -> logger.error("Failed to find sessions expiring before: {}", expirationTime, error));
    }
    
    @Override
    public Future<Long> countActiveSessionsByUserId(UUID userId) {
        return executeQuery(COUNT_ACTIVE_SESSIONS_BY_USER_ID_SQL, Tuple.of(userId))
            .map(rows -> {
                Row row = rows.iterator().next();
                return row.getLong(0);
            })
            .onFailure(error -> logger.error("Failed to count active sessions by user ID: {}", userId, error));
    }
    
    @Override
    public Future<Long> countActiveSessions() {
        String sql = "SELECT COUNT(*) FROM sessions WHERE is_active = true AND expires_at > NOW()";
        return executeQuery(sql, Tuple.tuple())
            .map(rows -> {
                if (rows.size() > 0) {
                    return rows.iterator().next().getLong(0);
                }
                return 0L;
            })
            .onFailure(error -> logger.error("Failed to count active sessions", error));
    }
    
    @Override
    public Future<Long> countTotalSessions() {
        String sql = "SELECT COUNT(*) FROM sessions";
        return executeQuery(sql, Tuple.tuple())
            .map(rows -> {
                if (rows.size() > 0) {
                    return rows.iterator().next().getLong(0);
                }
                return 0L;
            })
            .onFailure(error -> logger.error("Failed to count total sessions", error));
    }
    
    @Override
    public Future<Long> countSessionsCreatedSince(OffsetDateTime since) {
        String sql = "SELECT COUNT(*) FROM sessions WHERE created_at >= $1";
        return executeQuery(sql, Tuple.of(since))
            .map(rows -> {
                if (rows.size() > 0) {
                    return rows.iterator().next().getLong(0);
                }
                return 0L;
            })
            .onFailure(error -> logger.error("Failed to count sessions created since: {}", since, error));
    }
}