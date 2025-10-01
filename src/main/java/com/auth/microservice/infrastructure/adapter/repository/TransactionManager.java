package com.auth.microservice.infrastructure.adapter.repository;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Transaction;
import java.util.function.Function;

/**
 * Transaction manager for database operations
 */
public interface TransactionManager {
    
    /**
     * Execute operations within a transaction
     * @param operation Function that performs database operations
     * @param <T> Return type
     * @return Future containing the result
     */
    <T> Future<T> executeInTransaction(Function<SqlConnection, Future<T>> operation);
    
    /**
     * Execute operations within a transaction with explicit connection
     * @param connection Database connection
     * @param operation Function that performs database operations
     * @param <T> Return type
     * @return Future containing the result
     */
    <T> Future<T> executeInTransaction(SqlConnection connection, Function<Transaction, Future<T>> operation);
}