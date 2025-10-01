package com.auth.microservice.common.cqrs;

import com.auth.microservice.common.cqrs.exceptions.CommandNotFoundException;
import com.auth.microservice.common.cqrs.exceptions.HandlerRegistrationException;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class VertxCommandBusTest {
    
    private VertxCommandBus commandBus;
    private TestCommandHandler testHandler;
    
    @BeforeEach
    void setUp(Vertx vertx) {
        commandBus = new VertxCommandBus(vertx);
        testHandler = new TestCommandHandler();
    }
    
    @Test
    void shouldRegisterCommandHandler() {
        // When
        commandBus.registerHandler(testHandler);
        
        // Then
        assertEquals(1, commandBus.getHandlerCount());
        assertTrue(commandBus.hasHandler(TestCommand.class));
    }
    
    @Test
    void shouldThrowExceptionWhenRegisteringDuplicateHandler() {
        // Given
        commandBus.registerHandler(testHandler);
        
        // When & Then
        assertThrows(HandlerRegistrationException.class, () -> {
            commandBus.registerHandler(new TestCommandHandler());
        });
    }
    
    @Test
    void shouldExecuteCommandSuccessfully(VertxTestContext testContext) {
        // Given
        commandBus.registerHandler(testHandler);
        TestCommand command = new TestCommand("user123", "test data");
        
        // When
        commandBus.send(command)
            .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                // Then
                assertEquals("Processed: test data", result);
                testContext.completeNow();
            })));
    }
    
    @Test
    void shouldFailWhenNoHandlerFound(VertxTestContext testContext) {
        // Given
        TestCommand command = new TestCommand("user123", "test data");
        
        // When
        commandBus.send(command)
            .onComplete(testContext.failing(error -> testContext.verify(() -> {
                // Then
                assertInstanceOf(CommandNotFoundException.class, error);
                assertTrue(error.getMessage().contains("TestCommand"));
                testContext.completeNow();
            })));
    }
    
    @Test
    void shouldHaveCorrectInitialState() {
        assertEquals(0, commandBus.getHandlerCount());
        assertFalse(commandBus.hasHandler(TestCommand.class));
    }
}