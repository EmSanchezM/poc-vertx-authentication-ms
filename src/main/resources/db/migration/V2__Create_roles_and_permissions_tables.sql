-- Crear tabla de roles
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Crear tabla de permisos
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) UNIQUE NOT NULL,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Índices para optimizar consultas
CREATE INDEX idx_roles_name ON roles(name);
CREATE INDEX idx_permissions_name ON permissions(name);
CREATE INDEX idx_permissions_resource ON permissions(resource);
CREATE INDEX idx_permissions_action ON permissions(action);
CREATE INDEX idx_permissions_resource_action ON permissions(resource, action);

-- Constraint para asegurar combinación única de resource + action
ALTER TABLE permissions ADD CONSTRAINT unique_resource_action UNIQUE (resource, action);

-- Comentarios para documentación
COMMENT ON TABLE roles IS 'Tabla de roles del sistema RBAC';
COMMENT ON COLUMN roles.id IS 'Identificador único del rol';
COMMENT ON COLUMN roles.name IS 'Nombre único del rol';
COMMENT ON COLUMN roles.description IS 'Descripción del rol y sus responsabilidades';

COMMENT ON TABLE permissions IS 'Tabla de permisos granulares del sistema';
COMMENT ON COLUMN permissions.id IS 'Identificador único del permiso';
COMMENT ON COLUMN permissions.name IS 'Nombre único del permiso';
COMMENT ON COLUMN permissions.resource IS 'Recurso sobre el que aplica el permiso';
COMMENT ON COLUMN permissions.action IS 'Acción permitida sobre el recurso';
COMMENT ON COLUMN permissions.description IS 'Descripción detallada del permiso';