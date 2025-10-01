package com.auth.microservice.common.cqrs;

/**
 * Test query for unit testing CQRS infrastructure.
 */
public class TestQuery extends Query {
    
    private final String parameter;
    
    public TestQuery(String parameter) {
        super(null);
        this.parameter = parameter;
    }
    
    public String getParameter() {
        return parameter;
    }
}