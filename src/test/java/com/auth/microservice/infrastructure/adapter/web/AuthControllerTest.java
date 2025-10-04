package com.auth.microservice.infrastructure.adapter.web;

import com.auth.microservice.application.command.AuthenticateCommand;
import com.auth.microservice.application.command.RegisterUserCommand;
import com.auth.microservice.application.result.AuthenticationResult;
import com.auth.microservice.application.result.RegistrationResult;
import com.auth.microservice.common.cqrs.CommandBus;
import com.auth.microservice.common.cqrs.QueryBus;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import java.time.LocalDateTime;
import com.auth.microservice.domain.service.JWTService;
import com.auth.microservice.domain.service.RateLimitService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AuthController.
 * Tests the REST endpoints and CQRS integration.
 */
@ExtendWith(VertxExtension.class)
class AuthControllerTest {
    
    @Mock
    private CommandBus commandBus;
    
    @Mock
    private QueryBus queryBus;
    
    @Mock
    private RateLimitService rateLimitService;
    
    private AuthController authController;
    private WebClient webClient;
    private int port;
    
    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        MockitoAnnotations.openMocks(this);
        
        authController = new AuthController(commandBus, queryBus, rateLimitService);
        webClient = WebClient.create(vertx);
        
        // Create router and configure routes
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        authController.configureRoutes(router);
        
