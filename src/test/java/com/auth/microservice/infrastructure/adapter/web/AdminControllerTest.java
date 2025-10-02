package com.auth.microservice.infrastructure.adapter.web;

import com.auth.microservice.application.command.AssignRoleCommand;
import com.auth.microservice.application.command.CreateRoleCommand;
import com.auth.microservice.application.command.UpdateRoleCommand;
import com.auth.microservice.application.query.GetRolesQuery;
import com.auth.microservice.application.query.GetUsersQuery;
import com.auth.microservice.application.result.RoleAssignmentResult;
import com.auth.microservice.application.result.RoleCreationResult;
import com.auth.microservice.application.result.RoleUpdateResult;
import com.auth.microservice.common.cqrs.CommandBus;
import com.auth.microservice.common.cqrs.QueryBus;
import com.auth.microservice.domain.exception.RoleNotFoundException;
import com.auth.microservice.domain.exception.UserNotFoundException;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.model.valueobject.Email;
import com.auth.microservice.infrastructure.adapter.web.middleware.AuthenticationMiddleware;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdminController.
 * Tests all administrative endpoints with proper authorization and CQRS integration.
 */
@ExtendWith(VertxExtension.class)
class AdminControllerTest {
    
    @Mock
    private CommandBus commandBus;
    
    @Mock
    private QueryBus queryBus;
    
    @Mock
    private AuthenticationMiddleware authenticationMiddleware;
    
    private AdminController adminController;
    private WebClient webClient;
    private int port;
    
    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        MockitoAnnotations.openMocks(this);
        
        adminController = new AdminController(commandBus, queryBus, authenticationMiddleware);
        
        // Create router and configure routes
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        
        // Mock authentication middleware to set user context
        when(authenticationMiddleware.handle(any())).thenAnswer(invocation -> {
            io.vertx.ext.web.RoutingContext context = invocation.getArgument(0);
            // Simulate authenticated admin user
            context.put("user", new JsonObject()
                .put("userId", "admin-user-id")
                .put("email", "admin@example.com")
                .put("roles", new JsonArray().add("ADMIN")));
            context.next();
            return null;
        });
        
        adminController.configureRoutes(router);
        
