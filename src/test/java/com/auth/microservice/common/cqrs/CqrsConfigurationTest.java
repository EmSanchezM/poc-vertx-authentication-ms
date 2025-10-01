package com.auth.microservice.common.cqrs;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class CqrsConfigurationTest {
    
    private CqrsConfiguration cqrsConfiguration;
    
    @BeforeEach
    void setUp(Vertx vertx) {
        cqrsConfiguration = new CqrsConfiguration(vertx);
    }
    
    @Test
    void shouldInitializeCommandAndQueryBuses() {
        // Then
        assertNotNull(cqrsConfiguration.getCommandBus());
        assertNotNull(cqrsConfiguration.getQueryBus());
        assertInstanceOf(VertxCommandBus.class, cqrsConfiguration.getCommandBus());
        assertInstanceOf(VertxQueryBus.class, cqrsConfiguration.getQueryBus());
    }
    
    @Test
    void shouldRegisterCommandHandlers() {
        // Given
        List<CommandHandler<?, ?>> handlers = List.of(new TestCommandHandler());
        
        // When
        cqrsConfiguration.registerCommandHandlers(handlers);
        
        // Then
        VertxCommandBus commandBus = (VertxCommandBus) cqrsConfiguration.getCommandBus();
        assertEquals(1, commandBus.getHandlerCount());
        assertTrue(commandBus.hasHandler(TestCommand.class));
    }
    
    @Test
    void shouldRegisterQueryHandlers() {
        // Given
        List<QueryHandler<?, ?>> handlers = List.of(new TestQueryHandler());
        
        // When
        cqrsConfiguration.registerQueryHandlers(handlers);
        
        // Then
        VertxQueryBus queryBus = (VertxQueryBus) cqrsConfiguration.getQueryBus();
        assertEquals(1, queryBus.getHandlerCount());
        assertTrue(queryBus.hasHandler(TestQuery.class));
    }
    
    @Test
    void shouldRegisterAllHandlers() {
        // Given
        List<CommandHandler<?, ?>> commandHandlers = List.of(new TestCommandHandler());
        List<QueryHandler<?, ?>> queryHandlers = List.of(new TestQueryHandler());
        
        // When
        cqrsConfiguration.registerAllHandlers(commandHandlers, queryHandlers);
        
        // Then
        VertxCommandBus commandBus = (VertxCommandBus) cqrsConfiguration.getCommandBus();
        VertxQueryBus queryBus = (VertxQueryBus) cqrsConfiguration.getQueryBus();
        
        assertEquals(1, commandBus.getHandlerCount());
        assertEquals(1, queryBus.getHandlerCount());
        assertTrue(commandBus.hasHandler(TestCommand.class));
        assertTrue(queryBus.hasHandler(TestQuery.class));
    }
}