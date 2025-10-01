package com.auth.microservice.infrastructure.repository;

import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.port.Pagination;
import com.auth.microservice.infrastructure.adapter.repository.PermissionRepositoryImpl;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PermissionRepositoryImpl using real PostgreSQL database
 */
@ExtendWith(VertxExtension.class)
@Testcontainers
class PermissionRepositoryImplTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("auth_test")
            .withUsername("test")
            .withPassword("test");
    
    private PgPool pool;
    private PermissionRepositoryImpl permissionRepository;
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
        permissionRepository = new PermissionRepositoryImpl(pool, transactionManager);
        
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
    void shouldSaveAndFindPermissionById(VertxTestContext testContext) {
        // Given
        Permission permission = new Permission("USER_READ", "user", "read", "Read user data");
        
        // When & Then
        permissionRepository.save(permission)
            .compose(savedPermission -> {
                assertNotNull(savedPermission.getId());
                assertEquals("USER_READ", savedPermission.getName());
                assertEquals("user", savedPermission.getResource());
                assertEquals("read", savedPermission.getAction());
                
                return permissionRepository.findById(savedPermission.getId());
            })
            .onSuccess(foundPermission -> {
                testContext.verify(() -> {
                    assertTrue(foundPermission.isPresent());
                    assertEquals("USER_READ", foundPermission.get().getName());
                    assertEquals("user", foundPermission.get().getResource());
                    assertEquals("read", foundPermission.get().getAction());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldFindPermissionByName(VertxTestContext testContext) {
        // Given
        Permission permission = new Permission("USER_WRITE", "user", "write", "Write user data");
        
        // When & Then
        permissionRepository.save(permission)
            .compose(savedPermission -> permissionRepository.findByName("USER_WRITE"))
            .onSuccess(foundPermission -> {
                testContext.verify(() -> {
                    assertTrue(foundPermission.isPresent());
                    assertEquals("USER_WRITE", foundPermission.get().getName());
                    assertEquals("user", foundPermission.get().getResource());
                    assertEquals("write", foundPermission.get().getAction());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldFindPermissionByResourceAndAction(VertxTestContext testContext) {
        // Given
        Permission permission = new Permission("USER_DELETE", "user", "delete", "Delete user data");
        
        // When & Then
        permissionRepository.save(permission)
            .compose(savedPermission -> permissionRepository.findByResourceAndAction("user", "delete"))
            .onSuccess(foundPermission -> {
                testContext.verify(() -> {
                    assertTrue(foundPermission.isPresent());
                    assertEquals("USER_DELETE", foundPermission.get().getName());
                    assertEquals("user", foundPermission.get().getResource());
                    assertEquals("delete", foundPermission.get().getAction());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldFindPermissionsByResource(VertxTestContext testContext) {
        // Given
        Permission permission1 = new Permission("ROLE_READ", "role", "read", "Read role data");
        Permission permission2 = new Permission("ROLE_WRITE", "role", "write", "Write role data");
        Permission permission3 = new Permission("USER_READ", "user", "read", "Read user data");
        
        // When & Then
        permissionRepository.save(permission1)
            .compose(p1 -> permissionRepository.save(permission2))
            .compose(p2 -> permissionRepository.save(permission3))
            .compose(p3 -> permissionRepository.findByResource("role"))
            .onSuccess(permissions -> {
                testContext.verify(() -> {
                    assertEquals(2, permissions.size());
                    assertTrue(permissions.stream().allMatch(p -> "role".equals(p.getResource())));
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldUpdatePermission(VertxTestContext testContext) {
        // Given
        Permission permission = new Permission("TEMP_PERMISSION", "temp", "action", "Temporary permission");
        
        // When & Then
        permissionRepository.save(permission)
            .compose(savedPermission -> {
                Permission updatedPermission = new Permission(
                    savedPermission.getId(),
                    "UPDATED_PERMISSION",
                    "updated",
                    "update",
                    "Updated permission"
                );
                return permissionRepository.update(updatedPermission);
            })
            .onSuccess(updatedPermission -> {
                testContext.verify(() -> {
                    assertEquals("UPDATED_PERMISSION", updatedPermission.getName());
                    assertEquals("updated", updatedPermission.getResource());
                    assertEquals("update", updatedPermission.getAction());
                    assertEquals("Updated permission", updatedPermission.getDescription());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldCheckIfPermissionNameExists(VertxTestContext testContext) {
        // Given
        Permission permission = new Permission("EXISTS_PERMISSION", "exists", "check", "Permission to check existence");
        
        // When & Then
        permissionRepository.save(permission)
            .compose(savedPermission -> permissionRepository.existsByName("EXISTS_PERMISSION"))
            .onSuccess(exists -> {
                testContext.verify(() -> assertTrue(exists));
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldCheckIfPermissionExistsByResourceAndAction(VertxTestContext testContext) {
        // Given
        Permission permission = new Permission("RESOURCE_ACTION_CHECK", "resource", "action", "Check resource action");
        
        // When & Then
        permissionRepository.save(permission)
            .compose(savedPermission -> permissionRepository.existsByResourceAndAction("resource", "action"))
            .onSuccess(exists -> {
                testContext.verify(() -> assertTrue(exists));
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldFindAllPermissionsWithPagination(VertxTestContext testContext) {
        // Given
        Permission permission1 = new Permission("PERM_ONE", "resource1", "action1", "First permission");
        Permission permission2 = new Permission("PERM_TWO", "resource2", "action2", "Second permission");
        Permission permission3 = new Permission("PERM_THREE", "resource3", "action3", "Third permission");
        
        Pagination pagination = Pagination.of(0, 2);
        
        // When & Then
        permissionRepository.save(permission1)
            .compose(p1 -> permissionRepository.save(permission2))
            .compose(p2 -> permissionRepository.save(permission3))
            .compose(p3 -> permissionRepository.findAll(pagination))
            .onSuccess(permissions -> {
                testContext.verify(() -> {
                    assertEquals(2, permissions.size());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldDeletePermissionById(VertxTestContext testContext) {
        // Given
        Permission permission = new Permission("DELETE_PERMISSION", "delete", "test", "Permission to delete");
        
        // When & Then
        permissionRepository.save(permission)
            .compose(savedPermission -> permissionRepository.deleteById(savedPermission.getId())
                .compose(v -> permissionRepository.findById(savedPermission.getId())))
            .onSuccess(foundPermission -> {
                testContext.verify(() -> {
                    assertFalse(foundPermission.isPresent());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldCountPermissions(VertxTestContext testContext) {
        // Given
        Permission permission1 = new Permission("COUNT_ONE", "count1", "action", "Count permission one");
        Permission permission2 = new Permission("COUNT_TWO", "count2", "action", "Count permission two");
        
        // When & Then
        permissionRepository.save(permission1)
            .compose(p1 -> permissionRepository.save(permission2))
            .compose(p2 -> permissionRepository.count())
            .onSuccess(count -> {
                testContext.verify(() -> {
                    assertTrue(count >= 2);
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    private io.vertx.core.Future<Void> createTables() {
        String createPermissionsTable = """
            CREATE TABLE IF NOT EXISTS permissions (
                id UUID PRIMARY KEY,
                name VARCHAR(100) UNIQUE NOT NULL,
                resource VARCHAR(100) NOT NULL,
                action VARCHAR(50) NOT NULL,
                description TEXT
            )
            """;
        
        String createRolesTable = """
            CREATE TABLE IF NOT EXISTS roles (
                id UUID PRIMARY KEY,
                name VARCHAR(100) UNIQUE NOT NULL,
                description TEXT,
                created_at TIMESTAMP NOT NULL
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
        
        return pool.query(createPermissionsTable).execute()
            .compose(v -> pool.query(createRolesTable).execute())
            .compose(v -> pool.query(createRolePermissionsTable).execute())
            .compose(v -> pool.query(createUsersTable).execute())
            .compose(v -> pool.query(createUserRolesTable).execute())
            .mapEmpty();
    }
}