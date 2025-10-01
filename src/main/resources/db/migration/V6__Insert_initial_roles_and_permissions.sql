-- Insertar permisos básicos del sistema
INSERT INTO permissions (name, resource, action, description) VALUES
-- Permisos de usuario
('user.read', 'user', 'read', 'Leer información de usuarios'),
('user.create', 'user', 'create', 'Crear nuevos usuarios'),
('user.update', 'user', 'update', 'Actualizar información de usuarios'),
('user.delete', 'user', 'delete', 'Eliminar usuarios'),
('user.list', 'user', 'list', 'Listar usuarios del sistema'),

-- Permisos de perfil propio
('profile.read', 'profile', 'read', 'Leer el propio perfil'),
('profile.update', 'profile', 'update', 'Actualizar el propio perfil'),

-- Permisos de roles
('role.read', 'role', 'read', 'Leer información de roles'),
('role.create', 'role', 'create', 'Crear nuevos roles'),
('role.update', 'role', 'update', 'Actualizar roles existentes'),
('role.delete', 'role', 'delete', 'Eliminar roles'),
('role.assign', 'role', 'assign', 'Asignar roles a usuarios'),

-- Permisos de permisos
('permission.read', 'permission', 'read', 'Leer información de permisos'),
('permission.create', 'permission', 'create', 'Crear nuevos permisos'),
('permission.update', 'permission', 'update', 'Actualizar permisos existentes'),
('permission.delete', 'permission', 'delete', 'Eliminar permisos'),

-- Permisos de sesiones
('session.read', 'session', 'read', 'Leer información de sesiones'),
('session.manage', 'session', 'manage', 'Gestionar sesiones de usuarios'),
('session.invalidate', 'session', 'invalidate', 'Invalidar sesiones'),

-- Permisos de administración
('admin.dashboard', 'admin', 'dashboard', 'Acceder al dashboard administrativo'),
('admin.logs', 'admin', 'logs', 'Acceder a logs del sistema'),
('admin.metrics', 'admin', 'metrics', 'Acceder a métricas del sistema'),
('admin.health', 'admin', 'health', 'Acceder a health checks del sistema'),

-- Permisos de autenticación
('auth.login', 'auth', 'login', 'Iniciar sesión en el sistema'),
('auth.logout', 'auth', 'logout', 'Cerrar sesión'),
('auth.refresh', 'auth', 'refresh', 'Refrescar tokens de autenticación'),
('auth.register', 'auth', 'register', 'Registrar nuevos usuarios');

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
    'user.read', 'user.create', 'user.update', 'user.list',
    'role.read', 'role.create', 'role.update', 'role.assign',
    'permission.read',
    'session.read', 'session.manage', 'session.invalidate',
    'admin.dashboard', 'admin.logs', 'admin.metrics', 'admin.health',
    'auth.login', 'auth.logout', 'auth.refresh',
    'profile.read', 'profile.update'
);

-- Asignar permisos al rol USER_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'USER_MANAGER'
AND p.name IN (
    'user.read', 'user.create', 'user.update', 'user.list',
    'role.read',
    'session.read',
    'auth.login', 'auth.logout', 'auth.refresh',
    'profile.read', 'profile.update'
);

-- Asignar permisos al rol USER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'USER'
AND p.name IN (
    'auth.login', 'auth.logout', 'auth.refresh',
    'profile.read', 'profile.update'
);

-- Asignar permisos al rol GUEST
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'GUEST'
AND p.name IN (
    'auth.login', 'auth.register'
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