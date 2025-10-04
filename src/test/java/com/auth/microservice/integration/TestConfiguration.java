package com.auth.microservice.integration;

import com.auth.microservice.infrastructure.config.ApplicationBootstrap;
import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test configuration utility for end-to-end integration tests.
 * Provides common setup and teardown functionality for all integration tests.
 */
public class TestConfiguration {
    
    public static final String POSTGRES_IMAGE = "postgres:15-alpine";
    public static final String REDIS_IMAGE = "redis:7-alpine";
    public static final String TEST_DATABASE_NAME = "auth_integration_test";
    public static final String TEST_USERNAME = "test_user";
    public static final String TEST_PASSWORD = "test_password";
    public static final int REDIS_PORT = 6379;
    
    /**
     * Creates a configured PostgreSQL container for testing.
     */
    public static PostgreSQLContainer<?> createPostgresContainer() {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName(TEST_DATABASE_NAME)
                .withUsername(TEST_USERNAME)
                .withPassword(TEST_PASSWORD)
                .withReuse(true);
    }
    
    /**
     * Creates a configured Redis container for testing.
     */
    public static GenericContainer<?> createRedisContainer() {
        return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(REDIS_PORT)
                .withReuse(true);
    }
    
    /**
     * Configures system properties for test environment.
     */
    public static void configureTestEnvironment(PostgreSQLContainer<?> postgres, 
                                              GenericContainer<?> redis, 
                                              int testPort) {
        System.setProperty("DATABASE_URL", postgres.getJdbcUrl());
        System.setProperty("DATABASE_USERNAME", postgres.getUsername());
        System.setProperty("DATABASE_PASSWORD", postgres.getPassword());
        System.setProperty("REDIS_HOST", redis.getHost());
        System.setProperty("REDIS_PORT", String.valueOf(redis.getMappedPort(REDIS_PORT)));
        System.setProperty("JWT_SECRET", "test-secret-key-for-jwt-signing-in-integration-tests");
        System.setProperty("SERVER_PORT", String.valueOf(testPort));
        System.setProperty("ENVIRONMENT", "test");
        System.setProperty("LOG_LEVEL", "INFO");
        
        // Configure RestAssured
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = testPort;
        RestAssured.basePath = "/api";
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
    
    /**
     * Starts the test server with the given configuration.
     */
    public static Future<HttpServer> startTestServer(Vertx vertx, ApplicationBootstrap bootstrap, int port) {
        Router router = bootstrap.configureRouter();
        HttpServer server = vertx.createHttpServer();
        
        return server.requestHandler(router)
            .listen(port)
            .onSuccess(httpServer -> {
                System.out.println("Test server started successfully on port " + port);
            })
            .onFailure(throwable -> {
                System.err.println("Failed to start test server on port " + port + ": " + throwable.getMessage());
            });
    }
    
    /**
     * Common test data for integration tests.
     */
    public static class TestData {
        public static final String ADMIN_EMAIL = "admin@test.com";
        public static final String ADMIN_PASSWORD = "AdminPassword123!";
        public static final String ADMIN_FIRST_NAME = "Test";
        public static final String ADMIN_LAST_NAME = "Admin";
        
        public static final String USER_EMAIL = "user@test.com";
        public static final String USER_PASSWORD = "UserPassword123!";
        public static final String USER_FIRST_NAME = "Test";
        public static final String USER_LAST_NAME = "User";
        
        public static final String GUEST_EMAIL = "guest@test.com";
        public static final String GUEST_PASSWORD = "GuestPassword123!";
        public static final String GUEST_FIRST_NAME = "Test";
        public static final String GUEST_LAST_NAME = "Guest";
    }
    
    /**
     * Common test utilities for API interactions.
     */
    public static class TestUtils {
        
        /**
         * Creates a user registration request body.
         */
        public static String createRegistrationRequest(String email, String password, 
                                                     String firstName, String lastName) {
            return String.format("""
                {
                    "email": "%s",
                    "password": "%s",
                    "firstName": "%s",
                    "lastName": "%s"
                }
                """, email, password, firstName, lastName);
        }
        
        /**
         * Creates a user login request body.
         */
        public static String createLoginRequest(String email, String password) {
            return String.format("""
                {
                    "email": "%s",
                    "password": "%s"
                }
                """, email, password);
        }
        
        /**
         * Creates a profile update request body.
         */
        public static String createProfileUpdateRequest(String firstName, String lastName) {
            return String.format("""
                {
                    "firstName": "%s",
                    "lastName": "%s"
                }
                """, firstName, lastName);
        }
        
        /**
         * Creates a role creation request body.
         */
        public static String createRoleRequest(String name, String description, String[] permissions) {
            StringBuilder permissionsJson = new StringBuilder("[");
            for (int i = 0; i < permissions.length; i++) {
                permissionsJson.append("\"").append(permissions[i]).append("\"");
                if (i < permissions.length - 1) {
                    permissionsJson.append(",");
                }
            }
            permissionsJson.append("]");
            
            return String.format("""
                {
                    "name": "%s",
                    "description": "%s",
                    "permissions": %s
                }
                """, name, description, permissionsJson.toString());
        }
        
        /**
         * Creates a role assignment request body.
         */
        public static String createRoleAssignmentRequest(String roleId) {
            return String.format("""
                {
                    "roleId": "%s"
                }
                """, roleId);
        }
        
        /**
         * Creates a token refresh request body.
         */
        public static String createTokenRefreshRequest(String refreshToken) {
            return String.format("""
                {
                    "refreshToken": "%s"
                }
                """, refreshToken);
        }
    }
    
    /**
     * Test assertions and validations.
     */
    public static class TestAssertions {
        
        /**
         * Validates that a response contains valid user data.
         */
        public static void assertValidUserResponse(io.restassured.response.Response response, 
                                                 String expectedEmail, 
                                                 String expectedFirstName, 
                                                 String expectedLastName) {
            response.then()
                .body("id", org.hamcrest.Matchers.notNullValue())
                .body("email", org.hamcrest.Matchers.equalTo(expectedEmail))
                .body("firstName", org.hamcrest.Matchers.equalTo(expectedFirstName))
                .body("lastName", org.hamcrest.Matchers.equalTo(expectedLastName))
                .body("isActive", org.hamcrest.Matchers.equalTo(true))
                .body("createdAt", org.hamcrest.Matchers.notNullValue())
                .body("updatedAt", org.hamcrest.Matchers.notNullValue());
        }
        
        /**
         * Validates that a response contains valid authentication tokens.
         */
        public static void assertValidAuthResponse(io.restassured.response.Response response) {
            response.then()
                .body("accessToken", org.hamcrest.Matchers.notNullValue())
                .body("refreshToken", org.hamcrest.Matchers.notNullValue())
                .body("tokenType", org.hamcrest.Matchers.equalTo("Bearer"))
                .body("user", org.hamcrest.Matchers.notNullValue())
                .body("user.id", org.hamcrest.Matchers.notNullValue())
                .body("user.email", org.hamcrest.Matchers.notNullValue());
        }
        
        /**
         * Validates that a response contains a valid error structure.
         */
        public static void assertValidErrorResponse(io.restassured.response.Response response, 
                                                  String expectedErrorCode) {
            response.then()
                .body("error", org.hamcrest.Matchers.notNullValue())
                .body("error.code", org.hamcrest.Matchers.equalTo(expectedErrorCode))
                .body("error.message", org.hamcrest.Matchers.notNullValue())
                .body("error.timestamp", org.hamcrest.Matchers.notNullValue());
        }
        
        /**
         * Validates that a response contains valid role data.
         */
        public static void assertValidRoleResponse(io.restassured.response.Response response, 
                                                 String expectedName, 
                                                 String expectedDescription) {
            response.then()
                .body("id", org.hamcrest.Matchers.notNullValue())
                .body("name", org.hamcrest.Matchers.equalTo(expectedName))
                .body("description", org.hamcrest.Matchers.equalTo(expectedDescription))
                .body("createdAt", org.hamcrest.Matchers.notNullValue());
        }
    }
    
    /**
     * Performance testing utilities.
     */
    public static class PerformanceUtils {
        
        /**
         * Measures the execution time of an operation.
         */
        public static long measureExecutionTime(Runnable operation) {
            long startTime = System.currentTimeMillis();
            operation.run();
            return System.currentTimeMillis() - startTime;
        }
        
        /**
         * Validates that an operation completes within the expected time.
         */
        public static void assertExecutionTime(Runnable operation, long maxTimeMs, String operationName) {
            long executionTime = measureExecutionTime(operation);
            if (executionTime > maxTimeMs) {
                throw new AssertionError(String.format(
                    "%s took %d ms, expected less than %d ms", 
                    operationName, executionTime, maxTimeMs));
            }
        }
    }
}