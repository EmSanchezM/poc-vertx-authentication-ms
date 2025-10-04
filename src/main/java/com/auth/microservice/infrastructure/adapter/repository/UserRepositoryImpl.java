package com.auth.microservice.infrastructure.adapter.repository;

import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.Pagination;
import com.auth.microservice.domain.port.UserRepository;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL implementation of UserRepository
 */
public class UserRepositoryImpl extends AbstractRepository<User, UUID> implements UserRepository {
    
    private static final String TABLE_NAME = "users";
    private static final String ID_COLUMN = "id";
    
    private static final String INSERT_SQL = """
        INSERT INTO users (id, username, email, password_hash, first_name, last_name, is_active, created_at, updated_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
        RETURNING *
        """;
    
    private static final String UPDATE_SQL = """
        UPDATE users 
        SET username = $2, email = $3, password_hash = $4, first_name = $5, last_name = $6, 
            is_active = $7, updated_at = $8
        WHERE id = $1
        RETURNING *
        """;
    
    private static final String FIND_BY_EMAIL_SQL = """
        SELECT * FROM users WHERE email = $1
        """;
    
    private static final String FIND_BY_USERNAME_SQL = """
        SELECT * FROM users WHERE username = $1
        """;
    
    private static final String FIND_BY_ID_WITH_ROLES_SQL = """
        SELECT u.*, r.id as role_id, r.name as role_name, r.description as role_description, r.created_at as role_created_at
        FROM users u
        LEFT JOIN user_roles ur ON u.id = ur.user_id
        LEFT JOIN roles r ON ur.role_id = r.id
        WHERE u.id = $1
        """;
    
    private static final String FIND_BY_EMAIL_WITH_ROLES_SQL = """
        SELECT u.*, r.id as role_id, r.name as role_name, r.description as role_description, r.created_at as role_created_at
        FROM users u
        LEFT JOIN user_roles ur ON u.id = ur.user_id
        LEFT JOIN roles r ON ur.role_id = r.id
        WHERE u.email = $1
        """;
    
    private static final String FIND_BY_USERNAME_WITH_ROLES_SQL = """
        SELECT u.*, r.id as role_id, r.name as role_name, r.description as role_description, r.created_at as role_created_at
        FROM users u
        LEFT JOIN user_roles ur ON u.id = ur.user_id
        LEFT JOIN roles r ON ur.role_id = r.id
        WHERE u.username = $1
        """;
    
    private static final String FIND_ALL_PAGINATED_SQL = """
        SELECT * FROM users 
        ORDER BY %s %s
        LIMIT $1 OFFSET $2
        """;
    
    private static final String FIND_ACTIVE_USERS_SQL = """
        SELECT * FROM users 
        WHERE is_active = true
        ORDER BY %s %s
        LIMIT $1 OFFSET $2
        """;
    
    private static final String EXISTS_BY_EMAIL_SQL = """
        SELECT 1 FROM users WHERE email = $1 LIMIT 1
        """;
    
    private static final String EXISTS_BY_USERNAME_SQL = """
        SELECT 1 FROM users WHERE username = $1 LIMIT 1
        """;
    
    private static final String SEARCH_USERS_SQL = """
        SELECT * FROM users 
        WHERE (LOWER(first_name) LIKE LOWER($1) OR LOWER(last_name) LIKE LOWER($1) OR LOWER(email) LIKE LOWER($1))
        %s
        ORDER BY %s %s
        LIMIT $2 OFFSET $3
        """;
    
    private static final String SEARCH_ACTIVE_FILTER = "AND is_active = true";
    
