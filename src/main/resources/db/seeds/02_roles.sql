-- Insertar roles básicos del sistema
INSERT INTO roles (name, description) VALUES
('SUPER_ADMIN', 'Administrador con acceso completo al sistema'),
('ADMIN', 'Administrador con permisos de gestión de usuarios y roles'),
('USER_MANAGER', 'Gestor de usuarios con permisos limitados de administración'),
('USER', 'Usuario estándar con permisos básicos'),
('GUEST', 'Usuario invitado con permisos mínimos de solo lectura')
ON CONFLICT (name) DO NOTHING;