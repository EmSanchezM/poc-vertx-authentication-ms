package com.auth.microservice.common.cqrs;

import io.vertx.core.Future;

/**
 * Test query handler for unit testing CQRS infrastructure.
 */
public class TestQueryHandler implements QueryHandler<TestQuery, String> {
    
    @Override
    public Future<String> handle(TestQuery query) {
        return Future.succeededFuture("Result: " + query.getParameter());
    }
    
    @Override
    public Class<TestQuery> getQueryType() {
        return TestQuery.class;
    }
}