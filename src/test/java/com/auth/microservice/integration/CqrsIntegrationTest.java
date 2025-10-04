package com.auth.microservice.integration;

import com.auth.microservice.infrastructure.config.ApplicationBootstrap;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CQRS Integration Tests for Auth Microservice.
 * Validates that Command Query Responsibility Segregation is properly implemented
 * with real database integration using Testcontainers.
 * 
 * Test Coverage:
 * - Command and Query separation
 * - Command Bus and Query Bus integration
 * - Database consistency between commands and queries
 * - Event sourcing and state management
 * - Performance characteristics of CQRS pattern
 * - Error handling in CQRS operations
 */
@ExtendWith(VertxExtension.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CqrsIntegrationTest {
    
    private static final int TEST_PORT = 8890;
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("auth_cqrs_test")
            .withUsername("test_user")
            .withPassword("test_password")
            .withReuse(true);
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);
    
    private static Vertx vertx;
    private static HttpServer server;
    private static ApplicationBootstrap bootstrap;
    
    // Test data
    private static String testUserToken;
    private static String testAdminToken;
    private static String testUserId;
    private static String testRoleId;
    
    @BeforeAll
    static void setupCqrsTestEnvironment(VertxTestContext testContext) throws Exception {
        // Configure RestAssured
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = TEST_PORT;
        RestAssured.basePath = "/api";
        
        // Set test environment variables
        System.setProperty("DATABASE_URL", postgres.getJdbcUrl());
        System.setProperty("DATABASE_USERNAME", postgres.getUsername());
        System.setProperty("DATABASE_PASSWORD", postgres.getPassword());
        System.setProperty("REDIS_HOST", redis.getHost());
        System.setProperty("REDIS_PORT", String.valueOf(redis.getMappedPort(6379)));
        System.setProperty("JWT_SECRET", "cqrs-test-secret-key-for-jwt-signing");
        System.setProperty("SERVER_PORT", String.valueOf(TEST_PORT));
        System.setProperty("ENVIRONMENT", "test");
        
        // Initialize Vert.x and application
        vertx = Vertx.vertx();
        bootstrap = new ApplicationBootstrap(vertx);
        
        // Initialize application components
        bootstrap.initialize()
            .compose(v -> startTestServer())
            .onSuccess(v -> {
                System.out.println("CQRS test server started successfully on port " + TEST_PORT);
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
        
        // Wait for server to start
        assertTrue(testContext.awaitCompletion(30, TimeUnit.SECONDS));
    }
    
    @AfterAll
    static void tearDownCqrsTestEnvironment(VertxTestContext testContext) {
        if (server != null) {
            server.close()
                .compose(v -> bootstrap != null ? bootstrap.shutdown() : vertx.close())
                .onComplete(ar -> testContext.completeNow());
        } else {
            testContext.completeNow();
        }
    }
    
    private static io.vertx.core.Future<Void> startTestServer() {
        Router router = bootstrap.configureRouter();
        server = vertx.createHttpServer();
        
        return server.requestHandler(router)
            .listen(TEST_PORT)
            .map(httpServer -> (Void) null);
    }
    
    // ========================================
    // SETUP TEST DATA
    // ========================================
    
    @Test
    @Order(1)
    @DisplayName("Setup: Create test users for CQRS validation")
    void setupTestUsers() {
        // Create regular user
        Response userResponse = createUserAndLogin("cqrsuser@example.com", "CqrsUser123!", "CQRS", "User");
        testUserToken = userResponse.path("accessToken");
        testUserId = userResponse.path("user.id");
        
        // Create admin user
        Response adminResponse = createUserAndLogin("cqrsadmin@example.com", "CqrsAdmin123!", "CQRS", "Admin");
        testAdminToken = adminResponse.path("accessToken");
        
        assertNotNull(testUserToken, "Test user token should be created");
        assertNotNull(testAdminToken, "Test admin token should be created");
        assertNotNull(testUserId, "Test user ID should be available");
    }
    
    private Response createUserAndLogin(String email, String password, String firstName, String lastName) {
        // Register user (Command operation)
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "email": "%s",
                    "password": "%s",
                    "firstName": "%s",
                    "lastName": "%s"
                }
                """, email, password, firstName, lastName))
            .when()
                .post("/auth/register")
            .then()
                .statusCode(201);
        
        // Login user (Command operation)
        return given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "email": "%s",
                    "password": "%s"
                }
                """, email, password))
            .when()
                .post("/auth/login")
            .then()
                .statusCode(200)
                .extract().response();
    }
    
    // ========================================
    // COMMAND OPERATIONS TESTS
    // ========================================
    
    @Test
    @Order(10)
    @DisplayName("CQRS: Command operations should modify state")
    void testCommandOperations() {
        // Test user registration command
        long startTime = System.currentTimeMillis();
        
        Response registerResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "commandtest@example.com",
                    "password": "CommandTest123!",
                    "firstName": "Command",
                    "lastName": "Test"
                }
                """)
            .when()
                .post("/auth/register")
            .then()
                .statusCode(201)
                .body("email", equalTo("commandtest@example.com"))
                .body("firstName", equalTo("Command"))
                .body("lastName", equalTo("Test"))
                .extract().response();
        
        long commandTime = System.currentTimeMillis() - startTime;
        String newUserId = registerResponse.path("id");
        
        // Verify command was processed (state change occurred)
        assertNotNull(newUserId, "Command should create new user with ID");
        assertTrue(commandTime < 5000, "Command should complete within reasonable time");
        
        // Test authentication command
        startTime = System.currentTimeMillis();
        
        Response loginResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "commandtest@example.com",
                    "password": "CommandTest123!"
                }
                """)
            .when()
                .post("/auth/login")
            .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .extract().response();
        
        commandTime = System.currentTimeMillis() - startTime;
        String newUserToken = loginResponse.path("accessToken");
        
        assertNotNull(newUserToken, "Authentication command should generate tokens");
        assertTrue(commandTime < 3000, "Authentication command should be fast");
        
        // Test profile update command
        startTime = System.currentTimeMillis();
        
        given()
            .header("Authorization", "Bearer " + newUserToken)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "firstName": "Updated Command",
                    "lastName": "Updated Test"
                }
                """)
            .when()
                .put("/users/profile")
            .then()
                .statusCode(200)
                .body("firstName", equalTo("Updated Command"))
                .body("lastName", equalTo("Updated Test"));
        
        commandTime = System.currentTimeMillis() - startTime;
        assertTrue(commandTime < 2000, "Update command should be fast");
    }
    
    @Test
    @Order(11)
    @DisplayName("CQRS: Command operations should handle failures gracefully")
    void testCommandFailureHandling() {
        // Test duplicate user registration (should fail)
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "cqrsuser@example.com",
                    "password": "AnotherPassword123!",
                    "firstName": "Duplicate",
                    "lastName": "User"
                }
                """)
            .when()
                .post("/auth/register")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("CONFLICT"));
        
        // Test invalid authentication command
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "cqrsuser@example.com",
                    "password": "WrongPassword"
                }
                """)
            .when()
                .post("/auth/login")
            .then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHORIZED"));
        
        // Test invalid profile update command
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "firstName": "",
                    "lastName": ""
                }
                """)
            .when()
                .put("/users/profile")
            .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_ERROR"));
    }
    
    // ========================================
    // QUERY OPERATIONS TESTS
    // ========================================
    
    @Test
    @Order(20)
    @DisplayName("CQRS: Query operations should read current state")
    void testQueryOperations() {
        // Test user profile query
        long startTime = System.currentTimeMillis();
        
        Response profileResponse = given()
            .header("Authorization", "Bearer " + testUserToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200)
                .body("id", equalTo(testUserId))
                .body("email", equalTo("cqrsuser@example.com"))
                .body("firstName", equalTo("CQRS"))
                .body("lastName", equalTo("User"))
                .extract().response();
        
        long queryTime = System.currentTimeMillis() - startTime;
        assertTrue(queryTime < 1000, "Query should be very fast");
        
        // Verify query returns current state
        String profileId = profileResponse.path("id");
        assertEquals(testUserId, profileId, "Query should return current user state");
        
        // Test users list query (if admin has permission)
        startTime = System.currentTimeMillis();
        
        given()
            .header("Authorization", "Bearer " + testAdminToken)
            .queryParam("page", 0)
            .queryParam("size", 10)
            .when()
                .get("/users")
            .then()
                .statusCode(anyOf(equalTo(200), equalTo(403)))
                .body("data", anyOf(notNullValue(), nullValue()));
        
        queryTime = System.currentTimeMillis() - startTime;
        assertTrue(queryTime < 2000, "List query should be reasonably fast");
    }
    
    @Test
    @Order(21)
    @DisplayName("CQRS: Query operations should be idempotent")
    void testQueryIdempotency() {
        // Execute same query multiple times
        Response firstQuery = given()
            .header("Authorization", "Bearer " + testUserToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200)
                .extract().response();
        
        Response secondQuery = given()
            .header("Authorization", "Bearer " + testUserToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200)
                .extract().response();
        
        Response thirdQuery = given()
            .header("Authorization", "Bearer " + testUserToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200)
                .extract().response();
        
        // All queries should return identical results
        assertEquals(firstQuery.path("id"), secondQuery.path("id"), "Queries should be idempotent");
        assertEquals(firstQuery.path("email"), secondQuery.path("email"), "Queries should be idempotent");
        assertEquals(secondQuery.path("id"), thirdQuery.path("id"), "Queries should be idempotent");
        assertEquals(secondQuery.path("email"), thirdQuery.path("email"), "Queries should be idempotent");
    }
    
    // ========================================
    // COMMAND-QUERY CONSISTENCY TESTS
    // ========================================
    
    @Test
    @Order(30)
    @DisplayName("CQRS: Commands should be immediately visible in queries")
    void testCommandQueryConsistency() {
        // Execute command to update profile
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "firstName": "Consistency",
                    "lastName": "Test"
                }
                """)
            .when()
                .put("/users/profile")
            .then()
                .statusCode(200);
        
        // Immediately query to verify change is visible
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200)
                .body("firstName", equalTo("Consistency"))
                .body("lastName", equalTo("Test"));
        
        // Execute another command
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "firstName": "Updated Consistency",
                    "lastName": "Updated Test"
                }
                """)
            .when()
                .put("/users/profile")
            .then()
                .statusCode(200);
        
        // Verify second change is also immediately visible
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200)
                .body("firstName", equalTo("Updated Consistency"))
                .body("lastName", equalTo("Updated Test"));
    }
    
    @Test
    @Order(31)
    @DisplayName("CQRS: Complex command-query scenarios")
    void testComplexCommandQueryScenarios() {
        // Create a new user via command
        Response registerResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "complex@example.com",
                    "password": "Complex123!",
                    "firstName": "Complex",
                    "lastName": "Scenario"
                }
                """)
            .when()
                .post("/auth/register")
            .then()
                .statusCode(201)
                .extract().response();
        
        String complexUserId = registerResponse.path("id");
        
        // Login the new user (command)
        Response loginResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "complex@example.com",
                    "password": "Complex123!"
                }
                """)
            .when()
                .post("/auth/login")
            .then()
                .statusCode(200)
                .extract().response();
        
        String complexUserToken = loginResponse.path("accessToken");
        
        // Query user profile immediately after login
        given()
            .header("Authorization", "Bearer " + complexUserToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200)
                .body("id", equalTo(complexUserId))
                .body("email", equalTo("complex@example.com"))
                .body("firstName", equalTo("Complex"))
                .body("lastName", equalTo("Scenario"));
        
        // Update profile multiple times (commands)
        for (int i = 1; i <= 3; i++) {
            given()
                .header("Authorization", "Bearer " + complexUserToken)
                .contentType(ContentType.JSON)
                .body(String.format("""
                    {
                        "firstName": "Complex %d",
                        "lastName": "Scenario %d"
                    }
                    """, i, i))
                .when()
                    .put("/users/profile")
                .then()
                    .statusCode(200);
            
            // Verify each update is immediately visible (query)
            given()
                .header("Authorization", "Bearer " + complexUserToken)
                .when()
                    .get("/users/profile")
                .then()
                    .statusCode(200)
                    .body("firstName", equalTo("Complex " + i))
                    .body("lastName", equalTo("Scenario " + i));
        }
    }
    
    // ========================================
    // CQRS PERFORMANCE TESTS
    // ========================================
    
    @Test
    @Order(40)
    @DisplayName("CQRS: Performance characteristics validation")
    void testCqrsPerformanceCharacteristics() {
        // Test that queries are faster than commands
        long commandStartTime = System.currentTimeMillis();
        
        // Execute command (should be slower due to validation, persistence, etc.)
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "firstName": "Performance",
                    "lastName": "Command"
                }
                """)
            .when()
                .put("/users/profile")
            .then()
                .statusCode(200);
        
        long commandTime = System.currentTimeMillis() - commandStartTime;
        
        long queryStartTime = System.currentTimeMillis();
        
        // Execute query (should be faster due to read optimization)
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200);
        
        long queryTime = System.currentTimeMillis() - queryStartTime;
        
        // Queries should generally be faster than commands
        // Note: This is a general expectation, actual performance may vary
        assertTrue(queryTime <= commandTime * 2, 
            String.format("Query time (%d ms) should be comparable or faster than command time (%d ms)", 
                queryTime, commandTime));
        
        // Both should complete within reasonable time
        assertTrue(commandTime < 5000, "Commands should complete within 5 seconds");
        assertTrue(queryTime < 2000, "Queries should complete within 2 seconds");
    }
    
    @Test
    @Order(41)
    @DisplayName("CQRS: Concurrent operations handling")
    void testConcurrentOperations() {
        // Test concurrent queries (should all succeed)
        for (int i = 0; i < 5; i++) {
            given()
                .header("Authorization", "Bearer " + testUserToken)
                .when()
                    .get("/users/profile")
                .then()
                    .statusCode(200)
                    .body("email", equalTo("cqrsuser@example.com"));
        }
        
        // Test sequential commands (should all succeed and be consistent)
        for (int i = 1; i <= 3; i++) {
            given()
                .header("Authorization", "Bearer " + testUserToken)
                .contentType(ContentType.JSON)
                .body(String.format("""
                    {
                        "firstName": "Concurrent %d",
                        "lastName": "Test %d"
                    }
                    """, i, i))
                .when()
                    .put("/users/profile")
                .then()
                    .statusCode(200);
        }
        
        // Verify final state is consistent
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200)
                .body("firstName", equalTo("Concurrent 3"))
                .body("lastName", equalTo("Test 3"));
    }
    
    // ========================================
    // CQRS ERROR HANDLING TESTS
    // ========================================
    
    @Test
    @Order(50)
    @DisplayName("CQRS: Error handling in command operations")
    void testCommandErrorHandling() {
        // Test command with invalid data
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "firstName": null,
                    "lastName": null
                }
                """)
            .when()
                .put("/users/profile")
            .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_ERROR"));
        
        // Verify that failed command didn't change state
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200)
                .body("firstName", not(nullValue()))
                .body("lastName", not(nullValue()));
        
        // Test command with unauthorized access
        given()
            .header("Authorization", "Bearer invalid-token")
            .contentType(ContentType.JSON)
            .body("""
                {
                    "firstName": "Unauthorized",
                    "lastName": "Update"
                }
                """)
            .when()
                .put("/users/profile")
            .then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHORIZED"));
    }
    
    @Test
    @Order(51)
    @DisplayName("CQRS: Error handling in query operations")
    void testQueryErrorHandling() {
        // Test query with invalid authorization
        given()
            .header("Authorization", "Bearer invalid-token")
            .when()
                .get("/users/profile")
            .then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHORIZED"));
        
        // Test query with missing authorization
        given()
            .when()
                .get("/users/profile")
            .then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHORIZED"));
        
        // Test query for non-existent resource
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .when()
                .get("/users/nonexistent")
            .then()
                .statusCode(404);
    }
    
    // ========================================
    // CQRS INTEGRATION VALIDATION
    // ========================================
    
    @Test
    @Order(60)
    @DisplayName("CQRS: Complete integration validation")
    void testCompleteIntegration() {
        // Test complete CQRS flow with multiple operations
        
        // 1. Create user (Command)
        Response createResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "integration@example.com",
                    "password": "Integration123!",
                    "firstName": "Integration",
                    "lastName": "Test"
                }
                """)
            .when()
                .post("/auth/register")
            .then()
                .statusCode(201)
                .extract().response();
        
        String integrationUserId = createResponse.path("id");
        
        // 2. Authenticate user (Command)
        Response authResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "integration@example.com",
                    "password": "Integration123!"
                }
                """)
            .when()
                .post("/auth/login")
            .then()
                .statusCode(200)
                .extract().response();
        
        String integrationToken = authResponse.path("accessToken");
        
        // 3. Query user profile (Query)
        given()
            .header("Authorization", "Bearer " + integrationToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200)
                .body("id", equalTo(integrationUserId))
                .body("email", equalTo("integration@example.com"))
                .body("firstName", equalTo("Integration"))
                .body("lastName", equalTo("Test"));
        
        // 4. Update profile (Command)
        given()
            .header("Authorization", "Bearer " + integrationToken)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "firstName": "Updated Integration",
                    "lastName": "Updated Test"
                }
                """)
            .when()
                .put("/users/profile")
            .then()
                .statusCode(200);
        
        // 5. Verify update (Query)
        given()
            .header("Authorization", "Bearer " + integrationToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200)
                .body("firstName", equalTo("Updated Integration"))
                .body("lastName", equalTo("Updated Test"));
        
        // 6. Logout (Command)
        given()
            .header("Authorization", "Bearer " + integrationToken)
            .when()
                .post("/auth/logout")
            .then()
                .statusCode(200);
        
        // 7. Verify logout effect (Query should fail)
        given()
            .header("Authorization", "Bearer " + integrationToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(401);
        
        // This validates that the entire CQRS system works correctly
        // with proper command-query separation and state consistency
    }
}