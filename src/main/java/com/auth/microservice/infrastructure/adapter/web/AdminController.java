package com.auth.microservice.infrastructure.adapter.web;

import com.auth.microservice.application.command.AssignRoleCommand;
import com.auth.microservice.application.command.CreateRoleCommand;
import com.auth.microservice.application.command.UpdateRoleCommand;
import com.auth.microservice.application.query.GetRoleByIdQuery;
import com.auth.microservice.application.query.GetRolesQuery;
import com.auth.microservice.application.query.GetUserRolesQuery;
import com.auth.microservice.application.query.GetUsersQuery;
import com.auth.microservice.application.result.RoleAssignmentResult;
import com.auth.microservice.application.result.RoleCreationResult;
import com.auth.microservice.application.result.RoleUpdateResult;
import com.auth.microservice.common.cqrs.CommandBus;
import com.auth.microservice.common.cqrs.QueryBus;
import com.auth.microservice.domain.exception.DomainException;
import com.auth.microservice.domain.exception.RoleNotFoundException;
import com.auth.microservice.domain.exception.UserNotFoundException;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.Pagination;
import com.auth.microservice.infrastructure.adapter.web.middleware.AuthenticationMiddleware;
import com.auth.microservice.infrastructure.adapter.web.middleware.AuthorizationMiddleware;
import com.auth.microservice.infrastructure.adapter.web.response.ResponseUtil;
import com.auth.microservice.infrastructure.adapter.web.util.RequestUtil;
import com.auth.microservice.infrastructure.adapter.web.validation.RequestValidator;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * REST Controller for administrative endpoints with RBAC functions.
 * Implements CQRS pattern using CommandBus for write operations and QueryBus for read operations.
 * All endpoints require specific administrative permissions.
 * 
 * Integration example:
 * ```java
 * // In Main.java or WebConfiguration:
 * AdminController adminController = new AdminController(commandBus, queryBus, authenticationMiddleware);
 * adminController.configureRoutes(router);
 * ```
 * 
 * Endpoints:
 * - POST /admin/roles - Create new role (requires roles:create permission)
 * - PUT /admin/roles/{roleId} - Update role (requires roles:update permission)
 * - GET /admin/roles - Get all roles (requires roles:read permission)
 * - POST /admin/users/{userId}/roles - Assign role to user (requires users:assign_roles permission)
 * - GET /admin/reports/users - Get user statistics (requires reports:read permission)
 * - GET /admin/reports/roles - Get role statistics (requires reports:read permission)
 * - GET /admin/metrics/authentication - Get authentication metrics (requires metrics:read permission)
 */
