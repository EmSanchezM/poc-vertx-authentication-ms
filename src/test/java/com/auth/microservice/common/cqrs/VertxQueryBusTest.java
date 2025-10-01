package com.auth.microservice.common.cqrs;

import com.auth.microservice.common.cqrs.exceptions.HandlerRegistrationException;
import com.auth.microservice.common.cqrs.exceptions.QueryNotFoundException;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class VertxQueryBusTest {
    
    private VertxQueryBus queryBus;
    private TestQueryHandler testHandler;
    
    @BeforeEach
    void setUp(Vertx vertx) {
        queryBus = new VertxQueryBus(vertx);
        testHandler = new TestQueryHandler();
    }
    
    @Test
    void shouldRegisterQueryHandler() {
        // When
        queryBus.registerHandler(testHandler);
        
        // Then
        assertEquals(1, queryBus.getHandlerCount());
        assertTrue(queryBus.hasHandler(TestQuery.class));
    }
    
    @Test
    void shouldThrowExceptionWhenRegisteringDuplicateHandler() {
        // Given
        queryBus.registerHandler(testHandler);
        
        // When & Then
        assertThrows(HandlerRegistrationException.class, () -> {
            queryBus.registerHandler(new TestQueryHandler());
        });
    }
    
    @Test
    void shouldExecuteQuerySuccessfully(VertxTestContext testContext) {
        // Given
        queryBus.registerHandler(testHandler);
        TestQuery query = new TestQuery("test parameter");
        
        // When
        queryBus.send(query)
            .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                // Then
                assertEquals("Result: test parameter", result);
                testContext.completeNow();
            })));
    }
    
    @Test
    void shouldFailWhenNoHandlerFound(VertxTestContext testContext) {
        // Given
        TestQuery query = new TestQuery("test parameter");
        
        // When
        queryBus.send(query)
            .onComplete(testContext.failing(error -> testContext.verify(() -> {
                // Then
                assertInstanceOf(QueryNotFoundException.class, error);
                assertTrue(error.getMessage().contains("TestQuery"));
                testContext.completeNow();
            })));
    }
    
    @Test
    void shouldHaveCorrectInitialState() {
        assertEquals(0, queryBus.getHandlerCount());
        assertFalse(queryBus.hasHandler(TestQuery.class));
    }
}