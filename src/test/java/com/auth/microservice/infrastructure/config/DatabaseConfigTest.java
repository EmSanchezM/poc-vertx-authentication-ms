package com.auth.microservice.infrastructure.config;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración para DatabaseConfig usando Testcontainers
 */
@ExtendWith(VertxExtension.class)
@Testcontainers
class DatabaseConfigTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("auth_test")
            .withUsername("test_user")
            .withPassword("test_password");

    private Vertx vertx;
    private DatabaseConfig databaseConfig;
    private DatabaseProperties databaseProperties;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        
        databaseProperties = new DatabaseProperties(
            postgres.getHost(),
            postgres.getFirstMappedPort(),
            postgres.getDatabaseName(),
            postgres.getUsername(),
            postgres.getPassword()
        );
        
        databaseConfig = new DatabaseConfig(vertx, databaseProperties);
    }

    @AfterEach
    void tearDown(VertxTestContext testContext) {
        if (databaseConfig != null) {
            databaseConfig.close()
                .compose(v -> vertx.close())
                .onComplete(testContext.succeedingThenComplete());
        } else if (vertx != null) {
            vertx.close().onComplete(testContext.succeedingThenComplete());
        } else {
            testContext.completeNow();
        }
    }

    @Test
    void shouldCreateConnectionPoolSuccessfully() {
        assertThat(databaseConfig).isNotNull();
        assertThat(databaseConfig.getPool()).isNotNull();
    }

    @Test
    void shouldPassHealthCheck(VertxTestContext testContext) {
        databaseConfig.healthCheck()
            .onComplete(testContext.succeeding(isHealthy -> testContext.verify(() -> {
                assertThat(isHealthy).isTrue();
                testContext.completeNow();
            })));
    }

    @Test
    void shouldExecuteSimpleQuery(VertxTestContext testContext) {
        databaseConfig.getPool()
            .query("SELECT 1 as test_value")
            .execute()
            .onComplete(testContext.succeeding(rowSet -> testContext.verify(() -> {
                assertThat(rowSet).isNotNull();
                assertThat(rowSet.size()).isEqualTo(1);
                
                var row = rowSet.iterator().next();
                assertThat(row.getInteger("test_value")).isEqualTo(1);
                
                testContext.completeNow();
            })));
    }

    @Test
    void shouldGetPoolStatus() {
        DatabaseConfig.PoolStatus status = databaseConfig.getPoolStatus();
        
        assertThat(status).isNotNull();
        assertThat(status.getTotalConnections()).isGreaterThanOrEqualTo(0);
        assertThat(status.getActiveConnections()).isGreaterThanOrEqualTo(0);
        assertThat(status.getIdleConnections()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldHandleMultipleConcurrentConnections(VertxTestContext testContext) {
        int numberOfQueries = 10;
        var checkpoint = testContext.checkpoint(numberOfQueries);
        
        for (int i = 0; i < numberOfQueries; i++) {
            final int queryId = i;
            databaseConfig.getPool()
                .query("SELECT " + queryId + " as query_id")
                .execute()
                .onComplete(testContext.succeeding(rowSet -> {
                    var row = rowSet.iterator().next();
                    assertThat(row.getInteger("query_id")).isEqualTo(queryId);
                    checkpoint.flag();
                }));
        }
    }

    @Test
    void shouldClosePoolGracefully(VertxTestContext testContext) {
        databaseConfig.close()
            .onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                // Verificar que el pool se cerró correctamente
                // Nota: Vert.x no expone directamente el estado del pool cerrado
                testContext.completeNow();
            })));
    }
}