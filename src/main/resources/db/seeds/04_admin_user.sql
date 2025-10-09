-- Archivo de seed para usuario administrador
-- Passowrd: A$d.45678

INSERT INTO users (email, password_hash, first_name, last_name, username, is_active)
VALUES (
    'admin@auth-microservice.com',
    '$2a$10$jIwokCTxy82rXWweAW9HOupXtIZ.wQhcsbULnzt3SDkfOuiX4yeV2',
    'System',
    'Administrator',
    'systemadmin',
    true
);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.email = 'admin@auth-microservice.com'
AND r.name = 'SUPER_ADMIN';

