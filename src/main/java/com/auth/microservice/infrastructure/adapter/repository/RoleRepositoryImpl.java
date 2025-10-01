package com.auth.microservice.infrastructure.adapter.repository;

import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.port.Pagination;
import com.auth.microservice.domain.port.RoleRepository;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL implementation of RoleRepository
 */
public class RoleRepositoryImpl extends AbstractRepository<Role, UUID> implements RoleRepository {
    
    private static final String TABLE_NAME = "roles";
    private static final String ID_COLUMN = "id";
    
    private static final String INSERT_SQL = """
        INSERT INTO roles (id, name, description, created_at)
        VALUES ($1, $2, $3, $4)
        RETURNING *
        """;
    
    private static final String UPDATE_SQL = """
        UPDATE roles 
        SET name = $2, description = $3
        WHERE id = $1
        RETURNING *
        """;
    
    private static final String FIND_BY_NAME_SQL = """
        SELECT * FROM roles WHERE name = $1
        """;
    
    private static final String FIND_BY_ID_WITH_PERMISSIONS_SQL = """
        SELECT r.*, p.id as permission_id, p.name as permission_name, p.resource as permission_resource, 
               p.action as permission_action, p.description as permission_description
        FROM roles r
        LEFT JOIN role_permissions rp ON r.id = rp.role_id
        LEFT JOIN permissions p ON rp.permission_id = p.id
        WHERE r.id = $1
        """;
    
    private static final String FIND_BY_NAME_WITH_PERMISSIONS_SQL = """
        SELECT r.*, p.id as permission_id, p.name as permission_name, p.resource as permission_resource, 
               p.action as permission_action, p.description as permission_description
        FROM roles r
        LEFT JOIN role_permissions rp ON r.id = rp.role_id
        LEFT JOIN permissions p ON rp.permission_id = p.id
        WHERE r.name = $1
        """;
    
    private static final String FIND_BY_USER_ID_SQL = """
        SELECT r.* FROM roles r
        INNER JOIN user_roles ur ON r.id = ur.role_id
        WHERE ur.user_id = $1
        ORDER BY r.name
        """;
    
    private static final String FIND_BY_USER_ID_WITH_PERMISSIONS_SQL = """
        SELECT r.*, p.id as permission_id, p.name as permission_name, p.resource as permission_resource, 
               p.action as permission_action, p.description as permission_description
        FROM roles r
        INNER JOIN user_roles ur ON r.id = ur.role_id
        LEFT JOIN role_permissions rp ON r.id = rp.role_id
        LEFT JOIN permissions p ON rp.permission_id = p.id
        WHERE ur.user_id = $1
        ORDER BY r.name, p.name
        """;
    
    private static final String FIND_ALL_PAGINATED_SQL = """
        SELECT * FROM roles 
        ORDER BY %s %s
        LIMIT $1 OFFSET $2
        """;
    
    private static final String EXISTS_BY_NAME_SQL = """
        SELECT 1 FROM roles WHERE name = $1 LIMIT 1
        """;
    
