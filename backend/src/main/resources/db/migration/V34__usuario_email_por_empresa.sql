ALTER TABLE usuarios
    DROP CONSTRAINT IF EXISTS usuarios_email_key;

DROP INDEX IF EXISTS usuarios_email_key;
DROP INDEX IF EXISTS uk_usuario_empresa_email;

CREATE UNIQUE INDEX IF NOT EXISTS uk_usuario_empresa_email
    ON usuarios (empresa_id, email);

