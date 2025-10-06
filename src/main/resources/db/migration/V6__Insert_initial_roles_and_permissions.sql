-- Insertar permisos básicos del sistema
INSERT INTO permissions (name, resource, action, description) VALUES
-- Permisos de usuario
('USER_READ', 'user', 'read', 'Leer información de usuarios'),
('USER_CREATE', 'user', 'create', 'Crear nuevos usuarios'),
('USER_UPDATE', 'user', 'update', 'Actualizar información de usuarios'),
('USER_DELETE', 'user', 'delete', 'Eliminar usuarios'),
('USER_LIST', 'user', 'list', 'Listar usuarios del sistema'),

-- Permisos de perfil propio
('PROFILE_READ', 'profile', 'read', 'Leer el propio perfil'),
('PROFILE_UPDATE', 'profile', 'update', 'Actualizar el propio perfil'),

-- Permisos de roles
('ROLE_READ', 'role', 'read', 'Leer información de roles'),
('ROLE_CREATE', 'role', 'create', 'Crear nuevos roles'),
('ROLE_UPDATE', 'role', 'update', 'Actualizar roles existentes'),
('ROLE_DELETE', 'role', 'delete', 'Eliminar roles'),
('ROLE_ASSIGN', 'role', 'assign', 'Asignar roles a usuarios'),

-- Permisos de permisos
('PERMISSION_READ', 'permission', 'read', 'Leer información de permisos'),
('PERMISSION_CREATE', 'permission', 'create', 'Crear nuevos permisos'),
('PERMISSION_UPDATE', 'permission', 'update', 'Actualizar permisos existentes'),
('PERMISSION_DELETE', 'permission', 'delete', 'Eliminar permisos'),

-- Permisos de sesiones
('SESSION_READ', 'session', 'read', 'Leer información de sesiones'),
('SESSION_MANAGE', 'session', 'manage', 'Gestionar sesiones de usuarios'),
('SESSION_INVALIDATE', 'session', 'invalidate', 'Invalidar sesiones'),

-- Permisos de administración
('ADMIN_DASHBOARD', 'admin', 'dashboard', 'Acceder al dashboard administrativo'),
('ADMIN_LOGS', 'admin', 'logs', 'Acceder a logs del sistema'),
('ADMIN_METRICS', 'admin', 'metrics', 'Acceder a métricas del sistema'),
('ADMIN_HEALTH', 'admin', 'health', 'Acceder a health checks del sistema'),

-- Permisos de autenticación
('AUTH_LOGIN', 'auth', 'login', 'Iniciar sesión en el sistema'),
('AUTH_LOGOUT', 'auth', 'logout', 'Cerrar sesión'),
('AUTH_REFRESH', 'auth', 'refresh', 'Refrescar tokens de autenticación'),
('AUTH_REGISTER', 'auth', 'register', 'Registrar nuevos usuarios');

-- Insertar roles básicos del sistema
INSERT INTO roles (name, description) VALUES
('SUPER_ADMIN', 'Administrador con acceso completo al sistema'),
('ADMIN', 'Administrador con permisos de gestión de usuarios y roles'),
('USER_MANAGER', 'Gestor de usuarios con permisos limitados de administración'),
('USER', 'Usuario estándar con permisos básicos'),
('GUEST', 'Usuario invitado con permisos mínimos de solo lectura');

-- Asignar todos los permisos al SUPER_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'SUPER_ADMIN';

-- Asignar permisos al rol ADMIN (todos excepto algunos críticos)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
AND p.name IN (
    'USER_READ', 'USER_CREATE', 'USER_UPDATE', 'USER_LIST',
    'ROLE_READ', 'ROLE_CREATE', 'ROLE_UPDATE', 'ROLE_ASSIGN',
    'PERMISSION_READ',
    'SESSION_READ', 'SESSION_MANAGE', 'SESSION_INVALIDATE',
    'ADMIN_DASHBOARD', 'ADMIN_LOGS', 'ADMIN_METRICS', 'ADMIN_HEALTH',
    'AUTH_LOGIN', 'AUTH_LOGOUT', 'AUTH_REFRESH',
    'PROFILE_READ', 'PROFILE_UPDATE'
);

-- Asignar permisos al rol USER_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'USER_MANAGER'
AND p.name IN (
    'USER_READ', 'USER_CREATE', 'USER_UPDATE', 'USER_LIST',
    'ROLE_READ',
    'SESSION_READ',
    'AUTH_LOGIN', 'AUTH_LOGOUT', 'AUTH_REFRESH',
    'PROFILE_READ', 'PROFILE_UPDATE'
);

-- Asignar permisos al rol USER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'USER'
AND p.name IN (
    'AUTH_LOGIN', 'AUTH_LOGOUT', 'AUTH_REFRESH',
    'PROFILE_READ', 'PROFILE_UPDATE'
);

-- Asignar permisos al rol GUEST
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'GUEST'
AND p.name IN (
    'AUTH_LOGIN', 'AUTH_REGISTER'
);

-- Crear usuario administrador por defecto (contraseña: admin123)
-- Hash BCrypt de "admin123" con salt rounds = 12
INSERT INTO users (email, password_hash, first_name, last_name, username, is_active)
VALUES (
    'admin@auth-microservice.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj/RK.PJ/...',
    'System',
    'Administrator',
    'admin',
    true
);

-- Asignar rol SUPER_ADMIN al usuario administrador
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.email = 'admin@auth-microservice.com'
AND r.name = 'SUPER_ADMIN';

-- Crear algunos usuarios de ejemplo para testing
INSERT INTO users (email, password_hash, first_name, last_name, username, is_active)
VALUES 
    (
        'user@example.com',
        '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj/RK.PJ/...',
        'Test',
        'User',
        'testuser',
        true
    ),
    (
        'manager@example.com',
        '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj/RK.PJ/...',
        'Test',
        'Manager',
        'testmanager',
        true
    );

-- Asignar roles a usuarios de ejemplo
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.email = 'user@example.com' AND r.name = 'USER'

UNION ALL

SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.email = 'manager@example.com' AND r.name = 'USER_MANAGER';

-- Verificar que los datos se insertaron correctamente
DO $$
DECLARE
    permission_count INTEGER;
    role_count INTEGER;
    user_count INTEGER;
    role_permission_count INTEGER;
    user_role_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO permission_count FROM permissions;
    SELECT COUNT(*) INTO role_count FROM roles;
    SELECT COUNT(*) INTO user_count FROM users;
    SELECT COUNT(*) INTO role_permission_count FROM role_permissions;
    SELECT COUNT(*) INTO user_role_count FROM user_roles;
    
    RAISE NOTICE 'Datos iniciales insertados:';
    RAISE NOTICE '  - Permisos: %', permission_count;
    RAISE NOTICE '  - Roles: %', role_count;
    RAISE NOTICE '  - Usuarios: %', user_count;
    RAISE NOTICE '  - Asignaciones rol-permiso: %', role_permission_count;
    RAISE NOTICE '  - Asignaciones usuario-rol: %', user_role_count;
END $$;