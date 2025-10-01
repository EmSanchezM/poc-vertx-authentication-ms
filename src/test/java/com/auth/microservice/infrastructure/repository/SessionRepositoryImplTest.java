package com.auth.microservice.infrastructure.repository;

import com.auth.microservice.domain.model.Session;
import com.auth.microservice.infrastructure.adapter.repository.SessionRepositoryImpl;
import com.auth.microservice.infrastructure.adapter.repository.TransactionManager;
import com.auth.microservice.infrastructure.adapter.repository.VertxTransactionManager;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SessionRepositoryImpl using real PostgreSQL database
 */
@ExtendWith(VertxExtension.class)
@Testcontainers
class SessionRepositoryImplTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("auth_test")
            .withUsername("test")
            .withPassword("test");
    
    private PgPool pool;
    private SessionRepositoryImpl sessionRepository;
    private TransactionManager transactionManager;
    
    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(postgres.getFirstMappedPort())
                .setHost(postgres.getHost())
                .setDatabase(postgres.getDatabaseName())
                .setUser(postgres.getUsername())
                .setPassword(postgres.getPassword());
        
        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
        pool = PgPool.pool(vertx, connectOptions, poolOptions);
        transactionManager = new VertxTransactionManager(pool);
        sessionRepository = new SessionRepositoryImpl(pool, transactionManager);
        
        // Create tables
        createTables()
            .onComplete(testContext.succeedingThenComplete());
    }
    
    @AfterEach
    void tearDown(VertxTestContext testContext) {
        if (pool != null) {
            pool.close()
                .onComplete(testContext.succeedingThenComplete());
        }
    }
    
    @Test
    void shouldSaveAndFindSessionById(VertxTestContext testContext) {
        // Given
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        Session session = new Session(userId, "access_hash", "refresh_hash", 
                                    expiresAt, "192.168.1.1", "Mozilla/5.0");
        
        // When & Then
        sessionRepository.save(session)
            .compose(savedSession -> {
                assertNotNull(savedSession.getId());
                assertEquals(userId, savedSession.getUserId());
                assertEquals("access_hash", savedSession.getAccessTokenHash());
                assertEquals("refresh_hash", savedSession.getRefreshTokenHash());
                
                return sessionRepository.findById(savedSession.getId());
            })
            .onSuccess(foundSession -> {
                testContext.verify(() -> {
                    assertTrue(foundSession.isPresent());
                    assertEquals(userId, foundSession.get().getUserId());
                    assertEquals("access_hash", foundSession.get().getAccessTokenHash());
                    assertEquals("refresh_hash", foundSession.get().getRefreshTokenHash());
                    assertTrue(foundSession.get().isActive());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldFindSessionByAccessTokenHash(VertxTestContext testContext) {
        // Given
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        String accessTokenHash = "unique_access_hash";
        Session session = new Session(userId, accessTokenHash, "refresh_hash", 
                                    expiresAt, "192.168.1.1", "Mozilla/5.0");
        
        // When & Then
        sessionRepository.save(session)
            .compose(savedSession -> sessionRepository.findByAccessTokenHash(accessTokenHash))
            .onSuccess(foundSession -> {
                testContext.verify(() -> {
                    assertTrue(foundSession.isPresent());
                    assertEquals(accessTokenHash, foundSession.get().getAccessTokenHash());
                    assertEquals(userId, foundSession.get().getUserId());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldFindSessionByRefreshTokenHash(VertxTestContext testContext) {
        // Given
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        String refreshTokenHash = "unique_refresh_hash";
        Session session = new Session(userId, "access_hash", refreshTokenHash, 
                                    expiresAt, "192.168.1.1", "Mozilla/5.0");
        
        // When & Then
        sessionRepository.save(session)
            .compose(savedSession -> sessionRepository.findByRefreshTokenHash(refreshTokenHash))
            .onSuccess(foundSession -> {
                testContext.verify(() -> {
                    assertTrue(foundSession.isPresent());
                    assertEquals(refreshTokenHash, foundSession.get().getRefreshTokenHash());
                    assertEquals(userId, foundSession.get().getUserId());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldFindActiveSessionsByUserId(VertxTestContext testContext) {
        // Given
        UUID userId = UUID.randomUUID();
        LocalDateTime futureExpiry = LocalDateTime.now().plusHours(1);
        LocalDateTime pastExpiry = LocalDateTime.now().minusHours(1);
        
        Session activeSession1 = new Session(userId, "access1", "refresh1", 
                                           futureExpiry, "192.168.1.1", "Mozilla/5.0");
        Session activeSession2 = new Session(userId, "access2", "refresh2", 
                                           futureExpiry, "192.168.1.2", "Chrome");
        Session expiredSession = new Session(userId, "access3", "refresh3", 
                                           pastExpiry, "192.168.1.3", "Safari");
        
        // When & Then
        sessionRepository.save(activeSession1)
            .compose(s1 -> sessionRepository.save(activeSession2))
            .compose(s2 -> sessionRepository.save(expiredSession))
            .compose(s3 -> sessionRepository.findActiveSessionsByUserId(userId))
            .onSuccess(sessions -> {
                testContext.verify(() -> {
                    assertEquals(2, sessions.size());
                    assertTrue(sessions.stream().allMatch(s -> s.getUserId().equals(userId)));
                    assertTrue(sessions.stream().allMatch(Session::isActive));
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldFindAllSessionsByUserId(VertxTestContext testContext) {
        // Given
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        Session session1 = new Session(userId, "access1", "refresh1", 
                                     expiresAt, "192.168.1.1", "Mozilla/5.0");
        Session session2 = new Session(userId, "access2", "refresh2", 
                                     expiresAt, "192.168.1.2", "Chrome");
        session2.invalidate(); // Make one inactive
        
        // When & Then
        sessionRepository.save(session1)
            .compose(s1 -> sessionRepository.save(session2))
            .compose(s2 -> sessionRepository.findAllSessionsByUserId(userId))
            .onSuccess(sessions -> {
                testContext.verify(() -> {
                    assertEquals(2, sessions.size());
                    assertTrue(sessions.stream().allMatch(s -> s.getUserId().equals(userId)));
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldInvalidateAllUserSessions(VertxTestContext testContext) {
        // Given
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        Session session1 = new Session(userId, "access1", "refresh1", 
                                     expiresAt, "192.168.1.1", "Mozilla/5.0");
        Session session2 = new Session(userId, "access2", "refresh2", 
                                     expiresAt, "192.168.1.2", "Chrome");
        
        // When & Then
        sessionRepository.save(session1)
            .compose(s1 -> sessionRepository.save(session2))
            .compose(s2 -> sessionRepository.invalidateAllUserSessions(userId))
            .compose(v -> sessionRepository.findActiveSessionsByUserId(userId))
            .onSuccess(sessions -> {
                testContext.verify(() -> {
                    assertEquals(0, sessions.size());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldInvalidateByAccessTokenHash(VertxTestContext testContext) {
        // Given
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        String accessTokenHash = "invalidate_access_hash";
        Session session = new Session(userId, accessTokenHash, "refresh_hash", 
                                    expiresAt, "192.168.1.1", "Mozilla/5.0");
        
        // When & Then
        sessionRepository.save(session)
            .compose(savedSession -> sessionRepository.invalidateByAccessTokenHash(accessTokenHash))
            .compose(v -> sessionRepository.findByAccessTokenHash(accessTokenHash))
            .onSuccess(foundSession -> {
                testContext.verify(() -> {
                    assertFalse(foundSession.isPresent()); // Should not find active session
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldInvalidateByRefreshTokenHash(VertxTestContext testContext) {
        // Given
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        String refreshTokenHash = "invalidate_refresh_hash";
        Session session = new Session(userId, "access_hash", refreshTokenHash, 
                                    expiresAt, "192.168.1.1", "Mozilla/5.0");
        
        // When & Then
        sessionRepository.save(session)
            .compose(savedSession -> sessionRepository.invalidateByRefreshTokenHash(refreshTokenHash))
            .compose(v -> sessionRepository.findByRefreshTokenHash(refreshTokenHash))
            .onSuccess(foundSession -> {
                testContext.verify(() -> {
                    assertFalse(foundSession.isPresent()); // Should not find active session
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldCountActiveSessionsByUserId(VertxTestContext testContext) {
        // Given
        UUID userId = UUID.randomUUID();
        LocalDateTime futureExpiry = LocalDateTime.now().plusHours(1);
        LocalDateTime pastExpiry = LocalDateTime.now().minusHours(1);
        
        Session activeSession1 = new Session(userId, "access1", "refresh1", 
                                           futureExpiry, "192.168.1.1", "Mozilla/5.0");
        Session activeSession2 = new Session(userId, "access2", "refresh2", 
                                           futureExpiry, "192.168.1.2", "Chrome");
        Session expiredSession = new Session(userId, "access3", "refresh3", 
                                           pastExpiry, "192.168.1.3", "Safari");
        
        // When & Then
        sessionRepository.save(activeSession1)
            .compose(s1 -> sessionRepository.save(activeSession2))
            .compose(s2 -> sessionRepository.save(expiredSession))
            .compose(s3 -> sessionRepository.countActiveSessionsByUserId(userId))
            .onSuccess(count -> {
                testContext.verify(() -> {
                    assertEquals(2L, count);
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldFindSessionsExpiringBefore(VertxTestContext testContext) {
        // Given
        UUID userId = UUID.randomUUID();
        LocalDateTime soonExpiry = LocalDateTime.now().plusMinutes(30);
        LocalDateTime laterExpiry = LocalDateTime.now().plusHours(2);
        LocalDateTime checkTime = LocalDateTime.now().plusHours(1);
        
        Session expiringSoon = new Session(userId, "access1", "refresh1", 
                                         soonExpiry, "192.168.1.1", "Mozilla/5.0");
        Session expiringLater = new Session(userId, "access2", "refresh2", 
                                          laterExpiry, "192.168.1.2", "Chrome");
        
        // When & Then
        sessionRepository.save(expiringSoon)
            .compose(s1 -> sessionRepository.save(expiringLater))
            .compose(s2 -> sessionRepository.findSessionsExpiringBefore(checkTime))
            .onSuccess(sessions -> {
                testContext.verify(() -> {
                    assertEquals(1, sessions.size());
                    assertEquals("access1", sessions.get(0).getAccessTokenHash());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldUpdateSession(VertxTestContext testContext) {
        // Given
        UUID userId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        Session session = new Session(userId, "old_access", "old_refresh", 
                                    expiresAt, "192.168.1.1", "Mozilla/5.0");
        
        // When & Then
        sessionRepository.save(session)
            .compose(savedSession -> {
                LocalDateTime newExpiresAt = LocalDateTime.now().plusHours(2);
                savedSession.updateTokens("new_access", "new_refresh", newExpiresAt);
                return sessionRepository.update(savedSession);
            })
            .onSuccess(updatedSession -> {
                testContext.verify(() -> {
                    assertEquals("new_access", updatedSession.getAccessTokenHash());
                    assertEquals("new_refresh", updatedSession.getRefreshTokenHash());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    private io.vertx.core.Future<Void> createTables() {
        String createSessionsTable = """
            CREATE TABLE IF NOT EXISTS sessions (
                id UUID PRIMARY KEY,
                user_id UUID NOT NULL,
                access_token_hash VARCHAR(255) NOT NULL,
                refresh_token_hash VARCHAR(255) NOT NULL,
                expires_at TIMESTAMP NOT NULL,
                created_at TIMESTAMP NOT NULL,
                last_used_at TIMESTAMP NOT NULL,
                ip_address VARCHAR(45),
                user_agent TEXT,
                is_active BOOLEAN NOT NULL DEFAULT true
            )
            """;
        
        String createIndexes = """
            CREATE INDEX IF NOT EXISTS idx_sessions_access_token_hash ON sessions(access_token_hash);
            CREATE INDEX IF NOT EXISTS idx_sessions_refresh_token_hash ON sessions(refresh_token_hash);
            CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id);
            CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at);
            """;
        
        return pool.query(createSessionsTable).execute()
            .compose(v -> pool.query(createIndexes).execute())
            .mapEmpty();
    }
}