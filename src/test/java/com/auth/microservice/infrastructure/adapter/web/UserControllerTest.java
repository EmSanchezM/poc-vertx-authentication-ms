package com.auth.microservice.infrastructure.adapter.web;

import com.auth.microservice.common.cqrs.CommandBus;
import com.auth.microservice.common.cqrs.QueryBus;
import com.auth.microservice.infrastructure.adapter.web.middleware.AuthenticationMiddleware;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserController
 * Tests the CQRS integration and endpoint behavior
 */
class UserControllerTest {
    
    @Mock
    private CommandBus commandBus;
    
    @Mock
    private QueryBus queryBus;
    
    @Mock
    private AuthenticationMiddleware authenticationMiddleware;
    
    private UserController userController;
    private Vertx vertx;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        vertx = Vertx.vertx();
        userController = new UserController(commandBus, queryBus, authenticationMiddleware);
    }
    
    @Test
    void shouldInstantiateControllerCorrectly() {
        // Given/When/Then
        assertNotNull(userController);
        assertNotNull(commandBus);
        assertNotNull(queryBus);
        assertNotNull(authenticationMiddleware);
    }
    
    @Test
    void shouldConfigureRoutesWithoutErrors() {
        // Given
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        
        // When/Then - Should not throw any exceptions
        assertDoesNotThrow(() -> {
            userController.configureRoutes(router);
        });
        
        // Verify authentication middleware is not called during setup
        verify(authenticationMiddleware, never()).handle(any());
    }
    
    @Test
    void shouldHaveProperDependencyInjection() {
        // Given
        CommandBus mockCommandBus = mock(CommandBus.class);
        QueryBus mockQueryBus = mock(QueryBus.class);
        AuthenticationMiddleware mockAuthMiddleware = mock(AuthenticationMiddleware.class);
        
        // When
        UserController controller = new UserController(mockCommandBus, mockQueryBus, mockAuthMiddleware);
        
        // Then
        assertNotNull(controller);
    }
    
    @Test
    void shouldThrowExceptionForNullDependencies() {
        // Given/When/Then
        assertThrows(NullPointerException.class, () -> {
            new UserController(null, queryBus, authenticationMiddleware);
        });
        
        assertThrows(NullPointerException.class, () -> {
            new UserController(commandBus, null, authenticationMiddleware);
        });
        
        assertThrows(NullPointerException.class, () -> {
            new UserController(commandBus, queryBus, null);
        });
    }
}