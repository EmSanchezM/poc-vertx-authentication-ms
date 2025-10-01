-- Crear tabla de relación usuario-rol (Many-to-Many)
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID REFERENCES users(id),
    PRIMARY KEY (user_id, role_id)
);

-- Crear tabla de relación rol-permiso (Many-to-Many)
CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id)
);

-- Índices para optimizar consultas de autorización
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX idx_user_roles_assigned_at ON user_roles(assigned_at);

CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- Vista para obtener todos los permisos de un usuario de forma eficiente
CREATE VIEW user_permissions AS
SELECT DISTINCT 
    ur.user_id,
    p.id as permission_id,
    p.name as permission_name,
    p.resource,
    p.action,
    p.description,
    r.name as role_name
FROM user_roles ur
JOIN roles r ON ur.role_id = r.id
JOIN role_permissions rp ON r.id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id
JOIN users u ON ur.user_id = u.id
WHERE u.is_active = true;

-- Comentarios para documentación
COMMENT ON TABLE user_roles IS 'Relación Many-to-Many entre usuarios y roles';
COMMENT ON COLUMN user_roles.user_id IS 'ID del usuario';
COMMENT ON COLUMN user_roles.role_id IS 'ID del rol asignado';
COMMENT ON COLUMN user_roles.assigned_at IS 'Fecha y hora de asignación del rol';
COMMENT ON COLUMN user_roles.assigned_by IS 'ID del usuario que asignó el rol';

COMMENT ON TABLE role_permissions IS 'Relación Many-to-Many entre roles y permisos';
COMMENT ON COLUMN role_permissions.role_id IS 'ID del rol';
COMMENT ON COLUMN role_permissions.permission_id IS 'ID del permiso asignado al rol';

COMMENT ON VIEW user_permissions IS 'Vista que muestra todos los permisos efectivos de cada usuario activo';