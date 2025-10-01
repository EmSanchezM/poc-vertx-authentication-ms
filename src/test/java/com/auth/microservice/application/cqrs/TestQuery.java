package com.auth.microservice.application.cqrs;

/**
 * Test query for unit testing CQRS infrastructure.
 */
public class TestQuery extends Query<String> {
    
    private final String parameter;
    
    public TestQuery(String parameter) {
        this.parameter = parameter;
    }
    
    public String getParameter() {
        return parameter;
    }
}