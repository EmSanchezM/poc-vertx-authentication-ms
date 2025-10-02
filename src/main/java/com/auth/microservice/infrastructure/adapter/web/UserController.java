package com.auth.microservice.infrastructure.adapter.web;

import com.auth.microservice.application.command.CreateUserCommand;
import com.auth.microservice.application.command.UpdateUserCommand;
import com.auth.microservice.application.query.GetUserByIdQuery;
import com.auth.microservice.application.query.GetUserProfileQuery;
import com.auth.microservice.application.query.GetUsersQuery;
import com.auth.microservice.application.result.UserCreationResult;
import com.auth.microservice.application.result.UserProfile;
import com.auth.microservice.application.result.UserUpdateResult;
import com.auth.microservice.common.cqrs.CommandBus;
import com.auth.microservice.common.cqrs.QueryBus;
import com.auth.microservice.domain.exception.DomainException;
import com.auth.microservice.domain.exception.UserNotFoundException;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.Pagination;
import com.auth.microservice.infrastructure.adapter.web.middleware.AuthenticationMiddleware;
import com.auth.microservice.infrastructure.adapter.web.middleware.AuthorizationMiddleware;
import com.auth.microservice.infrastructure.adapter.web.response.ResponseUtil;
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
 * REST Controller for user management endpoints.
 * Implements CQRS pattern using CommandBus for write operations and QueryBus for read operations.
 * Includes proper authorization for different types of operations.
 * 
 * Integration example:
 * ```java
 * // In Main.java or WebConfiguration:
 * UserController userController = new UserController(commandBus, queryBus, authenticationMiddleware);
 * userController.configureRoutes(router);
 * ```
 * 
 * Endpoints:
 * - GET /users/profile - Get current user's profile (authenticated users)
 * - PUT /users/profile - Update current user's profile (authenticated users)
 * - GET /users - Get all users with pagination (admin only, requires users:read permission)
 * - POST /users - Create new user (admin only, requires users:create permission)
 * - PUT /users/{userId} - Update user by ID (admin only, requires users:update permission)
 */
