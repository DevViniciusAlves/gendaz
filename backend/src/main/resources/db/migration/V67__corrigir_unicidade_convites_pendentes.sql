ALTER TABLE convites_empresa
    DROP CONSTRAINT IF EXISTS uk_convite_empresa_email_ativo;

DROP INDEX IF EXISTS uk_convite_empresa_email_ativo;

CREATE UNIQUE INDEX IF NOT EXISTS ux_convite_empresa_email_pendente
    ON convites_empresa (empresa_id, email)
    WHERE status = 'PENDING';

CREATE UNIQUE INDEX IF NOT EXISTS ux_convite_token_hash
    ON convites_empresa (token_hash);
