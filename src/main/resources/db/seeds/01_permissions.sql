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
('AUTH_REGISTER', 'auth', 'register', 'Registrar nuevos usuarios')
ON CONFLICT (name) DO NOTHING;