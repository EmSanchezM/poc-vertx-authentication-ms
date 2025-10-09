-- Archivo de seed para usuarios de prueba

INSERT INTO users (email, password_hash, first_name, last_name, username, is_active)
VALUES 
    (
        'user@example.com',
        '$2a$10$jIwokCTxy82rXWweAW9HOupXtIZ.wQhcsbULnzt3SDkfOuiX4yeV2',
        'Test',
        'User',
        'testuser',
        true
    ),
    (
        'manager@example.com',
        '$2a$10$jIwokCTxy82rXWweAW9HOupXtIZ.wQhcsbULnzt3SDkfOuiX4yeV2',
        'Test',
        'Manager',
        'testmanager',
        true
    );

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r
WHERE u.email = 'user@example.com' AND r.name = 'USER';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r
WHERE u.email = 'manager@example.com' AND r.name = 'USER_MANAGER';