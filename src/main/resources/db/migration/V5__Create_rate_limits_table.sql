-- Crear tabla para rate limiting
CREATE TABLE rate_limits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    identifier VARCHAR(255) NOT NULL, -- IP address o user_id
    endpoint VARCHAR(100) NOT NULL,
    attempts INTEGER DEFAULT 1,
    window_start TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    blocked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Constraint para asegurar combinación única de identifier + endpoint
ALTER TABLE rate_limits ADD CONSTRAINT unique_identifier_endpoint UNIQUE (identifier, endpoint);

-- Índices para optimizar consultas de rate limiting
CREATE INDEX idx_rate_limits_identifier ON rate_limits(identifier);
CREATE INDEX idx_rate_limits_endpoint ON rate_limits(endpoint);
CREATE INDEX idx_rate_limits_window_start ON rate_limits(window_start);
CREATE INDEX idx_rate_limits_blocked_until ON rate_limits(blocked_until);
CREATE INDEX idx_rate_limits_cleanup ON rate_limits(window_start, blocked_until);

-- Trigger para actualizar updated_at automáticamente
CREATE TRIGGER update_rate_limits_updated_at 
    BEFORE UPDATE ON rate_limits 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Función para limpiar registros antiguos de rate limiting
CREATE OR REPLACE FUNCTION cleanup_old_rate_limits()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM rate_limits 
    WHERE (blocked_until IS NULL AND window_start < CURRENT_TIMESTAMP - INTERVAL '1 hour')
       OR (blocked_until IS NOT NULL AND blocked_until < CURRENT_TIMESTAMP);
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    RETURN deleted_count;
END;
$$ language 'plpgsql';

-- Función para verificar si un identificador está bloqueado
CREATE OR REPLACE FUNCTION is_rate_limited(
    p_identifier VARCHAR(255),
    p_endpoint VARCHAR(100),
    p_max_attempts INTEGER DEFAULT 5,
    p_window_minutes INTEGER DEFAULT 15,
    p_block_minutes INTEGER DEFAULT 60
)
RETURNS BOOLEAN AS $$
DECLARE
    current_record RECORD;
    window_start_time TIMESTAMP WITH TIME ZONE;
BEGIN
    window_start_time := CURRENT_TIMESTAMP - (p_window_minutes || ' minutes')::INTERVAL;
    
    -- Buscar registro existente
    SELECT * INTO current_record 
    FROM rate_limits 
    WHERE identifier = p_identifier AND endpoint = p_endpoint;
    
    -- Si no existe registro, no está limitado
    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;
    
    -- Si está explícitamente bloqueado y el bloqueo no ha expirado
    IF current_record.blocked_until IS NOT NULL AND current_record.blocked_until > CURRENT_TIMESTAMP THEN
        RETURN TRUE;
    END IF;
    
    -- Si la ventana de tiempo ha expirado, no está limitado
    IF current_record.window_start < window_start_time THEN
        RETURN FALSE;
    END IF;
    
    -- Si ha excedido el número máximo de intentos en la ventana actual
    IF current_record.attempts >= p_max_attempts THEN
        RETURN TRUE;
    END IF;
    
    RETURN FALSE;
END;
$$ language 'plpgsql';

-- Función para registrar un intento
CREATE OR REPLACE FUNCTION record_rate_limit_attempt(
    p_identifier VARCHAR(255),
    p_endpoint VARCHAR(100),
    p_max_attempts INTEGER DEFAULT 5,
    p_window_minutes INTEGER DEFAULT 15,
    p_block_minutes INTEGER DEFAULT 60
)
RETURNS BOOLEAN AS $$
DECLARE
    current_record RECORD;
    window_start_time TIMESTAMP WITH TIME ZONE;
    should_block BOOLEAN := FALSE;
BEGIN
    window_start_time := CURRENT_TIMESTAMP - (p_window_minutes || ' minutes')::INTERVAL;
    
    -- Buscar registro existente
    SELECT * INTO current_record 
    FROM rate_limits 
    WHERE identifier = p_identifier AND endpoint = p_endpoint;
    
    IF FOUND THEN
        -- Si la ventana de tiempo ha expirado, reiniciar contador
        IF current_record.window_start < window_start_time THEN
            UPDATE rate_limits 
            SET attempts = 1,
                window_start = CURRENT_TIMESTAMP,
                blocked_until = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE identifier = p_identifier AND endpoint = p_endpoint;
        ELSE
            -- Incrementar contador de intentos
            UPDATE rate_limits 
            SET attempts = attempts + 1,
                updated_at = CURRENT_TIMESTAMP,
                blocked_until = CASE 
                    WHEN attempts + 1 >= p_max_attempts 
                    THEN CURRENT_TIMESTAMP + (p_block_minutes || ' minutes')::INTERVAL
                    ELSE blocked_until
                END
            WHERE identifier = p_identifier AND endpoint = p_endpoint;
            
            -- Verificar si debe ser bloqueado
            should_block := (current_record.attempts + 1) >= p_max_attempts;
        END IF;
    ELSE
        -- Crear nuevo registro
        INSERT INTO rate_limits (identifier, endpoint, attempts, window_start)
        VALUES (p_identifier, p_endpoint, 1, CURRENT_TIMESTAMP);
    END IF;
    
    RETURN should_block;
END;
$$ language 'plpgsql';

-- Comentarios para documentación
COMMENT ON TABLE rate_limits IS 'Tabla para implementar rate limiting por IP y usuario';
COMMENT ON COLUMN rate_limits.id IS 'Identificador único del registro';
COMMENT ON COLUMN rate_limits.identifier IS 'Identificador (IP address o user_id)';
COMMENT ON COLUMN rate_limits.endpoint IS 'Endpoint o recurso al que aplica el límite';
COMMENT ON COLUMN rate_limits.attempts IS 'Número de intentos en la ventana actual';
COMMENT ON COLUMN rate_limits.window_start IS 'Inicio de la ventana de tiempo actual';
COMMENT ON COLUMN rate_limits.blocked_until IS 'Fecha hasta la cual está bloqueado (NULL si no está bloqueado)';

COMMENT ON FUNCTION cleanup_old_rate_limits() IS 'Función para limpiar registros antiguos de rate limiting';
COMMENT ON FUNCTION is_rate_limited(VARCHAR, VARCHAR, INTEGER, INTEGER, INTEGER) IS 'Función para verificar si un identificador está rate limited';
COMMENT ON FUNCTION record_rate_limit_attempt(VARCHAR, VARCHAR, INTEGER, INTEGER, INTEGER) IS 'Función para registrar un intento y aplicar rate limiting';