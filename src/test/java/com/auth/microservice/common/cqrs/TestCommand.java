package com.auth.microservice.common.cqrs;

/**
 * Test command for unit testing CQRS infrastructure.
 */
public class TestCommand extends Command {
    
    private final String data;
    
    public TestCommand(String userId, String data) {
        super(userId);
        this.data = data;
    }
    
    public String getData() {
        return data;
    }
}