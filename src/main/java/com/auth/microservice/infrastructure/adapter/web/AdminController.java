package com.auth.microservice.infrastructure.adapter.web;

import com.auth.microservice.application.command.AssignRoleCommand;
import com.auth.microservice.application.command.CreateRoleCommand;
import com.auth.microservice.application.command.UpdateRoleCommand;
import com.auth.microservice.application.query.GetAdminReportsQuery;
import com.auth.microservice.application.query.GetRoleByIdQuery;
import com.auth.microservice.application.query.GetRolesQuery;
import com.auth.microservice.application.result.AdminReportsResult;
import com.auth.microservice.application.result.RoleAssignmentResult;
import com.auth.microservice.application.result.RoleCreationResult;
import com.auth.microservice.application.result.RoleUpdateResult;
import com.auth.microservice.common.cqrs.CommandBus;
import com.auth.microservice.common.cqrs.QueryBus;
import com.auth.microservice.domain.exception.DomainException;
import com.auth.microservice.domain.exception.RoleNotFoundException;
import com.auth.microservice.domain.exception.UserNotFoundException;
import com.auth.microservice.domain.model.Role;
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
 * Includes granular authorization for different administrative operations.
 * 
 * Integration example:
 * ```java
 * // In Main.java or WebConfiguration:
 * AdminController adminController = new AdminController(commandBus, queryBus, authenticationMiddleware);
 * adminController.configureRoutes(router);
 * ```
 * 
 * Endpoints:
 * - POST /admin/roles - Create new role (requires admin:roles:create permission)
 * - PUT /admin/roles/{roleId} - Update role (requires admin:roles:update permission)
 * - GET /admin/roles - Get all roles (requires admin:roles:read permission)
 * - POST /admin/users/{userId}/roles - Assign role to user (requires admin:users:assign-roles permission)
 * - GET /admin/reports - Get administrative reports and metrics (requires admin:reports:read permission)
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
     * All routes require authentication and specific admin permissions.
     * 
     * @param router the Vert.x router to configure
     */
    public void configureRoutes(Router router) {
        // Role management endpoints
        router.post("/admin/roles")
            .handler(authenticationMiddleware)
            .handler(AuthorizationMiddleware.requirePermission(queryBus, "admin", "roles:create"))
            .handler(this::createRole);
            
        router.put("/admin/roles/:roleId")
            .handler(authenticationMiddleware)
            .handler(AuthorizationMiddleware.requirePermission(queryBus, "admin", "roles:update"))
            .handler(this::updateRole);
            
        router.get("/admin/roles")
            .handler(authenticationMiddleware)
            .handler(AuthorizationMiddleware.requirePermission(queryBus, "admin", "roles:read"))
            .handler(this::getRoles);
        
        // User role assignment endpoint
        router.post("/admin/users/:userId/roles")
            .handler(authenticationMiddleware)
            .handler(AuthorizationMiddleware.requirePermission(queryBus, "admin", "users:assign-roles"))
            .handler(this::assignRoleToUser);
        
        // Administrative reports endpoint
        router.get("/admin/reports")
            .handler(authenticationMiddleware)
            .handler(AuthorizationMiddleware.requirePermission(queryBus, "admin", "reports:read"))
            .handler(this::getAdminReports);
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
        logger.debug("Processing create role request by admin: {} from IP: {}", adminUserId, RequestUtil.getClientIp(context));
        
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
        String clientIp = RequestUtil.getClientIp(context);
        String userAgent = RequestUtil.getUserAgent(context);
        
        // Extract permissions (optional)
        Set<String> permissionNames = Set.of(); // Default to no permissions
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
                    adminUserId, RequestUtil.getClientIp(context), throwable);
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
        String roleIdParam = context.pathParam("roleId");
        
        logger.debug("Processing update role request by admin: {} for role: {} from IP: {}", 
            adminUserId, roleIdParam, RequestUtil.getClientIp(context));
        
        // Validate role ID parameter
        RequestValidator.ValidationResult roleIdValidation = RequestValidator.validateUuidParam(roleIdParam, "Role ID");
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
        
        // Validate update role request
        RequestValidator.ValidationResult updateValidation = RequestValidator.validateUpdateRoleRequest(requestBody);
        if (!updateValidation.isValid()) {
            updateValidation.sendErrorResponse(context);
            return;
        }
        
        UUID roleId = UUID.fromString(roleIdParam);
        String description = requestBody.getString("description");
        String clientIp = RequestUtil.getClientIp(context);
        String userAgent = RequestUtil.getUserAgent(context);
        
        // Create update command
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
                    adminUserId, roleId, RequestUtil.getClientIp(context), throwable);
                handleRoleUpdateError(context, throwable);
            });
    }
    
    /**
     * Handles get roles requests.
     * GET /admin/roles?page=0&size=20&includePermissions=false
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
                handleSuccessfulRolesRetrieval(context, roles, pagination);
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
        String userIdParam = context.pathParam("userId");
        
        logger.debug("Processing assign role request by admin: {} for user: {} from IP: {}", 
            adminUserId, userIdParam, RequestUtil.getClientIp(context));
        
        // Validate user ID parameter
        RequestValidator.ValidationResult userIdValidation = RequestValidator.validateUuidParam(userIdParam, "User ID");
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
        
        // Validate assign role request
        RequestValidator.ValidationResult assignValidation = RequestValidator.validateAssignRoleRequest(requestBody);
        if (!assignValidation.isValid()) {
            assignValidation.sendErrorResponse(context);
            return;
        }
        
        String roleId = requestBody.getString("roleId");
        String clientIp = RequestUtil.getClientIp(context);
        String userAgent = RequestUtil.getUserAgent(context);
        
        // Create assign role command
        AssignRoleCommand command = new AssignRoleCommand(
            adminUserId, userIdParam, roleId, clientIp, userAgent
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
                    adminUserId, userIdParam, RequestUtil.getClientIp(context), throwable);
                handleRoleAssignmentError(context, throwable);
            });
    }
    
    /**
     * Handles get admin reports requests.
     * GET /admin/reports?type=overview&includeDetails=false
     * 
     * Returns administrative reports and system metrics.
     */
    public void getAdminReports(RoutingContext context) {
        String adminUserId = AuthenticationMiddleware.getUserId(context);
        logger.debug("Processing get admin reports request by admin: {} from IP: {}", adminUserId, RequestUtil.getClientIp(context));
        
        // Validate report parameters
        RequestValidator.ValidationResult reportValidation = RequestValidator.validateAdminReportParams(context);
        if (!reportValidation.isValid()) {
            reportValidation.sendErrorResponse(context);
            return;
        }
        
        JsonObject reportParams = reportValidation.getData();
        String reportType = reportParams.getString("reportType");
        boolean includeDetails = reportParams.getBoolean("includeDetails");
        
        // Create query
        GetAdminReportsQuery query = new GetAdminReportsQuery(adminUserId, reportType, null, null, includeDetails);
        
        // Execute query through query bus
        queryBus.<AdminReportsResult>send(query)
            .onSuccess(result -> {
                handleSuccessfulReportsRetrieval(context, result);
            })
            .onFailure(throwable -> {
                logger.error("Get admin reports query failed for admin: {} from IP: {}", 
                    adminUserId, RequestUtil.getClientIp(context), throwable);
                handleReportsRetrievalError(context, throwable);
            });
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
    
    private void handleSuccessfulRolesRetrieval(RoutingContext context, List<Role> roles, Pagination pagination) {
        JsonArray rolesArray = new JsonArray();
        roles.forEach(role -> rolesArray.add(createRoleResponse(role)));
        
        // For simplicity, we'll use the current page size as total elements
        // In a real implementation, you'd get the actual count from the repository
        long totalElements = roles.size();
        
        ResponseUtil.sendPaginatedSuccess(context, rolesArray, 
            pagination.getPage(), pagination.getSize(), totalElements);
        
        logger.info("Roles list retrieved successfully by admin from IP: {} - {} roles returned", 
            RequestUtil.getClientIp(context), roles.size());
    }
    
    private void handleSuccessfulRoleAssignment(RoutingContext context, RoleAssignmentResult result) {
        JsonObject userResponse = createUserWithRolesResponse(result.getUser());
        ResponseUtil.sendSuccess(context, userResponse);
        
        logger.info("Role assigned successfully to user: {} by admin from IP: {}", 
            result.getUser().getId(), RequestUtil.getClientIp(context));
    }
    
    private void handleSuccessfulReportsRetrieval(RoutingContext context, AdminReportsResult result) {
        JsonObject reportResponse = createReportResponse(result);
        ResponseUtil.sendSuccess(context, reportResponse);
        
        logger.info("Admin report generated successfully: {} by admin from IP: {}", 
            result.getReportType(), RequestUtil.getClientIp(context));
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
    
    private void handleReportsRetrievalError(RoutingContext context, Throwable throwable) {
        if (throwable instanceof DomainException) {
            ResponseUtil.sendError(context, 400, "REPORTS_QUERY_ERROR", throwable.getMessage());
        } else {
            ResponseUtil.sendInternalError(context, "Reports service temporarily unavailable");
        }
    }
    
    // Utility methods
    
    private JsonObject createRoleResponse(Role role) {
        JsonObject response = new JsonObject()
            .put("id", role.getId().toString())
            .put("name", role.getName())
            .put("description", role.getDescription())
            .put("createdAt", role.getCreatedAt().toString());
        
        // Add permissions if available
        if (role.getPermissions() != null && !role.getPermissions().isEmpty()) {
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
    
    private JsonObject createUserWithRolesResponse(com.auth.microservice.domain.model.User user) {
        JsonObject response = new JsonObject()
            .put("id", user.getId().toString())
            .put("username", user.getUsername())
            .put("email", user.getEmail().getValue())
            .put("firstName", user.getFirstName())
            .put("lastName", user.getLastName())
            .put("isActive", user.isActive())
            .put("createdAt", user.getCreatedAt().toString())
            .put("updatedAt", user.getUpdatedAt().toString());
        
        // Add roles if available
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            JsonArray rolesArray = new JsonArray();
            user.getRoles().forEach(role -> {
                JsonObject roleObj = new JsonObject()
                    .put("id", role.getId().toString())
                    .put("name", role.getName())
                    .put("description", role.getDescription());
                rolesArray.add(roleObj);
            });
            response.put("roles", rolesArray);
        }
        
        return response;
    }
    
    private JsonObject createReportResponse(AdminReportsResult result) {
        JsonObject response = new JsonObject()
            .put("reportType", result.getReportType())
            .put("generatedAt", result.getGeneratedAt().toString())
            .put("metrics", new JsonObject(result.getMetrics()));
        
        if (result.getDetails() != null && !result.getDetails().isEmpty()) {
            response.put("details", new JsonObject(result.getDetails()));
        }
        
        return response;
    }
}