    public RoleRepositoryImpl(Pool pool, TransactionManager transactionManager) {
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
    protected Role mapRowToEntity(Row row) {
        UUID id = SqlUtils.getUUID(row, "id");
        String name = SqlUtils.getString(row, "name");
        String description = SqlUtils.getString(row, "description");
        LocalDateTime createdAt = SqlUtils.getLocalDateTime(row, "created_at");
        
        return new Role(id, name, description, createdAt);
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
    protected Tuple createInsertTuple(Role role) {
        return Tuple.of(
            role.getId(),
            role.getName(),
            role.getDescription(),
            role.getCreatedAt()
        );
    }
    
    @Override
    protected Tuple createUpdateTuple(Role role) {
        return Tuple.of(
            role.getId(),
            role.getName(),
            role.getDescription()
        );
    }
    
    @Override
    protected UUID extractId(Role role) {
        return role.getId();
    }
    
    @Override
    public Future<Optional<Role>> findByName(String name) {
        return executeQuery(FIND_BY_NAME_SQL, Tuple.of(name))
            .map(rows -> {
                if (rows.size() == 0) {
                    return Optional.<Role>empty();
                }
                return Optional.of(mapRowToEntity(rows.iterator().next()));
            })
            .onFailure(error -> logger.error("Failed to find role by name: {}", name, error));
    }
    
    @Override
    public Future<Optional<Role>> findByIdWithPermissions(UUID roleId) {
        return executeQuery(FIND_BY_ID_WITH_PERMISSIONS_SQL, Tuple.of(roleId))
            .map(rows -> {
                if (rows.size() == 0) {
                    return Optional.<Role>empty();
                }
                return Optional.of(mapRoleWithPermissions(rows));
            })
            .onFailure(error -> logger.error("Failed to find role with permissions by ID: {}", roleId, error));
    }
    
    @Override
    public Future<Optional<Role>> findByNameWithPermissions(String name) {
        return executeQuery(FIND_BY_NAME_WITH_PERMISSIONS_SQL, Tuple.of(name))
            .map(rows -> {
                if (rows.size() == 0) {
                    return Optional.<Role>empty();
                }
                return Optional.of(mapRoleWithPermissions(rows));
            })
            .onFailure(error -> logger.error("Failed to find role with permissions by name: {}", name, error));
    }
    
    @Override
    public Future<List<Role>> findByUserId(UUID userId) {
        return executeQuery(FIND_BY_USER_ID_SQL, Tuple.of(userId))
            .map(this::mapRowsToEntities)
            .onFailure(error -> logger.error("Failed to find roles by user ID: {}", userId, error));
    }
    
    @Override
    public Future<List<Role>> findByUserIdWithPermissions(UUID userId) {
        return executeQuery(FIND_BY_USER_ID_WITH_PERMISSIONS_SQL, Tuple.of(userId))
            .map(rows -> {
                return mapRolesWithPermissions(rows);
            })
            .onFailure(error -> logger.error("Failed to find roles with permissions by user ID: {}", userId, error));
    }
    
    @Override
    public Future<List<Role>> findAll(Pagination pagination) {
        String sortBy = SqlUtils.sanitizeColumnName(pagination.getSortBy());
        String direction = SqlUtils.validateSortDirection(pagination.getDirection().name());
        String sql = String.format(FIND_ALL_PAGINATED_SQL, sortBy, direction);
        
        return executeQuery(sql, Tuple.of(pagination.getSize(), pagination.getOffset()))
            .map(this::mapRowsToEntities)
            .onFailure(error -> logger.error("Failed to find all roles with pagination", error));
    }
    
    @Override
    public Future<Boolean> existsByName(String name) {
        return executeQuery(EXISTS_BY_NAME_SQL, Tuple.of(name))
            .map(rows -> rows.size() > 0)
            .onFailure(error -> logger.error("Failed to check if role name exists: {}", name, error));
    }
    
    /**
     * Map database rows to Role entity with permissions
     * @param rows Database rows containing role and permission data
     * @return Role entity with permissions populated
     */
    private Role mapRoleWithPermissions(io.vertx.sqlclient.RowSet<Row> rows) {
        Row firstRow = rows.iterator().next();
        Role role = mapRowToEntity(firstRow);
        
        // Add permissions to role
        for (Row row : rows) {
            UUID permissionId = SqlUtils.getUUID(row, "permission_id");
            if (permissionId != null) {
                String permissionName = SqlUtils.getString(row, "permission_name");
                String resource = SqlUtils.getString(row, "permission_resource");
                String action = SqlUtils.getString(row, "permission_action");
                String permissionDescription = SqlUtils.getString(row, "permission_description");
                
                Permission permission = new Permission(permissionId, permissionName, resource, action, permissionDescription);
                role.addPermission(permission);
            }
        }
        
        return role;
    }
    
    /**
     * Map database rows to multiple Role entities with permissions
     * @param rows Database rows containing role and permission data
     * @return List of Role entities with permissions populated
     */
    private List<Role> mapRolesWithPermissions(io.vertx.sqlclient.RowSet<Row> rows) {
        java.util.Map<UUID, Role> roleMap = new java.util.HashMap<>();
        
        for (Row row : rows) {
            UUID roleId = SqlUtils.getUUID(row, "id");
            
            // Get or create role
            Role role = roleMap.computeIfAbsent(roleId, id -> mapRowToEntity(row));
            
            // Add permission if present
            UUID permissionId = SqlUtils.getUUID(row, "permission_id");
            if (permissionId != null) {
                String permissionName = SqlUtils.getString(row, "permission_name");
                String resource = SqlUtils.getString(row, "permission_resource");
                String action = SqlUtils.getString(row, "permission_action");
                String permissionDescription = SqlUtils.getString(row, "permission_description");
                
                Permission permission = new Permission(permissionId, permissionName, resource, action, permissionDescription);
                role.addPermission(permission);
            }
        }
        
        return new java.util.ArrayList<>(roleMap.values());
    }
}