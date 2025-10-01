package com.auth.microservice.infrastructure.adapter.repository;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Vert.x implementation of TransactionManager
 */
public class VertxTransactionManager implements TransactionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(VertxTransactionManager.class);
    
    private final Pool pool;
    
    public VertxTransactionManager(Pool pool) {
        this.pool = pool;
    }
    
    @Override
    public <T> Future<T> executeInTransaction(Function<SqlConnection, Future<T>> operation) {
        Promise<T> promise = Promise.promise();
        
        pool.getConnection()
            .onSuccess(connection -> {
                connection.begin()
                    .onSuccess(transaction -> {
                        operation.apply(connection)
                            .onSuccess(result -> {
                                transaction.commit()
                                    .onSuccess(v -> {
                                        connection.close();
                                        promise.complete(result);
                                    })
                                    .onFailure(commitError -> {
                                        logger.error("Failed to commit transaction", commitError);
                                        connection.close();
                                        promise.fail(commitError);
                                    });
                            })
                            .onFailure(operationError -> {
                                logger.error("Operation failed, rolling back transaction", operationError);
                                transaction.rollback()
                                    .onComplete(rollbackResult -> {
                                        connection.close();
                                        promise.fail(operationError);
                                    });
                            });
                    })
                    .onFailure(transactionError -> {
                        logger.error("Failed to begin transaction", transactionError);
                        connection.close();
                        promise.fail(transactionError);
                    });
            })
            .onFailure(connectionError -> {
                logger.error("Failed to get database connection", connectionError);
                promise.fail(connectionError);
            });
        
        return promise.future();
    }
    
    @Override
    public <T> Future<T> executeInTransaction(SqlConnection connection, Function<Transaction, Future<T>> operation) {
        Promise<T> promise = Promise.promise();
        
        connection.begin()
            .onSuccess(transaction -> {
                operation.apply(transaction)
                    .onSuccess(result -> {
                        transaction.commit()
                            .onSuccess(v -> promise.complete(result))
                            .onFailure(commitError -> {
                                logger.error("Failed to commit transaction", commitError);
                                promise.fail(commitError);
                            });
                    })
                    .onFailure(operationError -> {
                        logger.error("Operation failed, rolling back transaction", operationError);
                        transaction.rollback()
                            .onComplete(rollbackResult -> promise.fail(operationError));
                    });
            })
            .onFailure(transactionError -> {
                logger.error("Failed to begin transaction", transactionError);
                promise.fail(transactionError);
            });
        
        return promise.future();
    }
}