        // Start test server
        port = 8080 + (int) (Math.random() * 1000);
        webClient = WebClient.create(vertx);
        
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(port)
            .onComplete(testContext.succeedingThenComplete());
    }
    
    @Test
    void testCreateRole_Success(Vertx vertx, VertxTestContext testContext) {
        // Arrange
        UUID roleId = UUID.randomUUID();
        Role mockRole = createMockRole(roleId, "MODERATOR", "Moderator role");
        RoleCreationResult successResult = RoleCreationResult.success(mockRole);
        
        when(commandBus.<RoleCreationResult>send(any(CreateRoleCommand.class)))
            .thenReturn(Future.succeededFuture(successResult));
        
        JsonObject requestBody = new JsonObject()
            .put("name", "MODERATOR")
            .put("description", "Moderator role")
            .put("permissions", new JsonArray().add("content:read").add("content:update"));
        
        // Act & Assert
        webClient.post(port, "localhost", "/admin/roles")
            .putHeader("Content-Type", "application/json")
            .putHeader("Authorization", "Bearer mock-admin-token")
            .sendJsonObject(requestBody)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(201, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertTrue(responseBody.getBoolean("success"));
                
                JsonObject roleData = responseBody.getJsonObject("data");
                assertEquals(roleId.toString(), roleData.getString("id"));
                assertEquals("MODERATOR", roleData.getString("name"));
                assertEquals("Moderator role", roleData.getString("description"));
                
                // Verify command was sent
                verify(commandBus).send(any(CreateRoleCommand.class));
                
                testContext.completeNow();
            })));
    }
    
    @Test
    void testCreateRole_ValidationError(Vertx vertx, VertxTestContext testContext) {
        // Arrange - missing required name field
        JsonObject requestBody = new JsonObject()
            .put("description", "Role without name");
        
        // Act & Assert
        webClient.post(port, "localhost", "/admin/roles")
            .putHeader("Content-Type", "application/json")
            .putHeader("Authorization", "Bearer mock-admin-token")
            .sendJsonObject(requestBody)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(400, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertFalse(responseBody.getBoolean("success"));
                assertEquals("VALIDATION_ERROR", responseBody.getJsonObject("error").getString("code"));
                
                // Verify command was not sent
                verify(commandBus, never()).send(any(CreateRoleCommand.class));
                
                testContext.completeNow();
            })));
    }
    
    @Test
    void testUpdateRole_Success(Vertx vertx, VertxTestContext testContext) {
        // Arrange
        UUID roleId = UUID.randomUUID();
        Role mockRole = createMockRole(roleId, "MODERATOR", "Updated moderator role");
        RoleUpdateResult successResult = RoleUpdateResult.success(mockRole);
        
        when(commandBus.<RoleUpdateResult>send(any(UpdateRoleCommand.class)))
            .thenReturn(Future.succeededFuture(successResult));
        
        JsonObject requestBody = new JsonObject()
            .put("description", "Updated moderator role");
        
        // Act & Assert
        webClient.put(port, "localhost", "/admin/roles/" + roleId)
            .putHeader("Content-Type", "application/json")
            .putHeader("Authorization", "Bearer mock-admin-token")
            .sendJsonObject(requestBody)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(200, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertTrue(responseBody.getBoolean("success"));
                
                JsonObject roleData = responseBody.getJsonObject("data");
                assertEquals(roleId.toString(), roleData.getString("id"));
                assertEquals("Updated moderator role", roleData.getString("description"));
                
                // Verify command was sent
                verify(commandBus).send(any(UpdateRoleCommand.class));
                
                testContext.completeNow();
            })));
    }
    
    @Test
    void testUpdateRole_RoleNotFound(Vertx vertx, VertxTestContext testContext) {
        // Arrange
        UUID roleId = UUID.randomUUID();
        
        when(commandBus.<RoleUpdateResult>send(any(UpdateRoleCommand.class)))
            .thenReturn(Future.failedFuture(new RoleNotFoundException("Role not found")));
        
        JsonObject requestBody = new JsonObject()
            .put("description", "Updated description");
        
        // Act & Assert
        webClient.put(port, "localhost", "/admin/roles/" + roleId)
            .putHeader("Content-Type", "application/json")
            .putHeader("Authorization", "Bearer mock-admin-token")
            .sendJsonObject(requestBody)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(404, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertFalse(responseBody.getBoolean("success"));
                assertEquals("NOT_FOUND", responseBody.getJsonObject("error").getString("code"));
                
                testContext.completeNow();
            })));
    }
    
    @Test
    void testGetRoles_Success(Vertx vertx, VertxTestContext testContext) {
        // Arrange
        List<Role> mockRoles = List.of(
            createMockRole(UUID.randomUUID(), "ADMIN", "Administrator role"),
            createMockRole(UUID.randomUUID(), "USER", "Regular user role")
        );
        
        when(queryBus.<List<Role>>send(any(GetRolesQuery.class)))
            .thenReturn(Future.succeededFuture(mockRoles));
        
        // Act & Assert
        webClient.get(port, "localhost", "/admin/roles?page=0&size=10&includePermissions=true")
            .putHeader("Authorization", "Bearer mock-admin-token")
            .send()
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(200, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertTrue(responseBody.getBoolean("success"));
                
                JsonArray rolesData = responseBody.getJsonArray("data");
                assertEquals(2, rolesData.size());
                
                JsonObject firstRole = rolesData.getJsonObject(0);
                assertEquals("ADMIN", firstRole.getString("name"));
                assertEquals("Administrator role", firstRole.getString("description"));
                
                // Verify pagination info
                JsonObject pagination = responseBody.getJsonObject("pagination");
                assertEquals(0, pagination.getInteger("page"));
                assertEquals(10, pagination.getInteger("size"));
                
                // Verify query was sent
                verify(queryBus).send(any(GetRolesQuery.class));
                
                testContext.completeNow();
            })));
    }
    
    @Test
    void testAssignRoleToUser_Success(Vertx vertx, VertxTestContext testContext) {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        
        RoleAssignmentResult successResult = new RoleAssignmentResult(
            userId.toString(), roleId.toString(), "MODERATOR", LocalDateTime.now(), true, null
        );
        
        when(commandBus.<RoleAssignmentResult>send(any(AssignRoleCommand.class)))
            .thenReturn(Future.succeededFuture(successResult));
        
        JsonObject requestBody = new JsonObject()
            .put("roleId", roleId.toString());
        
        // Act & Assert
        webClient.post(port, "localhost", "/admin/users/" + userId + "/roles")
            .putHeader("Content-Type", "application/json")
            .putHeader("Authorization", "Bearer mock-admin-token")
            .sendJsonObject(requestBody)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(200, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertTrue(responseBody.getBoolean("success"));
                
                JsonObject assignmentData = responseBody.getJsonObject("data");
                assertEquals(userId.toString(), assignmentData.getString("userId"));
                assertEquals(roleId.toString(), assignmentData.getString("roleId"));
                assertEquals("MODERATOR", assignmentData.getString("roleName"));
                
                // Verify command was sent
                verify(commandBus).send(any(AssignRoleCommand.class));
                
                testContext.completeNow();
            })));
    }
    
    @Test
    void testAssignRoleToUser_UserNotFound(Vertx vertx, VertxTestContext testContext) {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        
        when(commandBus.<RoleAssignmentResult>send(any(AssignRoleCommand.class)))
            .thenReturn(Future.failedFuture(new UserNotFoundException("User not found")));
        
        JsonObject requestBody = new JsonObject()
            .put("roleId", roleId.toString());
        
        // Act & Assert
        webClient.post(port, "localhost", "/admin/users/" + userId + "/roles")
            .putHeader("Content-Type", "application/json")
            .putHeader("Authorization", "Bearer mock-admin-token")
            .sendJsonObject(requestBody)
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(404, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertFalse(responseBody.getBoolean("success"));
                assertEquals("NOT_FOUND", responseBody.getJsonObject("error").getString("code"));
                
                testContext.completeNow();
            })));
    }
    
    @Test
    void testGetUsersReport_Success(Vertx vertx, VertxTestContext testContext) {
        // Arrange
        List<User> mockUsers = List.of(
            createMockUser(UUID.randomUUID(), "user1@example.com", true),
            createMockUser(UUID.randomUUID(), "user2@example.com", false)
        );
        
        when(queryBus.<List<User>>send(any(GetUsersQuery.class)))
            .thenReturn(Future.succeededFuture(mockUsers));
        
        // Act & Assert
        webClient.get(port, "localhost", "/admin/reports/users?includeInactive=true")
            .putHeader("Authorization", "Bearer mock-admin-token")
            .send()
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(200, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertTrue(responseBody.getBoolean("success"));
                
                JsonObject reportData = responseBody.getJsonObject("data");
                assertEquals("users", reportData.getString("reportType"));
                assertTrue(reportData.getBoolean("includeInactive"));
                
                JsonObject summary = reportData.getJsonObject("summary");
                assertEquals(2, summary.getInteger("totalUsers"));
                assertEquals(1, summary.getInteger("activeUsers"));
                assertEquals(1, summary.getInteger("inactiveUsers"));
                
                // Verify query was sent
                verify(queryBus).send(any(GetUsersQuery.class));
                
                testContext.completeNow();
            })));
    }
    
    @Test
    void testGetAuthenticationMetrics_Success(Vertx vertx, VertxTestContext testContext) {
        // Act & Assert - This endpoint returns mock data, so no mocking needed
        webClient.get(port, "localhost", "/admin/metrics/authentication")
            .putHeader("Authorization", "Bearer mock-admin-token")
            .send()
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(200, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertTrue(responseBody.getBoolean("success"));
                
                JsonObject metricsData = responseBody.getJsonObject("data");
                assertEquals("authentication", metricsData.getString("metricsType"));
                assertEquals("last24Hours", metricsData.getString("timeRange"));
                
                JsonObject summary = metricsData.getJsonObject("summary");
                assertTrue(summary.containsKey("totalLoginAttempts"));
                assertTrue(summary.containsKey("successfulLogins"));
                assertTrue(summary.containsKey("failedLogins"));
                assertTrue(summary.containsKey("successRate"));
                
                assertTrue(metricsData.containsKey("hourlyBreakdown"));
                assertTrue(metricsData.containsKey("topCountries"));
                assertTrue(metricsData.containsKey("failureReasons"));
                
                testContext.completeNow();
            })));
    }
    
    @Test
    void testInvalidUuidParameter(Vertx vertx, VertxTestContext testContext) {
        // Act & Assert
        webClient.put(port, "localhost", "/admin/roles/invalid-uuid")
            .putHeader("Content-Type", "application/json")
            .putHeader("Authorization", "Bearer mock-admin-token")
            .sendJsonObject(new JsonObject().put("description", "Test"))
            .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(400, response.statusCode());
                
                JsonObject responseBody = response.bodyAsJsonObject();
                assertFalse(responseBody.getBoolean("success"));
                assertEquals("VALIDATION_ERROR", responseBody.getJsonObject("error").getString("code"));
                assertTrue(responseBody.getJsonObject("error").getString("message").contains("UUID"));
                
                testContext.completeNow();
            })));
    }
    
    // Helper methods
    
    private Role createMockRole(UUID id, String name, String description) {
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(id);
        when(role.getName()).thenReturn(name);
        when(role.getDescription()).thenReturn(description);
        when(role.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(role.getPermissions()).thenReturn(Set.of());
        return role;
    }
    
    private User createMockUser(UUID id, String email, boolean isActive) {
        User user = mock(User.class);
        Email emailObj = mock(Email.class);
        when(emailObj.getValue()).thenReturn(email);
        
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(emailObj);
        when(user.isActive()).thenReturn(isActive);
        when(user.getFirstName()).thenReturn("Test");
        when(user.getLastName()).thenReturn("User");
        when(user.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(user.getUpdatedAt()).thenReturn(LocalDateTime.now());
        when(user.getRoles()).thenReturn(Set.of());
        return user;
    }
}