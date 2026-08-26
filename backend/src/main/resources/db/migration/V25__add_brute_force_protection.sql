ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS tentativas_login_falhadas INTEGER DEFAULT 0;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS bloqueado_ate TIMESTAMP;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS ultimo_login_falhado TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_usuarios_bloqueado_ate ON usuarios(bloqueado_ate);
