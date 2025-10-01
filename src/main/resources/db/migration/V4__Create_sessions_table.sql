-- Crear tabla de sesiones para gestión de JWT tokens
CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    access_token_hash VARCHAR(255) NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ip_address INET,
    user_agent TEXT,
    country_code VARCHAR(2),
    is_active BOOLEAN DEFAULT true
);

-- Índices para optimizar consultas de sesiones
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_access_token_hash ON sessions(access_token_hash);
CREATE INDEX idx_sessions_refresh_token_hash ON sessions(refresh_token_hash);
CREATE INDEX idx_sessions_expires_at ON sessions(expires_at);
CREATE INDEX idx_sessions_last_used_at ON sessions(last_used_at);
CREATE INDEX idx_sessions_ip_address ON sessions(ip_address);
CREATE INDEX idx_sessions_country_code ON sessions(country_code);
CREATE INDEX idx_sessions_active ON sessions(is_active);

-- Índice compuesto para consultas de limpieza de sesiones expiradas
CREATE INDEX idx_sessions_cleanup ON sessions(expires_at, is_active);

-- Constraint para asegurar que los tokens sean únicos
CREATE UNIQUE INDEX idx_sessions_unique_access_token ON sessions(access_token_hash) WHERE is_active = true;
CREATE UNIQUE INDEX idx_sessions_unique_refresh_token ON sessions(refresh_token_hash) WHERE is_active = true;

-- Trigger para actualizar last_used_at automáticamente
CREATE OR REPLACE FUNCTION update_session_last_used()
RETURNS TRIGGER AS $$
BEGIN
    -- Solo actualizar si han pasado más de 5 minutos desde la última actualización
    IF OLD.last_used_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes' THEN
        NEW.last_used_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_sessions_last_used 
    BEFORE UPDATE ON sessions 
    FOR EACH ROW 
    EXECUTE FUNCTION update_session_last_used();

-- Función para limpiar sesiones expiradas
CREATE OR REPLACE FUNCTION cleanup_expired_sessions()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM sessions 
    WHERE expires_at < CURRENT_TIMESTAMP 
       OR (last_used_at < CURRENT_TIMESTAMP - INTERVAL '30 days');
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    RETURN deleted_count;
END;
$$ language 'plpgsql';

-- Comentarios para documentación
COMMENT ON TABLE sessions IS 'Tabla de sesiones activas con tokens JWT';
COMMENT ON COLUMN sessions.id IS 'Identificador único de la sesión';
COMMENT ON COLUMN sessions.user_id IS 'ID del usuario propietario de la sesión';
COMMENT ON COLUMN sessions.access_token_hash IS 'Hash del token de acceso JWT';
COMMENT ON COLUMN sessions.refresh_token_hash IS 'Hash del token de refresco';
COMMENT ON COLUMN sessions.expires_at IS 'Fecha y hora de expiración de la sesión';
COMMENT ON COLUMN sessions.created_at IS 'Fecha y hora de creación de la sesión';
COMMENT ON COLUMN sessions.last_used_at IS 'Fecha y hora de último uso de la sesión';
COMMENT ON COLUMN sessions.ip_address IS 'Dirección IP desde donde se creó la sesión';
COMMENT ON COLUMN sessions.user_agent IS 'User agent del cliente';
COMMENT ON COLUMN sessions.country_code IS 'Código de país basado en la IP';
COMMENT ON COLUMN sessions.is_active IS 'Indica si la sesión está activa';

COMMENT ON FUNCTION cleanup_expired_sessions() IS 'Función para limpiar sesiones expiradas y antiguas';