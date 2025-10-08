package com.auth.microservice.infrastructure.adapter.web;

import com.auth.microservice.application.command.AuthenticateCommand;
import com.auth.microservice.application.command.InvalidateSessionCommand;
import com.auth.microservice.application.command.RefreshTokenCommand;
import com.auth.microservice.application.command.RegisterUserCommand;
import com.auth.microservice.application.result.AuthenticationResult;
import com.auth.microservice.application.result.RegistrationResult;
import com.auth.microservice.common.cqrs.CommandBus;
import com.auth.microservice.common.cqrs.QueryBus;
import com.auth.microservice.domain.exception.AuthenticationException;
import com.auth.microservice.domain.exception.DomainException;
import com.auth.microservice.domain.exception.UserAlreadyExistsException;
import com.auth.microservice.domain.service.JWTService;
import com.auth.microservice.domain.service.RateLimitService;
import com.auth.microservice.infrastructure.adapter.web.middleware.SecurityLoggingMiddleware;
import com.auth.microservice.infrastructure.adapter.web.response.ResponseUtil;
import com.auth.microservice.infrastructure.adapter.web.util.RequestUtil;
import com.auth.microservice.infrastructure.adapter.web.validation.RequestValidator;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;

/**
 * REST Controller for authentication endpoints.
 * Implements CQRS pattern using CommandBus and QueryBus for operations.
 * Includes rate limiting, validation, and security logging with geolocation.
 */
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final RateLimitService rateLimitService;
    
    public AuthController(CommandBus commandBus, QueryBus queryBus, RateLimitService rateLimitService) {
        this.commandBus = Objects.requireNonNull(commandBus, "CommandBus cannot be null");
        this.queryBus = Objects.requireNonNull(queryBus, "QueryBus cannot be null");
        this.rateLimitService = Objects.requireNonNull(rateLimitService, "RateLimitService cannot be null");
    }
    
    /**
     * Configures authentication routes on the provided router with API versioning.
     * All routes are prefixed with /api/v1/auth for semantic versioning.
     * 
     * @param router the Vert.x router to configure
     */
    public void configureRoutes(Router router) {
        // POST /api/v1/auth/login - User authentication
        router.post("/api/v1/auth/login").handler(this::login);
        
        // POST /api/v1/auth/register - User registration
        router.post("/api/v1/auth/register").handler(this::register);
        
        // POST /api/v1/auth/refresh - Token refresh
        router.post("/api/v1/auth/refresh").handler(this::refresh);
        
        // POST /api/v1/auth/logout - User logout
        router.post("/api/v1/auth/logout").handler(this::logout);
    }
    
    /**
     * Handles user login requests.
     * POST /api/v1/auth/login
     * 
     * Expected request body:
     * {
     *   "usernameOrEmail": "user@example.com",
     *   "password": "userPassword"
     * }
     */
    public void login(RoutingContext context) {
        logger.debug("Processing login request from IP: {}", RequestUtil.getClientIp(context));
        
        // Validate JSON body
        RequestValidator.ValidationResult bodyValidation = RequestValidator.validateJsonBody(context);
        if (!bodyValidation.isValid()) {
            bodyValidation.sendErrorResponse(context);
            return;
        }
        
        JsonObject requestBody = bodyValidation.getData();
        
        // Validate login request fields
        RequestValidator.ValidationResult loginValidation = RequestValidator.validateLoginRequest(requestBody);
        if (!loginValidation.isValid()) {
            SecurityLoggingMiddleware.logFailedAuthentication(context, 
                requestBody.getString("usernameOrEmail", "unknown"), "Invalid request format");
            loginValidation.sendErrorResponse(context);
            return;
        }
        
        String usernameOrEmail = requestBody.getString("usernameOrEmail");
        String password = requestBody.getString("password");
        String clientIp = RequestUtil.getClientIp(context);
        String userAgent = RequestUtil.getUserAgent(context);
        
        // Create authentication command
        AuthenticateCommand command = new AuthenticateCommand(usernameOrEmail, password, clientIp, userAgent);
        
        // Execute authentication through command bus
        commandBus.<AuthenticationResult>send(command)
            .onSuccess(result -> {
                if (result.isSuccess()) {
                    handleSuccessfulLogin(context, result);
                } else {
                    handleFailedLogin(context, usernameOrEmail, result.getMessage());
                }
            })
            .onFailure(throwable -> {
                logger.error("Authentication command failed for usernameOrEmail: {} from IP: {}", 
                    usernameOrEmail, clientIp, throwable);
                handleAuthenticationError(context, usernameOrEmail, throwable);
            });
    }
    
    /**
     * Handles user registration requests.
     * POST /api/v1/auth/register
     * 
     * Expected request body:
     * {
     *   "email": "user@example.com",
     *   "password": "userPassword",
     *   "firstName": "John",
     *   "lastName": "Doe"
     * }
     */
    public void register(RoutingContext context) {
        logger.debug("Processing registration request from IP: {}", RequestUtil.getClientIp(context));
        
        // Validate JSON body
        RequestValidator.ValidationResult bodyValidation = RequestValidator.validateJsonBody(context);
        if (!bodyValidation.isValid()) {
            bodyValidation.sendErrorResponse(context);
            return;
        }
        
        JsonObject requestBody = bodyValidation.getData();
        
        // Validate registration request fields
        RequestValidator.ValidationResult registrationValidation = RequestValidator.validateRegistrationRequest(requestBody);
        if (!registrationValidation.isValid()) {
            registrationValidation.sendErrorResponse(context);
            return;
        }
        
        String email = requestBody.getString("email");
        String password = requestBody.getString("password");
        String firstName = requestBody.getString("firstName");
        String lastName = requestBody.getString("lastName");
        String clientIp = RequestUtil.getClientIp(context);
        String userAgent = RequestUtil.getUserAgent(context);
        
        // Create registration command with default user role
        // Pass null as username to trigger automatic generation from firstName.lastName
        RegisterUserCommand command = new RegisterUserCommand(
            null, // Let the system auto-generate username from firstName.lastName
            email,
            password,
            firstName,
            lastName,
            Set.of("USER"), // Default role
            clientIp,
            userAgent
        );
        
        // Execute registration through command bus
        commandBus.<RegistrationResult>send(command)
            .onSuccess(result -> {
                if (result.isSuccess()) {
                    handleSuccessfulRegistration(context, result);
                } else {
                    handleFailedRegistration(context, email, result.getMessage());
                }
            })
            .onFailure(throwable -> {
                logger.error("Registration command failed for email: {} from IP: {}", 
                    email, clientIp, throwable);
                handleRegistrationError(context, email, throwable);
            });
    }
    
    /**
     * Handles token refresh requests.
     * POST /api/v1/auth/refresh
     * 
     * Expected request body:
     * {
     *   "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
     * }
     */
    public void refresh(RoutingContext context) {
        logger.debug("Processing token refresh request from IP: {}", RequestUtil.getClientIp(context));
        
        // Validate JSON body
        RequestValidator.ValidationResult bodyValidation = RequestValidator.validateJsonBody(context);
        if (!bodyValidation.isValid()) {
            bodyValidation.sendErrorResponse(context);
            return;
        }
        
        JsonObject requestBody = bodyValidation.getData();
        
        // Validate refresh token request
        RequestValidator.ValidationResult refreshValidation = RequestValidator.validateRefreshTokenRequest(requestBody);
        if (!refreshValidation.isValid()) {
            refreshValidation.sendErrorResponse(context);
            return;
        }
        
        String refreshToken = requestBody.getString("refreshToken");
        String clientIp = RequestUtil.getClientIp(context);
        String userAgent = RequestUtil.getUserAgent(context);
        
        // Create refresh token command
        RefreshTokenCommand command = new RefreshTokenCommand(refreshToken, clientIp, userAgent);
        
        // Execute token refresh through command bus
        Future<AuthenticationResult> future = commandBus.send(command);
        future.onSuccess(result -> {
                logger.debug("Received result from command bus: {}", result);
                logger.debug("Result type: {}", result.getClass().getName());
                logger.debug("Result success: {}", result.isSuccess());
                
                if (result.isSuccess() && result.getTokenPair() != null) {
                    logger.debug("Token pair is not null, proceeding with success handler");
                    handleSuccessfulTokenRefresh(context, result.getTokenPair());
                } else {
                    logger.debug("Token refresh failed or token pair is null: {}", result.getMessage());
                    handleFailedTokenRefresh(context, result.getMessage());
                }
            })
            .onFailure(throwable -> {
                logger.error("Token refresh command failed from IP: {}", clientIp, throwable);
                handleTokenRefreshError(context, throwable);
            });
    }
    
    /**
     * Handles user logout requests.
     * POST /api/v1/auth/logout
     * 
     * Requires Authorization header with Bearer token.
     */
    public void logout(RoutingContext context) {
        logger.debug("Processing logout request from IP: {}", RequestUtil.getClientIp(context));
        
        // Extract token from Authorization header
        String authHeader = context.request().getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            ResponseUtil.sendUnauthorized(context, "Authorization header with Bearer token is required");
            return;
        }
        
        String token = RequestUtil.getBearerToken(context);
        String clientIp = RequestUtil.getClientIp(context);
        
        // Get user context from middleware (should be set by AuthenticationMiddleware)
        JsonObject userContext = context.get("user");
        if (userContext == null) {
            ResponseUtil.sendUnauthorized(context, "Invalid or expired token");
            return;
        }
        
        String userId = userContext.getString("userId");
        
        // Create session invalidation command
        InvalidateSessionCommand command = new InvalidateSessionCommand(userId, token, "User logout", clientIp);
        
        // Execute logout through command bus
        commandBus.<Void>send(command)
            .onSuccess(result -> {
                handleSuccessfulLogout(context, userId);
            })
            .onFailure(throwable -> {
                logger.error("Logout command failed for user: {} from IP: {}", userId, clientIp, throwable);
                handleLogoutError(context, throwable);
            });
    }
    
    // Success handlers
    
    private void handleSuccessfulLogin(RoutingContext context, AuthenticationResult result) {
        String userId = result.getUser().getId().toString();
        String email = result.getUser().getEmail().getValue();
        
        // Log successful authentication with geolocation
        SecurityLoggingMiddleware.logSuccessfulAuthentication(context, userId, email);
        
        // Create user info for response
        JsonObject userInfo = ResponseUtil.createUserResponse(
            userId,
            email,
            result.getUser().getFirstName(),
            result.getUser().getLastName(),
            createRolesArray(result.getUser().getRoles())
        );
        
        // Create authentication response
        JsonObject authResponse = ResponseUtil.createAuthSuccessResponse(
            result.getTokenPair().accessToken(),
            result.getTokenPair().refreshToken(),
            userInfo
        );
        
        ResponseUtil.sendSuccess(context, authResponse);
        
        logger.info("User {} successfully authenticated from IP: {}", email, RequestUtil.getClientIp(context));
    }
    
    private void handleSuccessfulRegistration(RoutingContext context, RegistrationResult result) {
        String userId = result.getUser().getId().toString();
        String email = result.getUser().getEmail().getValue();
        
        // Create user info for response
        JsonObject userInfo = ResponseUtil.createUserResponse(
            userId,
            email,
            result.getUser().getFirstName(),
            result.getUser().getLastName(),
            createRolesArray(result.getUser().getRoles())
        );
        
        ResponseUtil.sendCreated(context, userInfo);
        
        logger.info("User {} successfully registered from IP: {}", email, RequestUtil.getClientIp(context));
    }
    
    private void handleSuccessfulTokenRefresh(RoutingContext context, JWTService.TokenPair tokenPair) {
        JsonObject refreshResponse = new JsonObject()
            .put("accessToken", tokenPair.accessToken())
            .put("refreshToken", tokenPair.refreshToken())
            .put("tokenType", "Bearer")
            .put("expiresAt", tokenPair.accessTokenExpiration().toString());
        
        ResponseUtil.sendSuccess(context, refreshResponse);
        
        logger.info("Token successfully refreshed from IP: {}", RequestUtil.getClientIp(context));
    }
    
    private void handleSuccessfulLogout(RoutingContext context, String userId) {
        JsonObject logoutResponse = ResponseUtil.createMessageResponse("Successfully logged out");
        ResponseUtil.sendSuccess(context, logoutResponse);
        
        logger.info("User {} successfully logged out from IP: {}", userId, RequestUtil.getClientIp(context));
    }
    
    // Error handlers
    
    private void handleFailedLogin(RoutingContext context, String usernameOrEmail, String message) {
        SecurityLoggingMiddleware.logFailedAuthentication(context, usernameOrEmail, message);
        ResponseUtil.sendUnauthorized(context, "Invalid credentials");
        
        logger.warn("Failed login attempt for usernameOrEmail: {} from IP: {} - Reason: {}", 
            usernameOrEmail, RequestUtil.getClientIp(context), message);
    }
    
    private void handleAuthenticationError(RoutingContext context, String usernameOrEmail, Throwable throwable) {
        if (throwable instanceof AuthenticationException) {
            handleFailedLogin(context, usernameOrEmail, throwable.getMessage());
        } else if (throwable instanceof DomainException) {
            SecurityLoggingMiddleware.logFailedAuthentication(context, usernameOrEmail, throwable.getMessage());
            ResponseUtil.sendError(context, 400, "AUTHENTICATION_ERROR", throwable.getMessage());
        } else {
            SecurityLoggingMiddleware.logFailedAuthentication(context, usernameOrEmail, "System error");
            ResponseUtil.sendInternalError(context, "Authentication service temporarily unavailable");
        }
    }
    
    private void handleFailedRegistration(RoutingContext context, String email, String message) {
        ResponseUtil.sendError(context, 400, "REGISTRATION_FAILED", message);
        
        logger.warn("Failed registration attempt for email: {} from IP: {} - Reason: {}", 
            email, RequestUtil.getClientIp(context), message);
    }
    
    private void handleRegistrationError(RoutingContext context, String email, Throwable throwable) {
        if (throwable instanceof UserAlreadyExistsException) {
            ResponseUtil.sendConflict(context, "User with this email already exists");
        } else if (throwable instanceof DomainException) {
            ResponseUtil.sendError(context, 400, "REGISTRATION_ERROR", throwable.getMessage());
        } else {
            ResponseUtil.sendInternalError(context, "Registration service temporarily unavailable");
        }
        
        logger.error("Registration error for email: {} from IP: {}", email, RequestUtil.getClientIp(context), throwable);
    }
    
    private void handleFailedTokenRefresh(RoutingContext context, String message) {
        ResponseUtil.sendUnauthorized(context, "Invalid or expired refresh token");
        
        logger.warn("Failed token refresh attempt from IP: {} - Reason: {}", 
            RequestUtil.getClientIp(context), message);
    }
    
    private void handleTokenRefreshError(RoutingContext context, Throwable throwable) {
        if (throwable instanceof AuthenticationException || 
            throwable.getMessage().contains("token") || 
            throwable.getMessage().contains("expired")) {
            ResponseUtil.sendUnauthorized(context, "Invalid or expired refresh token");
        } else if (throwable instanceof DomainException) {
            ResponseUtil.sendError(context, 400, "TOKEN_REFRESH_ERROR", throwable.getMessage());
        } else {
            ResponseUtil.sendInternalError(context, "Token refresh service temporarily unavailable");
        }
    }
    
    private void handleLogoutError(RoutingContext context, Throwable throwable) {
        if (throwable instanceof DomainException) {
            ResponseUtil.sendError(context, 400, "LOGOUT_ERROR", throwable.getMessage());
        } else {
            ResponseUtil.sendInternalError(context, "Logout service temporarily unavailable");
        }
    }
    
    // Utility methods
    
    private JsonArray createRolesArray(Set<com.auth.microservice.domain.model.Role> roles) {
        JsonArray rolesArray = new JsonArray();
        if (roles != null) {
            roles.forEach(role -> {
                JsonObject roleObj = new JsonObject()
                    .put("id", role.getId().toString())
                    .put("name", role.getName())
                    .put("description", role.getDescription());
                rolesArray.add(roleObj);
            });
        }
        return rolesArray;
    }
}