    public UserRepositoryImpl(Pool pool, TransactionManager transactionManager) {
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
    protected User mapRowToEntity(Row row) {
        UUID id = SqlUtils.getUUID(row, "id");
        String username = SqlUtils.getString(row, "username");
        String emailValue = SqlUtils.getString(row, "email");
        String passwordHash = SqlUtils.getString(row, "password_hash");
        String firstName = SqlUtils.getString(row, "first_name");
        String lastName = SqlUtils.getString(row, "last_name");
        Boolean isActive = SqlUtils.getBoolean(row, "is_active");
        LocalDateTime createdAt = SqlUtils.getLocalDateTime(row, "created_at");
        LocalDateTime updatedAt = SqlUtils.getLocalDateTime(row, "updated_at");
        
        Email email = new Email(emailValue);
        
        return new User(id, username, email, passwordHash, firstName, lastName, 
                       isActive, createdAt, updatedAt);
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
    protected Tuple createInsertTuple(User user) {
        return Tuple.of(
            user.getId(),
            user.getUsername(),
            user.getEmail().getValue(),
            user.getPasswordHash(),
            user.getFirstName(),
            user.getLastName(),
            user.isActive(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
    
    @Override
    protected Tuple createUpdateTuple(User user) {
        return Tuple.of(
            user.getId(),
            user.getUsername(),
            user.getEmail().getValue(),
            user.getPasswordHash(),
            user.getFirstName(),
            user.getLastName(),
            user.isActive(),
            user.getUpdatedAt()
        );
    }
    
    @Override
    protected UUID extractId(User user) {
        return user.getId();
    }
    
    @Override
    public Future<Optional<User>> findByEmail(Email email) {
        return executeQuery(FIND_BY_EMAIL_SQL, Tuple.of(email.getValue()))
            .map(rows -> {
                if (rows.size() == 0) {
                    return Optional.<User>empty();
                }
                return Optional.of(mapRowToEntity(rows.iterator().next()));
            })
            .onFailure(error -> logger.error("Failed to find user by email: {}", email.getValue(), error));
    }
    
    @Override
    public Future<Optional<User>> findByUsername(String username) {
        return executeQuery(FIND_BY_USERNAME_SQL, Tuple.of(username))
            .map(rows -> {
                if (rows.size() == 0) {
                    return Optional.<User>empty();
                }
                return Optional.of(mapRowToEntity(rows.iterator().next()));
            })
            .onFailure(error -> logger.error("Failed to find user by username: {}", username, error));
    }
    
    @Override
    public Future<Optional<User>> findByIdWithRoles(UUID userId) {
        return executeQuery(FIND_BY_ID_WITH_ROLES_SQL, Tuple.of(userId))
            .map(rows -> {
                if (rows.size() == 0) {
                    return Optional.<User>empty();
                }
                return Optional.of(mapUserWithRoles(rows));
            })
            .onFailure(error -> logger.error("Failed to find user with roles by ID: {}", userId, error));
    }
    
    @Override
    public Future<Optional<User>> findByEmailWithRoles(Email email) {
        return executeQuery(FIND_BY_EMAIL_WITH_ROLES_SQL, Tuple.of(email.getValue()))
            .map(rows -> {
                if (rows.size() == 0) {
                    return Optional.<User>empty();
                }
                return Optional.of(mapUserWithRoles(rows));
            })
            .onFailure(error -> logger.error("Failed to find user with roles by email: {}", email.getValue(), error));
    }
    
    @Override
    public Future<Optional<User>> findByUsernameWithRoles(String username) {
        return executeQuery(FIND_BY_USERNAME_WITH_ROLES_SQL, Tuple.of(username))
            .map(rows -> {
                if (rows.size() == 0) {
                    return Optional.<User>empty();
                }
                return Optional.of(mapUserWithRoles(rows));
            })
            .onFailure(error -> logger.error("Failed to find user with roles by username: {}", username, error));
    }
    
    @Override
    public Future<List<User>> findAll(Pagination pagination) {
        String sortBy = SqlUtils.sanitizeColumnName(pagination.getSortBy());
        String direction = SqlUtils.validateSortDirection(pagination.getDirection().name());
        String sql = String.format(FIND_ALL_PAGINATED_SQL, sortBy, direction);
        
        return executeQuery(sql, Tuple.of(pagination.getSize(), pagination.getOffset()))
            .map(this::mapRowsToEntities)
            .onFailure(error -> logger.error("Failed to find all users with pagination", error));
    }
    
    @Override
    public Future<List<User>> findActiveUsers(Pagination pagination) {
        String sortBy = SqlUtils.sanitizeColumnName(pagination.getSortBy());
        String direction = SqlUtils.validateSortDirection(pagination.getDirection().name());
        String sql = String.format(FIND_ACTIVE_USERS_SQL, sortBy, direction);
        
        return executeQuery(sql, Tuple.of(pagination.getSize(), pagination.getOffset()))
            .map(this::mapRowsToEntities)
            .onFailure(error -> logger.error("Failed to find active users with pagination", error));
    }
    
    @Override
    public Future<Boolean> existsByEmail(Email email) {
        return executeQuery(EXISTS_BY_EMAIL_SQL, Tuple.of(email.getValue()))
            .map(rows -> rows.size() > 0)
            .onFailure(error -> logger.error("Failed to check if email exists: {}", email.getValue(), error));
    }
    
    @Override
    public Future<Boolean> existsByUsername(String username) {
        return executeQuery(EXISTS_BY_USERNAME_SQL, Tuple.of(username))
            .map(rows -> rows.size() > 0)
            .onFailure(error -> logger.error("Failed to check if username exists: {}", username, error));
    }
    
    @Override
    public Future<List<User>> searchUsers(String searchTerm, Pagination pagination, boolean includeInactive) {
        String sortBy = SqlUtils.sanitizeColumnName(pagination.getSortBy());
        String direction = SqlUtils.validateSortDirection(pagination.getDirection().name());
        String activeFilter = includeInactive ? "" : SEARCH_ACTIVE_FILTER;
        String sql = String.format(SEARCH_USERS_SQL, activeFilter, sortBy, direction);
        
        String searchPattern = "%" + searchTerm + "%";
        
        return executeQuery(sql, Tuple.of(searchPattern, pagination.getSize(), pagination.getOffset()))
            .map(this::mapRowsToEntities)
            .onFailure(error -> logger.error("Failed to search users with term: {}", searchTerm, error));
    }
    
    @Override
    public Future<Long> countAll() {
        String sql = "SELECT COUNT(*) FROM users";
        return executeQuery(sql, Tuple.tuple())
            .map(rows -> {
                if (rows.size() > 0) {
                    return rows.iterator().next().getLong(0);
                }
                return 0L;
            })
            .onFailure(error -> logger.error("Failed to count all users", error));
    }
    
    @Override
    public Future<Long> countActive() {
        String sql = "SELECT COUNT(*) FROM users WHERE is_active = true";
        return executeQuery(sql, Tuple.tuple())
            .map(rows -> {
                if (rows.size() > 0) {
                    return rows.iterator().next().getLong(0);
                }
                return 0L;
            })
            .onFailure(error -> logger.error("Failed to count active users", error));
    }
    
    @Override
    public Future<Long> countCreatedSince(LocalDateTime since) {
        String sql = "SELECT COUNT(*) FROM users WHERE created_at >= $1";
        return executeQuery(sql, Tuple.of(since))
            .map(rows -> {
                if (rows.size() > 0) {
                    return rows.iterator().next().getLong(0);
                }
                return 0L;
            })
            .onFailure(error -> logger.error("Failed to count users created since: {}", since, error));
    }
    
    @Override
    public Future<Boolean> existsByUsernameIgnoreCase(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Future.succeededFuture(false);
        }
        
        String sql = "SELECT 1 FROM users WHERE LOWER(username) = LOWER($1) LIMIT 1";
        
        return executeQuery(sql, Tuple.of(username.trim()))
            .map(rows -> rows.size() > 0)
            .onFailure(error -> logger.error("Failed to check if username exists (case-insensitive): {}", username, error));
    }
    
    @Override
    public Future<List<String>> findUsernamesStartingWith(String usernamePrefix, int limit) {
        if (usernamePrefix == null || usernamePrefix.trim().isEmpty()) {
            return Future.succeededFuture(List.of());
        }
        
        final int finalLimit = limit <= 0 ? 100 : limit; // Default limit to prevent unbounded queries
        final String finalUsernamePrefix = usernamePrefix;
        
        String sql = """
            SELECT username FROM users 
            WHERE LOWER(username) LIKE LOWER($1) 
            ORDER BY username ASC 
            LIMIT $2
            """;
        
        String searchPattern = usernamePrefix.trim() + "%";
        
        return executeQuery(sql, Tuple.of(searchPattern, finalLimit))
            .map(rows -> {
                List<String> usernames = new java.util.ArrayList<>();
                for (Row row : rows) {
                    String username = SqlUtils.getString(row, "username");
                    if (username != null) {
                        usernames.add(username);
                    }
                }
                return usernames;
            })
            .onFailure(error -> logger.error("Failed to find usernames starting with: {} (limit: {})", finalUsernamePrefix, finalLimit, error));
    }
    
    /**
     * Map database rows to User entity with roles
     * @param rows Database rows containing user and role data
     * @return User entity with roles populated
     */
    private User mapUserWithRoles(io.vertx.sqlclient.RowSet<Row> rows) {
        Row firstRow = rows.iterator().next();
        User user = mapRowToEntity(firstRow);
        
        // Add roles to user
        for (Row row : rows) {
            UUID roleId = SqlUtils.getUUID(row, "role_id");
            if (roleId != null) {
                String roleName = SqlUtils.getString(row, "role_name");
                String roleDescription = SqlUtils.getString(row, "role_description");
                LocalDateTime roleCreatedAt = SqlUtils.getLocalDateTime(row, "role_created_at");
                
                Role role = new Role(roleId, roleName, roleDescription, roleCreatedAt);
                user.addRole(role);
            }
        }
        
        return user;
    }
}