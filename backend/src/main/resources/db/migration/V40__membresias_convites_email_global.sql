DO $$
DECLARE
    duplicate_email_count integer;
    usuario_sem_empresa integer;
    empresa_sem_dono integer;
BEGIN
    SELECT COUNT(*) INTO duplicate_email_count
    FROM (
        SELECT lower(trim(email)) AS email_normalizado
        FROM usuarios
        GROUP BY lower(trim(email))
        HAVING COUNT(*) > 1
    ) dup;

    IF duplicate_email_count > 0 THEN
        RAISE EXCEPTION 'Migration aborted: existem emails duplicados em usuarios.';
    END IF;

    SELECT COUNT(*) INTO usuario_sem_empresa
    FROM usuarios
    WHERE empresa_id IS NULL AND perfil <> 'SUPER_ADMIN';

    IF usuario_sem_empresa > 0 THEN
        RAISE EXCEPTION 'Migration aborted: existem usuarios comuns sem empresa.';
    END IF;

    SELECT COUNT(*) INTO empresa_sem_dono
    FROM empresas e
    LEFT JOIN usuarios u ON u.empresa_id = e.id AND u.perfil = 'DONO' AND u.status = 'ATIVO'
    WHERE e.status <> 'INATIVA'
    GROUP BY e.id
    HAVING COUNT(u.id) = 0;

    IF empresa_sem_dono > 0 THEN
        RAISE EXCEPTION 'Migration aborted: existem empresas ativas sem dono.';
    END IF;
END $$;

ALTER TABLE usuarios
    DROP CONSTRAINT IF EXISTS usuarios_email_key;

DROP INDEX IF EXISTS uk_usuario_empresa_email;

ALTER TABLE usuarios
    ADD CONSTRAINT uk_usuario_email UNIQUE (email);

CREATE TABLE IF NOT EXISTS membresias (
    id bigserial PRIMARY KEY,
    usuario_id bigint NOT NULL UNIQUE REFERENCES usuarios(id),
    empresa_id bigint NOT NULL REFERENCES empresas(id),
    status varchar(20) NOT NULL,
    funcao varchar(20) NOT NULL,
    is_owner boolean NOT NULL DEFAULT false,
    data_entrada timestamp NOT NULL DEFAULT now(),
    data_remocao timestamp NULL,
    data_criacao timestamp NOT NULL DEFAULT now(),
    data_atualizacao timestamp NULL,
    alterado_por_usuario_id bigint NULL REFERENCES usuarios(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_membresia_empresa_usuario
    ON membresias (empresa_id, usuario_id);

CREATE TABLE IF NOT EXISTS convites_empresa (
    id bigserial PRIMARY KEY,
    empresa_id bigint NOT NULL REFERENCES empresas(id),
    email varchar(120) NOT NULL,
    criado_por_usuario_id bigint NOT NULL REFERENCES usuarios(id),
    status varchar(20) NOT NULL,
    data_criacao timestamp NOT NULL DEFAULT now(),
    data_expiracao timestamp NOT NULL,
    data_aceite timestamp NULL,
    token_hash varchar(128) NOT NULL,
    convite_referenciado_por bigint NULL,
    email_enviado_em timestamp NULL,
    cancelado_em timestamp NULL,
    expirado_em timestamp NULL,
    aceito_por_usuario_id bigint NULL,
    reenvios integer NOT NULL DEFAULT 0,
    data_atualizacao timestamp NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_convite_empresa_email_pendente
    ON convites_empresa (empresa_id, email)
    WHERE status = 'PENDING';

CREATE UNIQUE INDEX IF NOT EXISTS ux_convite_token_hash
    ON convites_empresa (token_hash);