public class UserController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    
    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final AuthenticationMiddleware authenticationMiddleware;
    
    public UserController(CommandBus commandBus, QueryBus queryBus, AuthenticationMiddleware authenticationMiddleware) {
        this.commandBus = Objects.requireNonNull(commandBus, "CommandBus cannot be null");
        this.queryBus = Objects.requireNonNull(queryBus, "QueryBus cannot be null");
        this.authenticationMiddleware = Objects.requireNonNull(authenticationMiddleware, "AuthenticationMiddleware cannot be null");
    }
    
    /**
     * Configures user management routes on the provided router.
     * 
     * @param router the Vert.x router to configure
     */
    public void configureRoutes(Router router) {
        // User profile endpoints (authenticated users can access their own profile)
        router.get("/users/profile")
            .handler(authenticationMiddleware)
            .handler(this::getUserProfile);
            
        router.put("/users/profile")
            .handler(authenticationMiddleware)
            .handler(this::updateUserProfile);
        
        // Administrative endpoints (require admin permissions)
        router.get("/users")
            .handler(authenticationMiddleware)
            .handler(AuthorizationMiddleware.requirePermission(queryBus, "users", "read"))
            .handler(this::getUsers);
            
        router.post("/users")
            .handler(authenticationMiddleware)
            .handler(AuthorizationMiddleware.requirePermission(queryBus, "users", "create"))
            .handler(this::createUser);
            
        router.put("/users/:userId")
            .handler(authenticationMiddleware)
            .handler(AuthorizationMiddleware.requirePermission(queryBus, "users", "update"))
            .handler(this::updateUser);
    }
    
    /**
     * Handles get user profile requests.
     * GET /users/profile
     * 
     * Returns the profile of the authenticated user.
     */
    public void getUserProfile(RoutingContext context) {
        String userId = AuthenticationMiddleware.getUserId(context);
        logger.debug("Processing get profile request for user: {} from IP: {}", userId, getClientIp(context));
        
        if (userId == null) {
            ResponseUtil.sendUnauthorized(context, "User context not found");
            return;
        }
        
        try {
            UUID userUuid = UUID.fromString(userId);
            GetUserProfileQuery query = new GetUserProfileQuery(userId, userUuid, true);
            
            queryBus.<UserProfile>send(query)
                .onSuccess(userProfile -> {
                    handleSuccessfulProfileRetrieval(context, userProfile);
                })
                .onFailure(throwable -> {
                    logger.error("Get profile query failed for user: {} from IP: {}", 
                        userId, getClientIp(context), throwable);
                    handleProfileRetrievalError(context, throwable);
                });
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid user ID format: {} from IP: {}", userId, getClientIp(context));
            ResponseUtil.sendError(context, 400, "INVALID_USER_ID", "Invalid user ID format");
        }
    }
    
    /**
     * Handles update user profile requests.
     * PUT /users/profile
     * 
     * Expected request body:
     * {
     *   "firstName": "John",
     *   "lastName": "Doe"
     * }
     */
    public void updateUserProfile(RoutingContext context) {
        String userId = AuthenticationMiddleware.getUserId(context);
        logger.debug("Processing update profile request for user: {} from IP: {}", userId, getClientIp(context));
        
        if (userId == null) {
            ResponseUtil.sendUnauthorized(context, "User context not found");
            return;
        }
        
        // Validate JSON body
        RequestValidator.ValidationResult bodyValidation = RequestValidator.validateJsonBody(context);
        if (!bodyValidation.isValid()) {
            bodyValidation.sendErrorResponse(context);
            return;
        }
        
        JsonObject requestBody = bodyValidation.getData();
        
        // Validate update profile request
        RequestValidator.ValidationResult profileValidation = RequestValidator.validateUpdateProfileRequest(requestBody);
        if (!profileValidation.isValid()) {
            profileValidation.sendErrorResponse(context);
            return;
        }
        
        String clientIp = getClientIp(context);
        String userAgent = context.request().getHeader("User-Agent");
        
        // Extract optional fields
        Optional<String> firstName = Optional.ofNullable(requestBody.getString("firstName"));
        Optional<String> lastName = Optional.ofNullable(requestBody.getString("lastName"));
        Optional<Boolean> isActive = Optional.empty(); // Users cannot change their own active status
        
        // Create update command
        UpdateUserCommand command = new UpdateUserCommand(
            userId, userId, firstName, lastName, isActive, clientIp, userAgent
        );
        
        // Execute update through command bus
        commandBus.<UserUpdateResult>send(command)
            .onSuccess(result -> {
                if (result.isSuccess()) {
                    handleSuccessfulProfileUpdate(context, result);
                } else {
                    handleFailedProfileUpdate(context, result.getErrorMessage());
                }
            })
            .onFailure(throwable -> {
                logger.error("Update profile command failed for user: {} from IP: {}", 
                    userId, clientIp, throwable);
                handleProfileUpdateError(context, throwable);
            });
    }
    
    /**
     * Handles get users requests (admin only).
     * GET /users?page=0&size=20&includeInactive=false&search=term
     * 
     * Returns paginated list of users with optional filtering.
     */
    public void getUsers(RoutingContext context) {
        String adminUserId = AuthenticationMiddleware.getUserId(context);
        logger.debug("Processing get users request by admin: {} from IP: {}", adminUserId, getClientIp(context));
        
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
        boolean includeInactive = "true".equals(context.request().getParam("includeInactive"));
        String searchTerm = context.request().getParam("search");
        
        // Create pagination object
        Pagination pagination = Pagination.of(page, size, "createdAt", Pagination.SortDirection.DESC);
        
        // Create query
        GetUsersQuery query = new GetUsersQuery(adminUserId, pagination, includeInactive, true, searchTerm);
        
        // Execute query through query bus
        queryBus.<List<User>>send(query)
            .onSuccess(users -> {
                handleSuccessfulUsersRetrieval(context, users, pagination);
            })
            .onFailure(throwable -> {
                logger.error("Get users query failed for admin: {} from IP: {}", 
                    adminUserId, getClientIp(context), throwable);
                handleUsersRetrievalError(context, throwable);
            });
    }
    
    /**
     * Handles create user requests (admin only).
     * POST /users
     * 
     * Expected request body:
     * {
     *   "username": "johndoe",
     *   "email": "john@example.com",
     *   "firstName": "John",
     *   "lastName": "Doe",
     *   "roles": ["USER", "MODERATOR"],
     *   "isActive": true
     * }
     */
    public void createUser(RoutingContext context) {
        String adminUserId = AuthenticationMiddleware.getUserId(context);
        logger.debug("Processing create user request by admin: {} from IP: {}", adminUserId, getClientIp(context));
        
        // Validate JSON body
        RequestValidator.ValidationResult bodyValidation = RequestValidator.validateJsonBody(context);
        if (!bodyValidation.isValid()) {
            bodyValidation.sendErrorResponse(context);
            return;
        }
        
        JsonObject requestBody = bodyValidation.getData();
        
        // Validate create user request
        RequestValidator.ValidationResult userValidation = RequestValidator.validateCreateUserRequest(requestBody);
        if (!userValidation.isValid()) {
            userValidation.sendErrorResponse(context);
            return;
        }
        
        String username = requestBody.getString("username");
        String email = requestBody.getString("email");
        String firstName = requestBody.getString("firstName");
        String lastName = requestBody.getString("lastName");
        boolean isActive = requestBody.getBoolean("isActive", true);
        String clientIp = getClientIp(context);
        String userAgent = context.request().getHeader("User-Agent");
        
        // Extract roles (optional, defaults to USER role)
        Set<String> roleNames = Set.of("USER"); // Default role
        JsonArray rolesArray = requestBody.getJsonArray("roles");
        if (rolesArray != null && !rolesArray.isEmpty()) {
            roleNames = Set.copyOf(rolesArray.getList());
        }
        
        // Create user creation command
        CreateUserCommand command = new CreateUserCommand(
            adminUserId, username, email, firstName, lastName, roleNames, isActive, clientIp, userAgent
        );
        
        // Execute creation through command bus
        commandBus.<UserCreationResult>send(command)
            .onSuccess(result -> {
                if (result.isSuccess()) {
                    handleSuccessfulUserCreation(context, result);
                } else {
                    handleFailedUserCreation(context, result.getErrorMessage());
                }
            })
            .onFailure(throwable -> {
                logger.error("Create user command failed for admin: {} from IP: {}", 
                    adminUserId, clientIp, throwable);
                handleUserCreationError(context, throwable);
            });
    }
    
    /**
     * Handles update user requests (admin only).
     * PUT /users/{userId}
     * 
     * Expected request body:
     * {
     *   "firstName": "John",
     *   "lastName": "Doe",
     *   "isActive": true
     * }
     */
    public void updateUser(RoutingContext context) {
        String adminUserId = AuthenticationMiddleware.getUserId(context);
        String targetUserId = context.pathParam("userId");
        
        logger.debug("Processing update user request by admin: {} for user: {} from IP: {}", 
            adminUserId, targetUserId, getClientIp(context));
        
        // Validate target user ID
        RequestValidator.ValidationResult userIdValidation = RequestValidator.validateUuidParam(targetUserId, "User ID");
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
        
        // Validate update user request
        RequestValidator.ValidationResult updateValidation = RequestValidator.validateUpdateUserRequest(requestBody);
        if (!updateValidation.isValid()) {
            updateValidation.sendErrorResponse(context);
            return;
        }
        
        String clientIp = getClientIp(context);
        String userAgent = context.request().getHeader("User-Agent");
        
        // Extract optional fields
        Optional<String> firstName = Optional.ofNullable(requestBody.getString("firstName"));
        Optional<String> lastName = Optional.ofNullable(requestBody.getString("lastName"));
        Optional<Boolean> isActive = requestBody.containsKey("isActive") ? 
            Optional.of(requestBody.getBoolean("isActive")) : Optional.empty();
        
        // Create update command
        UpdateUserCommand command = new UpdateUserCommand(
            adminUserId, targetUserId, firstName, lastName, isActive, clientIp, userAgent
        );
        
        // Execute update through command bus
        commandBus.<UserUpdateResult>send(command)
            .onSuccess(result -> {
                if (result.isSuccess()) {
                    handleSuccessfulUserUpdate(context, result);
                } else {
                    handleFailedUserUpdate(context, result.getErrorMessage());
                }
            })
            .onFailure(throwable -> {
                logger.error("Update user command failed for admin: {} targeting user: {} from IP: {}", 
                    adminUserId, targetUserId, clientIp, throwable);
                handleUserUpdateError(context, throwable);
            });
    }
    
    // Success handlers
    
    private void handleSuccessfulProfileRetrieval(RoutingContext context, UserProfile userProfile) {
        JsonObject profileResponse = createUserProfileResponse(userProfile);
        ResponseUtil.sendSuccess(context, profileResponse);
        
        logger.info("User profile retrieved successfully for user: {} from IP: {}", 
            userProfile.getId(), getClientIp(context));
    }
    
    private void handleSuccessfulProfileUpdate(RoutingContext context, UserUpdateResult result) {
        JsonObject userResponse = createUserResponse(result.getUser());
        ResponseUtil.sendSuccess(context, userResponse);
        
        logger.info("User profile updated successfully for user: {} from IP: {}", 
            result.getUser().getId(), getClientIp(context));
    }
    
    private void handleSuccessfulUsersRetrieval(RoutingContext context, List<User> users, Pagination pagination) {
        JsonArray usersArray = new JsonArray();
        users.forEach(user -> usersArray.add(createUserResponse(user)));
        
        // For simplicity, we'll use the current page size as total elements
        // In a real implementation, you'd get the actual count from the repository
        long totalElements = users.size();
        
        ResponseUtil.sendPaginatedSuccess(context, usersArray, 
            pagination.getPage(), pagination.getSize(), totalElements);
        
        logger.info("Users list retrieved successfully by admin from IP: {} - {} users returned", 
            getClientIp(context), users.size());
    }
    
    private void handleSuccessfulUserCreation(RoutingContext context, UserCreationResult result) {
        JsonObject userResponse = createUserResponse(result.getUser());
        String location = "/users/" + result.getUser().getId();
        ResponseUtil.sendCreated(context, userResponse, location);
        
        logger.info("User created successfully: {} by admin from IP: {}", 
            result.getUser().getEmail().getValue(), getClientIp(context));
    }
    
    private void handleSuccessfulUserUpdate(RoutingContext context, UserUpdateResult result) {
        JsonObject userResponse = createUserResponse(result.getUser());
        ResponseUtil.sendSuccess(context, userResponse);
        
        logger.info("User updated successfully: {} by admin from IP: {}", 
            result.getUser().getId(), getClientIp(context));
    }
    
    // Error handlers
    
    private void handleProfileRetrievalError(RoutingContext context, Throwable throwable) {
        if (throwable instanceof UserNotFoundException) {
            ResponseUtil.sendNotFound(context, "User profile not found");
        } else if (throwable instanceof DomainException) {
            ResponseUtil.sendError(context, 400, "PROFILE_ERROR", throwable.getMessage());
        } else {
            ResponseUtil.sendInternalError(context, "Profile service temporarily unavailable");
        }
    }
    
    private void handleFailedProfileUpdate(RoutingContext context, String message) {
        ResponseUtil.sendError(context, 400, "PROFILE_UPDATE_FAILED", message);
        
        logger.warn("Failed profile update from IP: {} - Reason: {}", getClientIp(context), message);
    }
    
    private void handleProfileUpdateError(RoutingContext context, Throwable throwable) {
        if (throwable instanceof UserNotFoundException) {
            ResponseUtil.sendNotFound(context, "User not found");
        } else if (throwable instanceof DomainException) {
            ResponseUtil.sendError(context, 400, "PROFILE_UPDATE_ERROR", throwable.getMessage());
        } else {
            ResponseUtil.sendInternalError(context, "Profile update service temporarily unavailable");
        }
    }
    
    private void handleUsersRetrievalError(RoutingContext context, Throwable throwable) {
        if (throwable instanceof DomainException) {
            ResponseUtil.sendError(context, 400, "USERS_QUERY_ERROR", throwable.getMessage());
        } else {
            ResponseUtil.sendInternalError(context, "Users service temporarily unavailable");
        }
    }
    
    private void handleFailedUserCreation(RoutingContext context, String message) {
        ResponseUtil.sendError(context, 400, "USER_CREATION_FAILED", message);
        
        logger.warn("Failed user creation from IP: {} - Reason: {}", getClientIp(context), message);
    }
    
    private void handleUserCreationError(RoutingContext context, Throwable throwable) {
        if (throwable instanceof DomainException) {
            ResponseUtil.sendError(context, 400, "USER_CREATION_ERROR", throwable.getMessage());
        } else {
            ResponseUtil.sendInternalError(context, "User creation service temporarily unavailable");
        }
    }
    
    private void handleFailedUserUpdate(RoutingContext context, String message) {
        ResponseUtil.sendError(context, 400, "USER_UPDATE_FAILED", message);
        
        logger.warn("Failed user update from IP: {} - Reason: {}", getClientIp(context), message);
    }
    
    private void handleUserUpdateError(RoutingContext context, Throwable throwable) {
        if (throwable instanceof UserNotFoundException) {
            ResponseUtil.sendNotFound(context, "User not found");
        } else if (throwable instanceof DomainException) {
            ResponseUtil.sendError(context, 400, "USER_UPDATE_ERROR", throwable.getMessage());
        } else {
            ResponseUtil.sendInternalError(context, "User update service temporarily unavailable");
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
    
    private JsonObject createUserProfileResponse(UserProfile userProfile) {
        JsonObject response = new JsonObject()
            .put("id", userProfile.getId().toString())
            .put("username", userProfile.getUsername())
            .put("email", userProfile.getEmail())
            .put("firstName", userProfile.getFirstName())
            .put("lastName", userProfile.getLastName())
            .put("fullName", userProfile.getFullName())
            .put("isActive", userProfile.isActive())
            .put("createdAt", userProfile.getCreatedAt().toString())
            .put("updatedAt", userProfile.getUpdatedAt().toString());
        
        // Add roles if available
        if (!userProfile.getRoleNames().isEmpty()) {
            JsonArray rolesArray = new JsonArray();
            userProfile.getRoleNames().forEach(rolesArray::add);
            response.put("roles", rolesArray);
        }
        
        // Add permissions if available
        if (!userProfile.getPermissions().isEmpty()) {
            JsonArray permissionsArray = new JsonArray();
            userProfile.getPermissions().forEach(permissionsArray::add);
            response.put("permissions", permissionsArray);
        }
        
        return response;
    }
    
    private JsonObject createUserResponse(User user) {
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
}