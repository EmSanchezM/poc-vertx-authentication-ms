package com.auth.microservice.infrastructure.adapter.repository;

import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.port.Pagination;
import com.auth.microservice.domain.port.PermissionRepository;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * PostgreSQL implementation of PermissionRepository
 */
public class PermissionRepositoryImpl extends AbstractRepository<Permission, UUID> implements PermissionRepository {
    
    private static final String TABLE_NAME = "permissions";
    private static final String ID_COLUMN = "id";
    
    private static final String INSERT_SQL = """
        INSERT INTO permissions (id, name, resource, action, description)
        VALUES ($1, $2, $3, $4, $5)
        RETURNING *
        """;
    
    private static final String UPDATE_SQL = """
        UPDATE permissions 
        SET name = $2, resource = $3, action = $4, description = $5
        WHERE id = $1
        RETURNING *
        """;
    
    private static final String FIND_BY_NAME_SQL = """
        SELECT * FROM permissions WHERE name = $1
        """;
    
    private static final String FIND_BY_RESOURCE_AND_ACTION_SQL = """
        SELECT * FROM permissions WHERE resource = $1 AND action = $2
        """;
    
    private static final String FIND_BY_ROLE_ID_SQL = """
        SELECT p.* FROM permissions p
        INNER JOIN role_permissions rp ON p.id = rp.permission_id
        WHERE rp.role_id = $1
        ORDER BY p.name
        """;
    
    private static final String FIND_BY_USER_ID_SQL = """
        SELECT DISTINCT p.* FROM permissions p
        INNER JOIN role_permissions rp ON p.id = rp.permission_id
        INNER JOIN user_roles ur ON rp.role_id = ur.role_id
        WHERE ur.user_id = $1
        ORDER BY p.name
        """;
    
    private static final String FIND_BY_RESOURCE_SQL = """
        SELECT * FROM permissions WHERE resource = $1
        ORDER BY action
        """;
    
    private static final String FIND_ALL_PAGINATED_SQL = """
        SELECT * FROM permissions 
        ORDER BY %s %s
        LIMIT $1 OFFSET $2
        """;
    
    private static final String EXISTS_BY_NAME_SQL = """
        SELECT 1 FROM permissions WHERE name = $1 LIMIT 1
        """;
    
    private static final String EXISTS_BY_RESOURCE_AND_ACTION_SQL = """
        SELECT 1 FROM permissions WHERE resource = $1 AND action = $2 LIMIT 1
        """;
    
    public PermissionRepositoryImpl(Pool pool, TransactionManager transactionManager) {
        super(pool, transactionManager);
    }
    
    @Override
    protected String getTableName() {
        return TABLE_NAME;
    }
    
    @Override
    protected String getIdColumnName() {
        return ID_COLUMN;
    }
    
    @Override
    protected Permission mapRowToEntity(Row row) {
        UUID id = SqlUtils.getUUID(row, "id");
        String name = SqlUtils.getString(row, "name");
        String resource = SqlUtils.getString(row, "resource");
        String action = SqlUtils.getString(row, "action");
        String description = SqlUtils.getString(row, "description");
        
        return new Permission(id, name, resource, action, description);
    }
    
    @Override
    protected String getInsertSql() {
        return INSERT_SQL;
    }
    
    @Override
    protected String getUpdateSql() {
        return UPDATE_SQL;
    }
    
    @Override
    protected Tuple createInsertTuple(Permission permission) {
        return Tuple.of(
            permission.getId(),
            permission.getName(),
            permission.getResource(),
            permission.getAction(),
            permission.getDescription()
        );
    }
    
    @Override
    protected Tuple createUpdateTuple(Permission permission) {
        return Tuple.of(
            permission.getId(),
            permission.getName(),
            permission.getResource(),
            permission.getAction(),
            permission.getDescription()
        );
    }
    
    @Override
    protected UUID extractId(Permission permission) {
        return permission.getId();
    }
    
    @Override
    public Future<Optional<Permission>> findByName(String name) {
        return executeQuery(FIND_BY_NAME_SQL, Tuple.of(name))
            .map(rows -> {
                if (rows.size() == 0) {
                    return Optional.<Permission>empty();
                }
                return Optional.of(mapRowToEntity(rows.iterator().next()));
            })
            .onFailure(error -> logger.error("Failed to find permission by name: {}", name, error));
    }
    
    @Override
    public Future<Optional<Permission>> findByResourceAndAction(String resource, String action) {
        return executeQuery(FIND_BY_RESOURCE_AND_ACTION_SQL, Tuple.of(resource, action))
            .map(rows -> {
                if (rows.size() == 0) {
                    return Optional.<Permission>empty();
                }
                return Optional.of(mapRowToEntity(rows.iterator().next()));
            })
            .onFailure(error -> logger.error("Failed to find permission by resource and action: {} {}", resource, action, error));
    }
    
    @Override
    public Future<Set<Permission>> findByRoleId(UUID roleId) {
        return executeQuery(FIND_BY_ROLE_ID_SQL, Tuple.of(roleId))
            .map(rows -> {
                Set<Permission> permissions = new HashSet<>();
                for (Row row : rows) {
                    permissions.add(mapRowToEntity(row));
                }
                return permissions;
            })
            .onFailure(error -> logger.error("Failed to find permissions by role ID: {}", roleId, error));
    }
    
    @Override
    public Future<Set<Permission>> findByUserId(UUID userId) {
        return executeQuery(FIND_BY_USER_ID_SQL, Tuple.of(userId))
            .map(rows -> {
                Set<Permission> permissions = new HashSet<>();
                for (Row row : rows) {
                    permissions.add(mapRowToEntity(row));
                }
                return permissions;
            })
            .onFailure(error -> logger.error("Failed to find permissions by user ID: {}", userId, error));
    }
    
    @Override
    public Future<List<Permission>> findByResource(String resource) {
        return executeQuery(FIND_BY_RESOURCE_SQL, Tuple.of(resource))
            .map(this::mapRowsToEntities)
            .onFailure(error -> logger.error("Failed to find permissions by resource: {}", resource, error));
    }
    
    @Override
    public Future<List<Permission>> findAll(Pagination pagination) {
        String sortBy = SqlUtils.sanitizeColumnName(pagination.getSortBy());
        String direction = SqlUtils.validateSortDirection(pagination.getDirection().name());
        String sql = String.format(FIND_ALL_PAGINATED_SQL, sortBy, direction);
        
        return executeQuery(sql, Tuple.of(pagination.getSize(), pagination.getOffset()))
            .map(this::mapRowsToEntities)
            .onFailure(error -> logger.error("Failed to find all permissions with pagination", error));
    }
    
    @Override
    public Future<Boolean> existsByName(String name) {
        return executeQuery(EXISTS_BY_NAME_SQL, Tuple.of(name))
            .map(rows -> rows.size() > 0)
            .onFailure(error -> logger.error("Failed to check if permission name exists: {}", name, error));
    }
    
    @Override
    public Future<Boolean> existsByResourceAndAction(String resource, String action) {
        return executeQuery(EXISTS_BY_RESOURCE_AND_ACTION_SQL, Tuple.of(resource, action))
            .map(rows -> rows.size() > 0)
            .onFailure(error -> logger.error("Failed to check if permission exists by resource and action: {} {}", resource, action, error));
    }
}