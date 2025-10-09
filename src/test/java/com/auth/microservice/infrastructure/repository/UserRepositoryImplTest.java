package com.auth.microservice.infrastructure.repository;

import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.Pagination;
import com.auth.microservice.infrastructure.adapter.repository.TransactionManager;
import com.auth.microservice.infrastructure.adapter.repository.UserRepositoryImpl;
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
 * Integration tests for UserRepositoryImpl using real PostgreSQL database
 */
@ExtendWith(VertxExtension.class)
@Testcontainers
class UserRepositoryImplTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("auth_test")
            .withUsername("test")
            .withPassword("test");
    
    private PgPool pool;
    private UserRepositoryImpl userRepository;
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
        userRepository = new UserRepositoryImpl(pool, transactionManager);
        
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
    void shouldSaveAndFindUserById(VertxTestContext testContext) {
        // Given
        User user = new User("testuser", new Email("test@example.com"), 
                           "hashedpassword", "John", "Doe");
        
        // When & Then
        userRepository.save(user)
            .compose(savedUser -> {
                assertNotNull(savedUser.getId());
                assertEquals("testuser", savedUser.getUsername());
                assertEquals("test@example.com", savedUser.getEmail().getValue());
                
                return userRepository.findById(savedUser.getId());
            })
            .onSuccess(foundUser -> {
                testContext.verify(() -> {
                    assertTrue(foundUser.isPresent());
                    assertEquals("testuser", foundUser.get().getUsername());
                    assertEquals("test@example.com", foundUser.get().getEmail().getValue());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldFindUserByEmail(VertxTestContext testContext) {
        // Given
        Email email = new Email("findme@example.com");
        User user = new User("finduser", email, "hashedpassword", "Jane", "Smith");
        
        // When & Then
        userRepository.save(user)
            .compose(savedUser -> userRepository.findByEmail(email))
            .onSuccess(foundUser -> {
                testContext.verify(() -> {
                    assertTrue(foundUser.isPresent());
                    assertEquals("finduser", foundUser.get().getUsername());
                    assertEquals("findme@example.com", foundUser.get().getEmail().getValue());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldFindUserByUsername(VertxTestContext testContext) {
        // Given
        String username = "uniqueuser";
        User user = new User(username, new Email("unique@example.com"), 
                           "hashedpassword", "Unique", "User");
        
        // When & Then
        userRepository.save(user)
            .compose(savedUser -> userRepository.findByUsername(username))
            .onSuccess(foundUser -> {
                testContext.verify(() -> {
                    assertTrue(foundUser.isPresent());
                    assertEquals(username, foundUser.get().getUsername());
                    assertEquals("unique@example.com", foundUser.get().getEmail().getValue());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldUpdateUser(VertxTestContext testContext) {
        // Given
        User user = new User("updateuser", new Email("update@example.com"), 
                           "hashedpassword", "Update", "User");
        
        // When & Then
        userRepository.save(user)
            .compose(savedUser -> {
                savedUser.updateProfile("Updated", "Name");
                return userRepository.update(savedUser);
            })
            .onSuccess(updatedUser -> {
                testContext.verify(() -> {
                    assertEquals("Updated", updatedUser.getFirstName());
                    assertEquals("Name", updatedUser.getLastName());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldCheckIfEmailExists(VertxTestContext testContext) {
        // Given
        Email email = new Email("exists@example.com");
        User user = new User("existsuser", email, "hashedpassword", "Exists", "User");
        
        // When & Then
        userRepository.save(user)
            .compose(savedUser -> userRepository.existsByEmail(email))
            .onSuccess(exists -> {
                testContext.verify(() -> assertTrue(exists));
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldCheckIfUsernameExists(VertxTestContext testContext) {
        // Given
        String username = "existsusername";
        User user = new User(username, new Email("existsusername@example.com"), 
                           "hashedpassword", "Exists", "Username");
        
        // When & Then
        userRepository.save(user)
            .compose(savedUser -> userRepository.existsByUsername(username))
            .onSuccess(exists -> {
                testContext.verify(() -> assertTrue(exists));
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldFindAllUsersWithPagination(VertxTestContext testContext) {
        // Given
        User user1 = new User("user1", new Email("user1@example.com"), "hash1", "User", "One");
        User user2 = new User("user2", new Email("user2@example.com"), "hash2", "User", "Two");
        User user3 = new User("user3", new Email("user3@example.com"), "hash3", "User", "Three");
        
        Pagination pagination = Pagination.of(0, 2);
        
        // When & Then
        userRepository.save(user1)
            .compose(u1 -> userRepository.save(user2))
            .compose(u2 -> userRepository.save(user3))
            .compose(u3 -> userRepository.findAll(pagination))
            .onSuccess(users -> {
                testContext.verify(() -> {
                    assertEquals(2, users.size());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldFindActiveUsersOnly(VertxTestContext testContext) {
        // Given
        User activeUser = new User("activeuser", new Email("active@example.com"), 
                                 "hash", "Active", "User");
        User inactiveUser = new User("inactiveuser", new Email("inactive@example.com"), 
                                   "hash", "Inactive", "User");
        inactiveUser.deactivate();
        
        Pagination pagination = Pagination.of(0, 10);
        
        // When & Then
        userRepository.save(activeUser)
            .compose(au -> userRepository.save(inactiveUser))
            .compose(iu -> userRepository.findActiveUsers(pagination))
            .onSuccess(users -> {
                testContext.verify(() -> {
                    assertEquals(1, users.size());
                    assertTrue(users.get(0).isActive());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldDeleteUserById(VertxTestContext testContext) {
        // Given
        User user = new User("deleteuser", new Email("delete@example.com"), 
                           "hashedpassword", "Delete", "User");
        
        // When & Then
        userRepository.save(user)
            .compose(savedUser -> userRepository.deleteById(savedUser.getId())
                .compose(v -> userRepository.findById(savedUser.getId())))
            .onSuccess(foundUser -> {
                testContext.verify(() -> {
                    assertFalse(foundUser.isPresent());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldCountUsers(VertxTestContext testContext) {
        // Given
        User user1 = new User("count1", new Email("count1@example.com"), "hash", "Count", "One");
        User user2 = new User("count2", new Email("count2@example.com"), "hash", "Count", "Two");
        
        // When & Then
        userRepository.save(user1)
            .compose(u1 -> userRepository.save(user2))
            .compose(u2 -> userRepository.count())
            .onSuccess(count -> {
                testContext.verify(() -> {
                    assertTrue(count >= 2);
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldSaveUserWithRoles(VertxTestContext testContext) {
        // Given
        User user = new User("roleuser", new Email("roleuser@example.com"), 
                           "hashedpassword", "Role", "User");
        
        // Create and insert test roles first
        createTestRoles()
            .compose(roles -> {
                // Add roles to user
                for (Role role : roles) {
                    user.addRole(role);
                }
                
                // When
                return userRepository.saveWithRoles(user);
            })
            .compose(savedUser -> {
                testContext.verify(() -> {
                    assertNotNull(savedUser.getId());
                    assertEquals("roleuser", savedUser.getUsername());
                    assertEquals(2, savedUser.getRoles().size());
                });
                
                // Verify roles were persisted by querying with roles
                return userRepository.findByIdWithRoles(savedUser.getId());
            })
            .onSuccess(foundUser -> {
                testContext.verify(() -> {
                    assertTrue(foundUser.isPresent());
                    assertEquals(2, foundUser.get().getRoles().size());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldSaveUserWithoutRoles(VertxTestContext testContext) {
        // Given
        User user = new User("noroleuser", new Email("noroleuser@example.com"), 
                           "hashedpassword", "NoRole", "User");
        
        // When & Then
        userRepository.saveWithRoles(user)
            .onSuccess(savedUser -> {
                testContext.verify(() -> {
                    assertNotNull(savedUser.getId());
                    assertEquals("noroleuser", savedUser.getUsername());
                    assertEquals(0, savedUser.getRoles().size());
                });
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }
    
    @Test
    void shouldRollbackOnRoleInsertFailure(VertxTestContext testContext) {
        // Given
        User user = new User("failuser", new Email("failuser@example.com"), 
                           "hashedpassword", "Fail", "User");
        
        // Add a role with non-existent ID to cause foreign key violation
        Role invalidRole = new Role(UUID.randomUUID(), "INVALID_ROLE", "Invalid role", 
                                  java.time.OffsetDateTime.now());
        user.addRole(invalidRole);
        
        // When & Then
        userRepository.saveWithRoles(user)
            .onSuccess(result -> testContext.failNow("Expected operation to fail"))
            .onFailure(error -> {
                testContext.verify(() -> {
                    assertNotNull(error);
                });
                
                // Verify user was not created due to rollback
                userRepository.findByUsername("failuser")
                    .onSuccess(foundUser -> {
                        testContext.verify(() -> {
                            assertFalse(foundUser.isPresent());
                        });
                        testContext.completeNow();
                    })
                    .onFailure(testContext::failNow);
            });
    }
    
    @Test
    void shouldHandleDatabaseConnectionError(VertxTestContext testContext) {
        // Given
        User user = new User("connuser", new Email("connuser@example.com"), 
                           "hashedpassword", "Conn", "User");
        
        // Close the pool to simulate connection error
        pool.close()
            .compose(v -> {
                // When
                return userRepository.saveWithRoles(user);
            })
            .onSuccess(result -> testContext.failNow("Expected operation to fail"))
            .onFailure(error -> {
                testContext.verify(() -> {
                    assertNotNull(error);
                });
                testContext.completeNow();
            });
    }
    
    private io.vertx.core.Future<java.util.List<Role>> createTestRoles() {
        Role adminRole = new Role("ADMIN", "Administrator role");
        Role userRole = new Role("USER", "Regular user role");
        
        String insertRole1 = """
            INSERT INTO roles (id, name, description, created_at) 
            VALUES ($1, $2, $3, $4)
            """;
        
        return pool.preparedQuery(insertRole1)
            .execute(io.vertx.sqlclient.Tuple.of(
                adminRole.getId(), 
                adminRole.getName(), 
                adminRole.getDescription(), 
                adminRole.getCreatedAt()))
            .compose(v -> pool.preparedQuery(insertRole1)
                .execute(io.vertx.sqlclient.Tuple.of(
                    userRole.getId(), 
                    userRole.getName(), 
                    userRole.getDescription(), 
                    userRole.getCreatedAt())))
            .map(v -> java.util.List.of(adminRole, userRole));
    }
    
    private io.vertx.core.Future<Void> createTables() {
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
        
        String createRolesTable = """
            CREATE TABLE IF NOT EXISTS roles (
                id UUID PRIMARY KEY,
                name VARCHAR(100) UNIQUE NOT NULL,
                description TEXT,
                created_at TIMESTAMP NOT NULL
            )
            """;
        
        String createUserRolesTable = """
            CREATE TABLE IF NOT EXISTS user_roles (
                user_id UUID REFERENCES users(id) ON DELETE CASCADE,
                role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
                assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                assigned_by UUID REFERENCES users(id),
                PRIMARY KEY (user_id, role_id)
            )
            """;
        
        return pool.query(createUsersTable).execute()
            .compose(v -> pool.query(createRolesTable).execute())
            .compose(v -> pool.query(createUserRolesTable).execute())
            .mapEmpty();
    }
}