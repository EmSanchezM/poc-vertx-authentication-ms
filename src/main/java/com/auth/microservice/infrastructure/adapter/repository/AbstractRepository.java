package com.auth.microservice.infrastructure.adapter.repository;

import com.auth.microservice.domain.port.Repository;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Abstract base repository implementation with common CRUD operations
 * @param <T> Entity type
 * @param <ID> ID type
 */
public abstract class AbstractRepository<T, ID> implements Repository<T, ID> {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final Pool pool;
    protected final TransactionManager transactionManager;
    
    protected AbstractRepository(Pool pool, TransactionManager transactionManager) {
        this.pool = pool;
        this.transactionManager = transactionManager;
    }
    
    /**
     * Get the table name for this entity
     * @return Table name
     */
    protected abstract String getTableName();
    
    /**
     * Get the ID column name
     * @return ID column name
     */
    protected abstract String getIdColumnName();
    
    /**
     * Map a database row to an entity
     * @param row Database row
     * @return Mapped entity
     */
    protected abstract T mapRowToEntity(Row row);
    
    /**
     * Get insert SQL statement
     * @return Insert SQL
     */
    protected abstract String getInsertSql();
    
    /**
     * Get update SQL statement
     * @return Update SQL
     */
    protected abstract String getUpdateSql();
    
    /**
     * Create tuple for insert operation
     * @param entity Entity to insert
     * @return Tuple with insert parameters
     */
    protected abstract Tuple createInsertTuple(T entity);
    
    /**
     * Create tuple for update operation
     * @param entity Entity to update
     * @return Tuple with update parameters
     */
    protected abstract Tuple createUpdateTuple(T entity);
    
    /**
     * Extract ID from entity
     * @param entity Entity
     * @return Entity ID
     */
    protected abstract ID extractId(T entity);
    
    @Override
    public Future<Optional<T>> findById(ID id) {
        String sql = "SELECT * FROM " + getTableName() + " WHERE " + getIdColumnName() + " = $1";
        
        return pool.preparedQuery(sql)
            .execute(Tuple.of(id))
            .map(rows -> {
                if (rows.size() == 0) {
                    return Optional.<T>empty();
                }
                return Optional.of(mapRowToEntity(rows.iterator().next()));
            })
            .onFailure(error -> logger.error("Failed to find entity by ID: {}", id, error));
    }
    
    @Override
    public Future<List<T>> findAll() {
        String sql = "SELECT * FROM " + getTableName() + " ORDER BY " + getIdColumnName();
        
        return pool.preparedQuery(sql)
            .execute()
            .map(this::mapRowsToEntities)
            .onFailure(error -> logger.error("Failed to find all entities", error));
    }
    
    @Override
    public Future<T> save(T entity) {
        return pool.preparedQuery(getInsertSql())
            .execute(createInsertTuple(entity))
            .map(rows -> {
                if (rows.size() == 0) {
                    throw new RuntimeException("Insert operation did not return any rows");
                }
                return mapRowToEntity(rows.iterator().next());
            })
            .onFailure(error -> logger.error("Failed to save entity: {}", entity, error));
    }
    
    @Override
    public Future<T> update(T entity) {
        return pool.preparedQuery(getUpdateSql())
            .execute(createUpdateTuple(entity))
            .map(rows -> {
                if (rows.size() == 0) {
                    throw new RuntimeException("Update operation did not return any rows");
                }
                return mapRowToEntity(rows.iterator().next());
            })
            .onFailure(error -> logger.error("Failed to update entity: {}", entity, error));
    }
    
    @Override
    public Future<Void> deleteById(ID id) {
        String sql = "DELETE FROM " + getTableName() + " WHERE " + getIdColumnName() + " = $1";
        
        return pool.preparedQuery(sql)
            .execute(Tuple.of(id))
            .map(rows -> {
                if (rows.rowCount() == 0) {
                    throw new RuntimeException("No entity found with ID: " + id);
                }
                return (Void) null;
            })
            .onFailure(error -> logger.error("Failed to delete entity by ID: {}", id, error));
    }
    
    @Override
    public Future<Boolean> existsById(ID id) {
        String sql = "SELECT 1 FROM " + getTableName() + " WHERE " + getIdColumnName() + " = $1 LIMIT 1";
        
        return pool.preparedQuery(sql)
            .execute(Tuple.of(id))
            .map(rows -> rows.size() > 0)
            .onFailure(error -> logger.error("Failed to check existence by ID: {}", id, error));
    }
    
    @Override
    public Future<Long> count() {
        String sql = "SELECT COUNT(*) FROM " + getTableName();
        
        return pool.preparedQuery(sql)
            .execute()
            .map(rows -> {
                Row row = rows.iterator().next();
                return row.getLong(0);
            })
            .onFailure(error -> logger.error("Failed to count entities", error));
    }
    
    /**
     * Execute a query with parameters
     * @param sql SQL query
     * @param params Query parameters
     * @return Future containing row set
     */
    protected Future<RowSet<Row>> executeQuery(String sql, Tuple params) {
        return pool.preparedQuery(sql).execute(params);
    }
    
    /**
     * Execute a query without parameters
     * @param sql SQL query
     * @return Future containing row set
     */
    protected Future<RowSet<Row>> executeQuery(String sql) {
        return pool.preparedQuery(sql).execute();
    }
    
    /**
     * Map multiple rows to entities
     * @param rows Database rows
     * @return List of entities
     */
    protected List<T> mapRowsToEntities(RowSet<Row> rows) {
        List<T> entities = new ArrayList<>();
        for (Row row : rows) {
            entities.add(mapRowToEntity(row));
        }
        return entities;
    }
    
    /**
     * Build ORDER BY clause for pagination
     * @param sortBy Sort field
     * @param direction Sort direction
     * @return ORDER BY clause
     */
    protected String buildOrderByClause(String sortBy, String direction) {
        return " ORDER BY " + sortBy + " " + direction;
    }
    
    /**
     * Build LIMIT and OFFSET clause for pagination
     * @param limit Number of records to limit
     * @param offset Number of records to skip
     * @return LIMIT OFFSET clause
     */
    protected String buildLimitOffsetClause(int limit, int offset) {
        return " LIMIT " + limit + " OFFSET " + offset;
    }
}