public class AdminController {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);
    
    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final AuthenticationMiddleware authenticationMiddleware;
    
    public AdminController(CommandBus commandBus, QueryBus queryBus, AuthenticationMiddleware authenticationMiddleware) {
        this.commandBus = Objects.requireNonNull(commandBus, "CommandBus cannot be null");
        this.queryBus = Objects.requireNonNull(queryBus, "QueryBus cannot be null");
        this.authenticationMiddleware = Objects.requireNonNull(authenticationMiddleware, "AuthenticationMiddleware cannot be null");
    }
    
    /**
     * Configures administrative routes on the provided router.
     * All routes require authentication and specific permissions.
     * 
     * @param router the Vert.x router to configure
     */
    public void configureRoutes(Router router) {
        // Role management endpoints
        router.post("/admin/roles")
            .handler(authenticationMiddleware)
            .handler(AuthorizationMiddleware.requirePermission(queryBus, "roles", "create"))
            .handler(this::createRole);
            
        router.put("/admin/roles/:roleId")
            .handler(authenticationMiddleware)
            .handler(AuthorizationMiddleware.requirePermission(queryBus, "roles", "update"))
            .handler(this::updateRole);
            
        router.get("/admin/roles")
            .handler(authenticationMiddleware)
            .handler(AuthorizationMiddleware.requirePermission(queryBus, "roles", "read"))
            .handler(this::getRoles);
        
        // User role assignment endpoints
        router.post("/admin/users/:userId/roles")
            .handler(authenticationMiddleware)
            .handler(AuthorizationMiddleware.requirePermission(queryBus, "users", "assign_roles"))
            .handler(this::assignRoleToUser);
        
        // Administrative reports endpoints
        router.get("/admin/reports/users")
            .handler(authenticationMiddleware)
            .handler(AuthorizationMiddleware.requirePermission(queryBus, "reports", "read"))
            .handler(this::getUsersReport);
            
        router.get("/admin/reports/roles")
            .handler(authenticationMiddleware)
            .handler(AuthorizationMiddleware.requirePermission(queryBus, "reports", "read"))
            .handler(this::getRolesReport);
        
        // Administrative metrics endpoints
        router.get("/admin/metrics/authentication")
            .handler(authenticationMiddleware)
            .handler(AuthorizationMiddleware.requirePermission(queryBus, "metrics", "read"))
            .handler(this::getAuthenticationMetrics);
    }
    
    /**
     * Handles create role requests.
     * POST /admin/roles
     * 
     * Expected request body:
     * {
     *   "name": "MODERATOR",
     *   "description": "Moderator role with content management permissions",
     *   "permissions": ["content:read", "content:update", "users:read"]
     * }
     */
    public void createRole(RoutingContext context) {
        String adminUserId = AuthenticationMiddleware.getUserId(context);
        logger.debug("Processing create role request by admin: {} from IP: {}", adminUserId, RequestUtil.RequestUtil.getClientIp(context));
        
        // Validate JSON body
        RequestValidator.ValidationResult bodyValidation = RequestValidator.validateJsonBody(context);
        if (!bodyValidation.isValid()) {
            bodyValidation.sendErrorResponse(context);
            return;
        }
        
        JsonObject requestBody = bodyValidation.getData();
        
        // Validate create role request
        RequestValidator.ValidationResult roleValidation = RequestValidator.validateCreateRoleRequest(requestBody);
        if (!roleValidation.isValid()) {
            roleValidation.sendErrorResponse(context);
            return;
        }
        
        String name = requestBody.getString("name");
        String description = requestBody.getString("description");
        String clientIp = RequestUtil.RequestUtil.getClientIp(context);
        String userAgent = RequestUtil.getUserAgent(context);
        
        // Extract permissions (optional)
        Set<String> permissionNames = Set.of(); // Default empty set
        JsonArray permissionsArray = requestBody.getJsonArray("permissions");
        if (permissionsArray != null && !permissionsArray.isEmpty()) {
            permissionNames = Set.copyOf(permissionsArray.getList());
        }
        
        // Create role creation command
        CreateRoleCommand command = new CreateRoleCommand(
            adminUserId, name, description, permissionNames, clientIp, userAgent
        );
        
        // Execute creation through command bus
        commandBus.<RoleCreationResult>send(command)
            .onSuccess(result -> {
                if (result.isSuccess()) {
                    handleSuccessfulRoleCreation(context, result);
                } else {
                    handleFailedRoleCreation(context, result.getErrorMessage());
                }
            })
            .onFailure(throwable -> {
                logger.error("Create role command failed for admin: {} from IP: {}", 
                    adminUserId, clientIp, throwable);
                handleRoleCreationError(context, throwable);
            });
    }
    
    /**
     * Handles update role requests.
     * PUT /admin/roles/{roleId}
     * 
     * Expected request body:
     * {
     *   "description": "Updated role description"
     * }
     */
    public void updateRole(RoutingContext context) {
        String adminUserId = AuthenticationMiddleware.getUserId(context);
        String roleIdStr = context.pathParam("roleId");
        
        logger.debug("Processing update role request by admin: {} for role: {} from IP: {}", 
            adminUserId, roleIdStr, RequestUtil.getClientIp(context));
        
        // Validate role ID
        RequestValidator.ValidationResult roleIdValidation = RequestValidator.validateUuidParam(roleIdStr, "Role ID");
        if (!roleIdValidation.isValid()) {
            roleIdValidation.sendErrorResponse(context);
            return;
        }
        
        // Validate JSON body
        RequestValidator.ValidationResult bodyValidation = RequestValidator.validateJsonBody(context);
        if (!bodyValidation.isValid()) {
            bodyValidation.sendErrorResponse(context);
            return;
        }
        
        JsonObject requestBody = bodyValidation.getData();
        
        // Validate that description is provided
        String description = requestBody.getString("description");
        if (description == null) {
            ResponseUtil.sendError(context, 400, "VALIDATION_ERROR", "Description is required for role update");
            return;
        }
        
        if (description.length() > 500) {
            ResponseUtil.sendError(context, 400, "VALIDATION_ERROR", "Role description must not exceed 500 characters");
            return;
        }
        
        UUID roleId = UUID.fromString(roleIdStr);
        String clientIp = RequestUtil.getClientIp(context);
        String userAgent = context.request().getHeader("User-Agent");
        
        // Create role update command
        UpdateRoleCommand command = new UpdateRoleCommand(
            adminUserId, roleId, description, clientIp, userAgent
        );
        
        // Execute update through command bus
        commandBus.<RoleUpdateResult>send(command)
            .onSuccess(result -> {
                if (result.isSuccess()) {
                    handleSuccessfulRoleUpdate(context, result);
                } else {
                    handleFailedRoleUpdate(context, result.getErrorMessage());
                }
            })
            .onFailure(throwable -> {
                logger.error("Update role command failed for admin: {} targeting role: {} from IP: {}", 
                    adminUserId, roleId, clientIp, throwable);
                handleRoleUpdateError(context, throwable);
            });
    }
    
    /**
     * Handles get roles requests.
     * GET /admin/roles?page=0&size=20&includePermissions=true
     * 
     * Returns paginated list of roles with optional permissions.
     */
    public void getRoles(RoutingContext context) {
        String adminUserId = AuthenticationMiddleware.getUserId(context);
        logger.debug("Processing get roles request by admin: {} from IP: {}", adminUserId, RequestUtil.getClientIp(context));
        
        // Validate pagination parameters
        RequestValidator.ValidationResult paginationValidation = RequestValidator.validatePaginationParams(context);
        if (!paginationValidation.isValid()) {
            paginationValidation.sendErrorResponse(context);
            return;
        }
        
        JsonObject paginationParams = paginationValidation.getData();
        int page = paginationParams.getInteger("page");
        int size = paginationParams.getInteger("size");
        
        // Extract optional parameters
        boolean includePermissions = "true".equals(context.request().getParam("includePermissions"));
        
        // Create pagination object
        Pagination pagination = Pagination.of(page, size, "name", Pagination.SortDirection.ASC);
        
        // Create query
        GetRolesQuery query = new GetRolesQuery(adminUserId, pagination, includePermissions);
        
        // Execute query through query bus
        queryBus.<List<Role>>send(query)
            .onSuccess(roles -> {
                handleSuccessfulRolesRetrieval(context, roles, pagination, includePermissions);
            })
            .onFailure(throwable -> {
                logger.error("Get roles query failed for admin: {} from IP: {}", 
                    adminUserId, RequestUtil.getClientIp(context), throwable);
                handleRolesRetrievalError(context, throwable);
            });
    }
    
    /**
     * Handles assign role to user requests.
     * POST /admin/users/{userId}/roles
     * 
     * Expected request body:
     * {
     *   "roleId": "550e8400-e29b-41d4-a716-446655440000"
     * }
     */
    public void assignRoleToUser(RoutingContext context) {
        String adminUserId = AuthenticationMiddleware.getUserId(context);
        String userIdStr = context.pathParam("userId");
        
        logger.debug("Processing assign role request by admin: {} for user: {} from IP: {}", 
            adminUserId, userIdStr, RequestUtil.getClientIp(context));
        
        // Validate user ID
        RequestValidator.ValidationResult userIdValidation = RequestValidator.validateUuidParam(userIdStr, "User ID");
        if (!userIdValidation.isValid()) {
            userIdValidation.sendErrorResponse(context);
            return;
        }
        
        // Validate JSON body
        RequestValidator.ValidationResult bodyValidation = RequestValidator.validateJsonBody(context);
        if (!bodyValidation.isValid()) {
            bodyValidation.sendErrorResponse(context);
            return;
        }
        
        JsonObject requestBody = bodyValidation.getData();
        
        // Validate role ID in body
        String roleIdStr = requestBody.getString("roleId");
        if (roleIdStr == null || roleIdStr.trim().isEmpty()) {
            ResponseUtil.sendError(context, 400, "VALIDATION_ERROR", "Role ID is required");
            return;
        }
        
        RequestValidator.ValidationResult roleIdValidation = RequestValidator.validateUuidParam(roleIdStr, "Role ID");
        if (!roleIdValidation.isValid()) {
            roleIdValidation.sendErrorResponse(context);
            return;
        }
        
        String clientIp = RequestUtil.getClientIp(context);
        String userAgent = context.request().getHeader("User-Agent");
        
        // Create role assignment command
        AssignRoleCommand command = new AssignRoleCommand(
            adminUserId, userIdStr, roleIdStr, clientIp, userAgent
        );
        
        // Execute assignment through command bus
        commandBus.<RoleAssignmentResult>send(command)
            .onSuccess(result -> {
                if (result.isSuccess()) {
                    handleSuccessfulRoleAssignment(context, result);
                } else {
                    handleFailedRoleAssignment(context, result.getErrorMessage());
                }
            })
            .onFailure(throwable -> {
                logger.error("Assign role command failed for admin: {} targeting user: {} from IP: {}", 
                    adminUserId, userIdStr, clientIp, throwable);
                handleRoleAssignmentError(context, throwable);
            });
    }
    
    /**
     * Handles users report requests.
     * GET /admin/reports/users?includeInactive=false
     * 
     * Returns statistical information about users.
     */
    public void getUsersReport(RoutingContext context) {
        String adminUserId = AuthenticationMiddleware.getUserId(context);
        logger.debug("Processing users report request by admin: {} from IP: {}", adminUserId, RequestUtil.getClientIp(context));
        
        // Extract optional parameters
        boolean includeInactive = "true".equals(context.request().getParam("includeInactive"));
        
        // Create pagination for getting all users (large page size for report)
        Pagination pagination = Pagination.of(0, 1000, "createdAt", Pagination.SortDirection.DESC);
        
        // Create query to get users for report
        GetUsersQuery query = new GetUsersQuery(adminUserId, pagination, includeInactive, true, null);
        
        // Execute query through query bus
        queryBus.<List<User>>send(query)
            .onSuccess(users -> {
                handleSuccessfulUsersReport(context, users, includeInactive);
            })
            .onFailure(throwable -> {
                logger.error("Users report query failed for admin: {} from IP: {}", 
                    adminUserId, RequestUtil.getClientIp(context), throwable);
                handleUsersReportError(context, throwable);
            });
    }
    
    /**
     * Handles roles report requests.
     * GET /admin/reports/roles
     * 
     * Returns statistical information about roles and permissions.
     */
    public void getRolesReport(RoutingContext context) {
        String adminUserId = AuthenticationMiddleware.getUserId(context);
        logger.debug("Processing roles report request by admin: {} from IP: {}", adminUserId, RequestUtil.getClientIp(context));
        
        // Create pagination for getting all roles (large page size for report)
        Pagination pagination = Pagination.of(0, 1000, "name", Pagination.SortDirection.ASC);
        
        // Create query to get roles with permissions for report
        GetRolesQuery query = new GetRolesQuery(adminUserId, pagination, true);
        
        // Execute query through query bus
        queryBus.<List<Role>>send(query)
            .onSuccess(roles -> {
                handleSuccessfulRolesReport(context, roles);
            })
            .onFailure(throwable -> {
                logger.error("Roles report query failed for admin: {} from IP: {}", 
                    adminUserId, RequestUtil.getClientIp(context), throwable);
                handleRolesReportError(context, throwable);
            });
    }
    
    /**
     * Handles authentication metrics requests.
     * GET /admin/metrics/authentication
     * 
     * Returns authentication-related metrics and statistics.
     * Note: This is a simplified implementation. In a real system, you would
     * query dedicated metrics storage or monitoring systems.
     */
    public void getAuthenticationMetrics(RoutingContext context) {
        String adminUserId = AuthenticationMiddleware.getUserId(context);
        logger.debug("Processing authentication metrics request by admin: {} from IP: {}", adminUserId, RequestUtil.getClientIp(context));
        
        // For this implementation, we'll return basic metrics
        // In a real system, you would query metrics from monitoring systems like Prometheus
        JsonObject metrics = createAuthenticationMetrics();
        
        ResponseUtil.sendSuccess(context, metrics);
        
        logger.info("Authentication metrics retrieved by admin: {} from IP: {}", 
            adminUserId, RequestUtil.getClientIp(context));
    }
    
    // Success handlers
    
    private void handleSuccessfulRoleCreation(RoutingContext context, RoleCreationResult result) {
        JsonObject roleResponse = createRoleResponse(result.getRole());
        String location = "/admin/roles/" + result.getRole().getId();
        ResponseUtil.sendCreated(context, roleResponse, location);
        
        logger.info("Role created successfully: {} by admin from IP: {}", 
            result.getRole().getName(), RequestUtil.getClientIp(context));
    }
    
    private void handleSuccessfulRoleUpdate(RoutingContext context, RoleUpdateResult result) {
        JsonObject roleResponse = createRoleResponse(result.getRole());
        ResponseUtil.sendSuccess(context, roleResponse);
        
        logger.info("Role updated successfully: {} by admin from IP: {}", 
            result.getRole().getId(), RequestUtil.getClientIp(context));
    }
    
    private void handleSuccessfulRolesRetrieval(RoutingContext context, List<Role> roles, 
                                              Pagination pagination, boolean includePermissions) {
        JsonArray rolesArray = new JsonArray();
        roles.forEach(role -> rolesArray.add(createRoleResponse(role, includePermissions)));
        
        // For simplicity, we'll use the current page size as total elements
        // In a real implementation, you'd get the actual count from the repository
        long totalElements = roles.size();
        
        ResponseUtil.sendPaginatedSuccess(context, rolesArray, 
            pagination.getPage(), pagination.getSize(), totalElements);
        
        logger.info("Roles list retrieved successfully by admin from IP: {} - {} roles returned", 
            RequestUtil.getClientIp(context), roles.size());
    }
    
    private void handleSuccessfulRoleAssignment(RoutingContext context, RoleAssignmentResult result) {
        JsonObject assignmentResponse = new JsonObject()
            .put("userId", result.getUserId())
            .put("roleId", result.getRoleId())
            .put("roleName", result.getRoleName())
            .put("assignedAt", result.getAssignedAt().toString())
            .put("message", "Role assigned successfully");
        
        ResponseUtil.sendSuccess(context, assignmentResponse);
        
        logger.info("Role {} assigned to user {} successfully by admin from IP: {}", 
            result.getRoleName(), result.getUserId(), RequestUtil.getClientIp(context));
    }
    
    private void handleSuccessfulUsersReport(RoutingContext context, List<User> users, boolean includeInactive) {
        JsonObject report = createUsersReport(users, includeInactive);
        ResponseUtil.sendSuccess(context, report);
        
        logger.info("Users report generated successfully by admin from IP: {} - {} users analyzed", 
            RequestUtil.getClientIp(context), users.size());
    }
    
    private void handleSuccessfulRolesReport(RoutingContext context, List<Role> roles) {
        JsonObject report = createRolesReport(roles);
        ResponseUtil.sendSuccess(context, report);
        
        logger.info("Roles report generated successfully by admin from IP: {} - {} roles analyzed", 
            RequestUtil.getClientIp(context), roles.size());
    }
    
    // Error handlers
    
    private void handleFailedRoleCreation(RoutingContext context, String message) {
        ResponseUtil.sendError(context, 400, "ROLE_CREATION_FAILED", message);
        
        logger.warn("Failed role creation from IP: {} - Reason: {}", RequestUtil.getClientIp(context), message);
    }
    
    private void handleRoleCreationError(RoutingContext context, Throwable throwable) {
        if (throwable instanceof DomainException) {
            ResponseUtil.sendError(context, 400, "ROLE_CREATION_ERROR", throwable.getMessage());
        } else {
            ResponseUtil.sendInternalError(context, "Role creation service temporarily unavailable");
        }
    }
    
    private void handleFailedRoleUpdate(RoutingContext context, String message) {
        ResponseUtil.sendError(context, 400, "ROLE_UPDATE_FAILED", message);
        
        logger.warn("Failed role update from IP: {} - Reason: {}", RequestUtil.getClientIp(context), message);
    }
    
    private void handleRoleUpdateError(RoutingContext context, Throwable throwable) {
        if (throwable instanceof RoleNotFoundException) {
            ResponseUtil.sendNotFound(context, "Role not found");
        } else if (throwable instanceof DomainException) {
            ResponseUtil.sendError(context, 400, "ROLE_UPDATE_ERROR", throwable.getMessage());
        } else {
            ResponseUtil.sendInternalError(context, "Role update service temporarily unavailable");
        }
    }
    
    private void handleRolesRetrievalError(RoutingContext context, Throwable throwable) {
        if (throwable instanceof DomainException) {
            ResponseUtil.sendError(context, 400, "ROLES_QUERY_ERROR", throwable.getMessage());
        } else {
            ResponseUtil.sendInternalError(context, "Roles service temporarily unavailable");
        }
    }
    
    private void handleFailedRoleAssignment(RoutingContext context, String message) {
        ResponseUtil.sendError(context, 400, "ROLE_ASSIGNMENT_FAILED", message);
        
        logger.warn("Failed role assignment from IP: {} - Reason: {}", RequestUtil.getClientIp(context), message);
    }
    
    private void handleRoleAssignmentError(RoutingContext context, Throwable throwable) {
        if (throwable instanceof UserNotFoundException) {
            ResponseUtil.sendNotFound(context, "User not found");
        } else if (throwable instanceof RoleNotFoundException) {
            ResponseUtil.sendNotFound(context, "Role not found");
        } else if (throwable instanceof DomainException) {
            ResponseUtil.sendError(context, 400, "ROLE_ASSIGNMENT_ERROR", throwable.getMessage());
        } else {
            ResponseUtil.sendInternalError(context, "Role assignment service temporarily unavailable");
        }
    }
    
    private void handleUsersReportError(RoutingContext context, Throwable throwable) {
        if (throwable instanceof DomainException) {
            ResponseUtil.sendError(context, 400, "USERS_REPORT_ERROR", throwable.getMessage());
        } else {
            ResponseUtil.sendInternalError(context, "Users report service temporarily unavailable");
        }
    }
    
    private void handleRolesReportError(RoutingContext context, Throwable throwable) {
        if (throwable instanceof DomainException) {
            ResponseUtil.sendError(context, 400, "ROLES_REPORT_ERROR", throwable.getMessage());
        } else {
            ResponseUtil.sendInternalError(context, "Roles report service temporarily unavailable");
        }
    }
    
    // Utility methods
    
    private String getClientIp(RoutingContext context) {
        // Check proxy headers first
        String xForwardedFor = context.request().getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = context.request().getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return context.request().remoteAddress().host();
    }
    
    private JsonObject createRoleResponse(Role role) {
        return createRoleResponse(role, false);
    }
    
    private JsonObject createRoleResponse(Role role, boolean includePermissions) {
        JsonObject response = new JsonObject()
            .put("id", role.getId().toString())
            .put("name", role.getName())
            .put("description", role.getDescription())
            .put("createdAt", role.getCreatedAt().toString());
        
        // Add permissions if requested and available
        if (includePermissions && role.getPermissions() != null && !role.getPermissions().isEmpty()) {
            JsonArray permissionsArray = new JsonArray();
            role.getPermissions().forEach(permission -> {
                JsonObject permissionObj = new JsonObject()
                    .put("id", permission.getId().toString())
                    .put("name", permission.getName())
                    .put("resource", permission.getResource())
                    .put("action", permission.getAction())
                    .put("description", permission.getDescription());
                permissionsArray.add(permissionObj);
            });
            response.put("permissions", permissionsArray);
        }
        
        return response;
    }
    
    private JsonObject createUsersReport(List<User> users, boolean includeInactive) {
        long totalUsers = users.size();
        long activeUsers = users.stream().mapToLong(user -> user.isActive() ? 1 : 0).sum();
        long inactiveUsers = totalUsers - activeUsers;
        
        // Count users by role
        JsonObject roleDistribution = new JsonObject();
        users.forEach(user -> {
            if (user.getRoles() != null) {
                user.getRoles().forEach(role -> {
                    String roleName = role.getName();
                    int currentCount = roleDistribution.getInteger(roleName, 0);
                    roleDistribution.put(roleName, currentCount + 1);
                });
            }
        });
        
        JsonObject report = new JsonObject()
            .put("reportType", "users")
            .put("generatedAt", java.time.Instant.now().toString())
            .put("includeInactive", includeInactive)
            .put("summary", new JsonObject()
                .put("totalUsers", totalUsers)
                .put("activeUsers", activeUsers)
                .put("inactiveUsers", inactiveUsers)
                .put("activePercentage", totalUsers > 0 ? (double) activeUsers / totalUsers * 100 : 0))
            .put("roleDistribution", roleDistribution);
        
        return report;
    }
    
    private JsonObject createRolesReport(List<Role> roles) {
        long totalRoles = roles.size();
        
        // Count permissions per role
        JsonObject permissionDistribution = new JsonObject();
        int totalPermissions = 0;
        
        for (Role role : roles) {
            if (role.getPermissions() != null) {
                int permissionCount = role.getPermissions().size();
                permissionDistribution.put(role.getName(), permissionCount);
                totalPermissions += permissionCount;
            } else {
                permissionDistribution.put(role.getName(), 0);
            }
        }
        
        double avgPermissionsPerRole = totalRoles > 0 ? (double) totalPermissions / totalRoles : 0;
        
        JsonObject report = new JsonObject()
            .put("reportType", "roles")
            .put("generatedAt", java.time.Instant.now().toString())
            .put("summary", new JsonObject()
                .put("totalRoles", totalRoles)
                .put("totalPermissions", totalPermissions)
                .put("averagePermissionsPerRole", avgPermissionsPerRole))
            .put("permissionDistribution", permissionDistribution);
        
        return report;
    }
    
    private JsonObject createAuthenticationMetrics() {
        // In a real implementation, these would come from actual metrics storage
        // For now, we'll return mock data to demonstrate the structure
        
        JsonObject metrics = new JsonObject()
            .put("metricsType", "authentication")
            .put("generatedAt", java.time.Instant.now().toString())
            .put("timeRange", "last24Hours")
            .put("summary", new JsonObject()
                .put("totalLoginAttempts", 1250)
                .put("successfulLogins", 1180)
                .put("failedLogins", 70)
                .put("successRate", 94.4)
                .put("uniqueUsers", 450)
                .put("activeTokens", 380))
            .put("hourlyBreakdown", createHourlyAuthMetrics())
            .put("topCountries", createCountryMetrics())
            .put("failureReasons", new JsonObject()
                .put("invalidCredentials", 45)
                .put("accountLocked", 15)
                .put("rateLimited", 10));
        
        return metrics;
    }
    
    private JsonArray createHourlyAuthMetrics() {
        JsonArray hourlyData = new JsonArray();
        
        // Mock hourly data for the last 24 hours
        for (int hour = 0; hour < 24; hour++) {
            JsonObject hourData = new JsonObject()
                .put("hour", hour)
                .put("attempts", 45 + (int)(Math.random() * 30))
                .put("successes", 40 + (int)(Math.random() * 25))
                .put("failures", 2 + (int)(Math.random() * 8));
            hourlyData.add(hourData);
        }
        
        return hourlyData;
    }
    
    private JsonArray createCountryMetrics() {
        JsonArray countryData = new JsonArray();
        
        // Mock country data
        countryData.add(new JsonObject().put("country", "United States").put("logins", 450));
        countryData.add(new JsonObject().put("country", "Canada").put("logins", 280));
        countryData.add(new JsonObject().put("country", "United Kingdom").put("logins", 220));
        countryData.add(new JsonObject().put("country", "Germany").put("logins", 180));
        countryData.add(new JsonObject().put("country", "France").put("logins", 150));
        
        return countryData;
    }
}
