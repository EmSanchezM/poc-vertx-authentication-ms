-- Asignar todos los permisos al SUPER_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

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
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

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
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Asignar permisos al rol USER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'USER'
AND p.name IN (
    'AUTH_LOGIN', 'AUTH_LOGOUT', 'AUTH_REFRESH',
    'PROFILE_READ', 'PROFILE_UPDATE'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Asignar permisos al rol GUEST
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'GUEST'
AND p.name IN (
    'AUTH_LOGIN', 'AUTH_REGISTER'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;