        // Start HTTP server
        port = 8081; // Use different port for tests
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(port)
            .onComplete(testContext.succeedingThenComplete());
    }
    
    @Test
    void testLoginEndpoint_Success(Vertx vertx, VertxTestContext testContext) {
        // Arrange
        User mockUser = createMockUser();
        JWTService.TokenPair mockTokenPair = new JWTService.TokenPair(
            "access-token",
            "refresh-token",
            LocalDateTime.now().plusHours(1),
            LocalDateTime.now().plusDays(7)
        );
        AuthenticationResult mockResult = AuthenticationResult.success(mockUser, mockTokenPair);
        
        when(commandBus.<AuthenticationResult>send(any(AuthenticateCommand.class)))
            .thenReturn(Future.succeededFuture(mockResult));
        
        JsonObject loginRequest = new JsonObject()
            .put("usernameOrEmail", "test@example.com")
            .put("password", "TestPassword123!");
        
        // Act & Assert
        webClient.request(HttpMethod.POST, port, "localhost", "/auth/login")
            .sendJsonObject(loginRequest)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(200, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertTrue(responseBody.getBoolean("success"));
                
                JsonObject data = responseBody.getJsonObject("data");
                assertNotNull(data);
                assertEquals("access-token", data.getString("accessToken"));
                assertEquals("refresh-token", data.getString("refreshToken"));
                assertEquals("Bearer", data.getString("tokenType"));
                
                JsonObject user = data.getJsonObject("user");
                assertNotNull(user);
                assertEquals("test@example.com", user.getString("email"));
                assertEquals("John", user.getString("firstName"));
                assertEquals("Doe", user.getString("lastName"));
                
                testContext.completeNow();
            })));
    }
    
    @Test
    void testLoginEndpoint_WithUsername_Success(Vertx vertx, VertxTestContext testContext) {
        // Arrange
        User mockUser = createMockUser();
        JWTService.TokenPair mockTokenPair = new JWTService.TokenPair(
            "access-token",
            "refresh-token",
            LocalDateTime.now().plusHours(1),
            LocalDateTime.now().plusDays(7)
        );
        AuthenticationResult mockResult = AuthenticationResult.success(mockUser, mockTokenPair);
        
        when(commandBus.<AuthenticationResult>send(any(AuthenticateCommand.class)))
            .thenReturn(Future.succeededFuture(mockResult));
        
        JsonObject loginRequest = new JsonObject()
            .put("usernameOrEmail", "testuser")
            .put("password", "TestPassword123!");
        
        // Act & Assert
        webClient.request(HttpMethod.POST, port, "localhost", "/auth/login")
            .sendJsonObject(loginRequest)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(200, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertTrue(responseBody.getBoolean("success"));
                
                JsonObject data = responseBody.getJsonObject("data");
                assertNotNull(data);
                assertEquals("access-token", data.getString("accessToken"));
                assertEquals("refresh-token", data.getString("refreshToken"));
                assertEquals("Bearer", data.getString("tokenType"));
                
                JsonObject user = data.getJsonObject("user");
                assertNotNull(user);
                assertEquals("test@example.com", user.getString("email"));
                assertEquals("John", user.getString("firstName"));
                assertEquals("Doe", user.getString("lastName"));
                
                testContext.completeNow();
            })));
    }

    @Test
    void testLoginEndpoint_InvalidRequest(Vertx vertx, VertxTestContext testContext) {
        // Arrange
        JsonObject invalidRequest = new JsonObject()
            .put("usernameOrEmail", "invalid-email")
            .put("password", ""); // Empty password
        
        // Act & Assert
        webClient.request(HttpMethod.POST, port, "localhost", "/auth/login")
            .sendJsonObject(invalidRequest)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(400, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertFalse(responseBody.getBoolean("success"));
                
                JsonObject error = responseBody.getJsonObject("error");
                assertNotNull(error);
                assertEquals("VALIDATION_ERROR", error.getString("code"));
                
                testContext.completeNow();
            })));
    }

    @Test
    void testLoginEndpoint_MissingUsernameOrEmail(Vertx vertx, VertxTestContext testContext) {
        // Arrange
        JsonObject invalidRequest = new JsonObject()
            .put("password", "TestPassword123!"); // Missing usernameOrEmail
        
        // Act & Assert
        webClient.request(HttpMethod.POST, port, "localhost", "/auth/login")
            .sendJsonObject(invalidRequest)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(400, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertFalse(responseBody.getBoolean("success"));
                
                JsonObject error = responseBody.getJsonObject("error");
                assertNotNull(error);
                assertEquals("VALIDATION_ERROR", error.getString("code"));
                
                testContext.completeNow();
            })));
    }
    
    @Test
    void testRegisterEndpoint_Success(Vertx vertx, VertxTestContext testContext) {
        // Arrange
        User mockUser = createMockUser();
        RegistrationResult mockResult = RegistrationResult.success(mockUser);
        
        when(commandBus.<RegistrationResult>send(any(RegisterUserCommand.class)))
            .thenReturn(Future.succeededFuture(mockResult));
        
        JsonObject registerRequest = new JsonObject()
            .put("email", "newuser@example.com")
            .put("password", "NewPassword123!")
            .put("firstName", "Jane")
            .put("lastName", "Smith");
        
        // Act & Assert
        webClient.request(HttpMethod.POST, port, "localhost", "/auth/register")
            .sendJsonObject(registerRequest)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(201, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertTrue(responseBody.getBoolean("success"));
                
                JsonObject data = responseBody.getJsonObject("data");
                assertNotNull(data);
                assertEquals("test@example.com", data.getString("email"));
                assertEquals("John", data.getString("firstName"));
                assertEquals("Doe", data.getString("lastName"));
                
                testContext.completeNow();
            })));
    }
    
    @Test
    void testRegisterEndpoint_InvalidPassword(Vertx vertx, VertxTestContext testContext) {
        // Arrange
        JsonObject registerRequest = new JsonObject()
            .put("email", "newuser@example.com")
            .put("password", "weak") // Weak password
            .put("firstName", "Jane")
            .put("lastName", "Smith");
        
        // Act & Assert
        webClient.request(HttpMethod.POST, port, "localhost", "/auth/register")
            .sendJsonObject(registerRequest)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(400, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertFalse(responseBody.getBoolean("success"));
                
                JsonObject error = responseBody.getJsonObject("error");
                assertNotNull(error);
                assertEquals("VALIDATION_ERROR", error.getString("code"));
                
                testContext.completeNow();
            })));
    }
    
    @Test
    void testRefreshEndpoint_Success(Vertx vertx, VertxTestContext testContext) {
        // Arrange
        JWTService.TokenPair mockTokenPair = new JWTService.TokenPair(
            "new-access-token",
            "new-refresh-token",
            LocalDateTime.now().plusHours(1),
            LocalDateTime.now().plusDays(7)
        );
        
        when(commandBus.<JWTService.TokenPair>send(any()))
            .thenReturn(Future.succeededFuture(mockTokenPair));
        
        JsonObject refreshRequest = new JsonObject()
            .put("refreshToken", "valid-refresh-token");
        
        // Act & Assert
        webClient.request(HttpMethod.POST, port, "localhost", "/auth/refresh")
            .sendJsonObject(refreshRequest)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(200, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertTrue(responseBody.getBoolean("success"));
                
                JsonObject data = responseBody.getJsonObject("data");
                assertNotNull(data);
                assertEquals("new-access-token", data.getString("accessToken"));
                assertEquals("new-refresh-token", data.getString("refreshToken"));
                assertEquals("Bearer", data.getString("tokenType"));
                
                testContext.completeNow();
            })));
    }
    
    @Test
    void testRefreshEndpoint_MissingToken(Vertx vertx, VertxTestContext testContext) {
        // Arrange
        JsonObject refreshRequest = new JsonObject(); // Missing refreshToken
        
        // Act & Assert
        webClient.request(HttpMethod.POST, port, "localhost", "/auth/refresh")
            .sendJsonObject(refreshRequest)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(400, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertFalse(responseBody.getBoolean("success"));
                
                testContext.completeNow();
            })));
    }
    
    @Test
    void testLogoutEndpoint_MissingAuthHeader(Vertx vertx, VertxTestContext testContext) {
        // Act & Assert
        webClient.request(HttpMethod.POST, port, "localhost", "/auth/logout")
            .send()
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(401, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertFalse(responseBody.getBoolean("success"));
                
                JsonObject error = responseBody.getJsonObject("error");
                assertNotNull(error);
                assertEquals("UNAUTHORIZED", error.getString("code"));
                
                testContext.completeNow();
            })));
    }
    
    private User createMockUser() {
        try {
            User user = new User(
                UUID.randomUUID(),
                "testuser",
                new Email("test@example.com"),
                "hashedPassword",
                "John",
                "Doe",
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
            );
            
            // Add a mock role
            Role userRole = new Role(UUID.randomUUID(), "USER", "Standard user role", LocalDateTime.now());
            user.addRole(userRole);
            
            return user;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create mock user", e);
        }
    }
}