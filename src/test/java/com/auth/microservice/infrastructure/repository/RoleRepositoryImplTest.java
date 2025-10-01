package com.auth.microservice.infrastructure.repository;

import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.port.Pagination;
import com.auth.microservice.infrastructure.adapter.repository.RoleRepositoryImpl;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RoleRepositoryImpl using real PostgreSQL database
 */
@ExtendWith(VertxExtension.class)
@Testcontainers
class RoleRepositoryImplTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("auth_test")
            .withUsername("test")
            .withPassword("test");
    
    private PgPool pool;
    private RoleRepositoryImpl roleRepository;
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
        roleRepository = new RoleRepositoryImpl(pool, transactionManager);
        
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
    void shouldSaveAndFindRoleById(VertxTestContext testContext) {
        // Given
        Role role = new Role("ADMIN", "Administrator role");
        
        // When & Then
        roleRepository.save(role)
            .compose(savedRole -> {
                assertNotNull(savedRole.getId());
                assertEquals("ADMIN", savedRole.getName());
                assertEquals("Administrator role", savedRole.getDescription());
                
                return roleRepository.findById(savedRole.getId());
            })
            .onSuccess(foundRole -> {
                testContext.verify(() -> {
                    assertTrue(foundRole.isPresent());
                    assertEquals("ADMIN", foundRole.get().getName());
                    assertEquals("Administrator role", foundRole.get().getDescription());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldFindRoleByName(VertxTestContext testContext) {
        // Given
        Role role = new Role("USER", "Regular user role");
        
        // When & Then
        roleRepository.save(role)
            .compose(savedRole -> roleRepository.findByName("USER"))
            .onSuccess(foundRole -> {
                testContext.verify(() -> {
                    assertTrue(foundRole.isPresent());
                    assertEquals("USER", foundRole.get().getName());
                    assertEquals("Regular user role", foundRole.get().getDescription());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldUpdateRole(VertxTestContext testContext) {
        // Given
        Role role = new Role("MODERATOR", "Moderator role");
        
        // When & Then
        roleRepository.save(role)
            .compose(savedRole -> {
                savedRole.updateDescription("Updated moderator role");
                return roleRepository.update(savedRole);
            })
            .onSuccess(updatedRole -> {
                testContext.verify(() -> {
                    assertEquals("Updated moderator role", updatedRole.getDescription());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldCheckIfRoleNameExists(VertxTestContext testContext) {
        // Given
        Role role = new Role("EDITOR", "Editor role");
        
        // When & Then
        roleRepository.save(role)
            .compose(savedRole -> roleRepository.existsByName("EDITOR"))
            .onSuccess(exists -> {
                testContext.verify(() -> assertTrue(exists));
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldFindAllRolesWithPagination(VertxTestContext testContext) {
        // Given
        Role role1 = new Role("ROLE_ONE", "First role");
        Role role2 = new Role("ROLE_TWO", "Second role");
        Role role3 = new Role("ROLE_THREE", "Third role");
        
        Pagination pagination = Pagination.of(0, 2);
        
        // When & Then
        roleRepository.save(role1)
            .compose(r1 -> roleRepository.save(role2))
            .compose(r2 -> roleRepository.save(role3))
            .compose(r3 -> roleRepository.findAll(pagination))
            .onSuccess(roles -> {
                testContext.verify(() -> {
                    assertEquals(2, roles.size());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldDeleteRoleById(VertxTestContext testContext) {
        // Given
        Role role = new Role("DELETE_ROLE", "Role to delete");
        
        // When & Then
        roleRepository.save(role)
            .compose(savedRole -> roleRepository.deleteById(savedRole.getId())
                .compose(v -> roleRepository.findById(savedRole.getId())))
            .onSuccess(foundRole -> {
                testContext.verify(() -> {
                    assertFalse(foundRole.isPresent());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldCountRoles(VertxTestContext testContext) {
        // Given
        Role role1 = new Role("COUNT_ONE", "Count role one");
        Role role2 = new Role("COUNT_TWO", "Count role two");
        
        // When & Then
        roleRepository.save(role1)
            .compose(r1 -> roleRepository.save(role2))
            .compose(r2 -> roleRepository.count())
            .onSuccess(count -> {
                testContext.verify(() -> {
                    assertTrue(count >= 2);
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    private io.vertx.core.Future<Void> createTables() {
        String createRolesTable = """
            CREATE TABLE IF NOT EXISTS roles (
                id UUID PRIMARY KEY,
                name VARCHAR(100) UNIQUE NOT NULL,
                description TEXT,
                created_at TIMESTAMP NOT NULL
            )
            """;
        
        String createPermissionsTable = """
            CREATE TABLE IF NOT EXISTS permissions (
                id UUID PRIMARY KEY,
                name VARCHAR(100) UNIQUE NOT NULL,
                resource VARCHAR(100) NOT NULL,
                action VARCHAR(50) NOT NULL,
                description TEXT
            )
            """;
        
        String createRolePermissionsTable = """
            CREATE TABLE IF NOT EXISTS role_permissions (
                role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
                permission_id UUID REFERENCES permissions(id) ON DELETE CASCADE,
                PRIMARY KEY (role_id, permission_id)
            )
            """;
        
        String createUsersTable = """
            CREATE TABLE IF NOT EXISTS users (
                id UUID PRIMARY KEY,
                username VARCHAR(50) UNIQUE NOT NULL,
                email VARCHAR(255) UNIQUE NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                first_name VARCHAR(100) NOT NULL,
                last_name VARCHAR(100) NOT NULL,
                is_active BOOLEAN NOT NULL DEFAULT true,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
            """;
        
        String createUserRolesTable = """
            CREATE TABLE IF NOT EXISTS user_roles (
                user_id UUID REFERENCES users(id) ON DELETE CASCADE,
                role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
                PRIMARY KEY (user_id, role_id)
            )
            """;
        
        return pool.query(createRolesTable).execute()
            .compose(v -> pool.query(createPermissionsTable).execute())
            .compose(v -> pool.query(createRolePermissionsTable).execute())
            .compose(v -> pool.query(createUsersTable).execute())
            .compose(v -> pool.query(createUserRolesTable).execute())
            .mapEmpty();
    }
}