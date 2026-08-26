CREATE TABLE IF NOT EXISTS admin_impersonation_sessions (
    id BIGSERIAL PRIMARY KEY
);

ALTER TABLE admin_impersonation_sessions
    ADD COLUMN IF NOT EXISTS admin_usuario_id BIGINT,
    ADD COLUMN IF NOT EXISTS usuario_impersonado_id BIGINT,
    ADD COLUMN IF NOT EXISTS empresa_id BIGINT,
    ADD COLUMN IF NOT EXISTS session_token_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS ip_inicio VARCHAR(100),
    ADD COLUMN IF NOT EXISTS user_agent_inicio VARCHAR(500),
    ADD COLUMN IF NOT EXISTS criado_em TIMESTAMP,
    ADD COLUMN IF NOT EXISTS expira_em TIMESTAMP,
    ADD COLUMN IF NOT EXISTS encerrado_em TIMESTAMP,
    ADD COLUMN IF NOT EXISTS motivo_encerramento VARCHAR(100);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'admin_impersonation_sessions' AND column_name = 'admin_id'
    ) THEN
        EXECUTE 'UPDATE admin_impersonation_sessions SET admin_usuario_id = admin_id WHERE admin_usuario_id IS NULL';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'admin_impersonation_sessions' AND column_name = 'ip'
    ) THEN
        EXECUTE 'UPDATE admin_impersonation_sessions SET ip_inicio = ip WHERE ip_inicio IS NULL';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'admin_impersonation_sessions' AND column_name = 'user_agent'
    ) THEN
        EXECUTE 'UPDATE admin_impersonation_sessions SET user_agent_inicio = LEFT(user_agent, 500) WHERE user_agent_inicio IS NULL';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'admin_impersonation_sessions' AND column_name = 'data_inicio'
    ) THEN
        EXECUTE 'UPDATE admin_impersonation_sessions SET criado_em = data_inicio WHERE criado_em IS NULL';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'admin_impersonation_sessions' AND column_name = 'data_fim'
    ) THEN
        EXECUTE 'UPDATE admin_impersonation_sessions SET encerrado_em = data_fim WHERE encerrado_em IS NULL AND data_fim IS NOT NULL';
    END IF;
END $$;

UPDATE admin_impersonation_sessions
SET admin_usuario_id = COALESCE(admin_usuario_id, 0),
    usuario_impersonado_id = COALESCE(usuario_impersonado_id, 0),
    empresa_id = COALESCE(empresa_id, 0),
    session_token_hash = COALESCE(session_token_hash, 'legacy-' || id || '-' || md5(random()::text || clock_timestamp()::text)),
    status = COALESCE(status, 'ENCERRADA'),
    criado_em = COALESCE(criado_em, NOW()),
    expira_em = COALESCE(expira_em, encerrado_em, criado_em, NOW()),
    motivo_encerramento = COALESCE(motivo_encerramento, 'LEGACY')
WHERE admin_usuario_id IS NULL
   OR usuario_impersonado_id IS NULL
   OR empresa_id IS NULL
   OR session_token_hash IS NULL
   OR status IS NULL
   OR criado_em IS NULL
   OR expira_em IS NULL
   OR motivo_encerramento IS NULL;

ALTER TABLE admin_impersonation_sessions
    ALTER COLUMN admin_usuario_id SET NOT NULL,
    ALTER COLUMN usuario_impersonado_id SET NOT NULL,
    ALTER COLUMN empresa_id SET NOT NULL,
    ALTER COLUMN session_token_hash SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN criado_em SET NOT NULL,
    ALTER COLUMN expira_em SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_admin_impersonation_token_hash
    ON admin_impersonation_sessions(session_token_hash);

CREATE INDEX IF NOT EXISTS idx_admin_impersonation_token_hash
    ON admin_impersonation_sessions(session_token_hash);

CREATE INDEX IF NOT EXISTS idx_admin_impersonation_admin_status
    ON admin_impersonation_sessions(admin_usuario_id, status);

CREATE INDEX IF NOT EXISTS idx_admin_impersonation_usuario_empresa
    ON admin_impersonation_sessions(usuario_impersonado_id, empresa_id);
