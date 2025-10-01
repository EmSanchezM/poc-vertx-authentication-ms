package com.auth.microservice.application.cqrs;

import io.vertx.core.Future;

/**
 * Test command handler for unit testing CQRS infrastructure.
 */
public class TestCommandHandler implements CommandHandler<TestCommand, String> {
    
    @Override
    public Future<String> handle(TestCommand command) {
        return Future.succeededFuture("Processed: " + command.getData());
    }
    
    @Override
    public Class<TestCommand> getCommandType() {
        return TestCommand.class;
    }
}