CREATE TABLE IF NOT EXISTS admin_impersonation_sessions (
    id BIGSERIAL PRIMARY KEY,
    admin_usuario_id BIGINT NOT NULL,
    usuario_impersonado_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    session_token_hash VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    ip_inicio VARCHAR(100),
    user_agent_inicio VARCHAR(500),
    criado_em TIMESTAMP NOT NULL,
    expira_em TIMESTAMP NOT NULL,
    encerrado_em TIMESTAMP,
    motivo_encerramento VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_admin_impersonation_token_hash
    ON admin_impersonation_sessions(session_token_hash);

CREATE INDEX IF NOT EXISTS idx_admin_impersonation_admin_status
    ON admin_impersonation_sessions(admin_usuario_id, status);

CREATE INDEX IF NOT EXISTS idx_admin_impersonation_usuario_empresa
    ON admin_impersonation_sessions(usuario_impersonado_id, empresa_id);
