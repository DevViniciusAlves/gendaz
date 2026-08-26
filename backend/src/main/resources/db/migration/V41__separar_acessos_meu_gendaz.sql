CREATE TABLE IF NOT EXISTS meu_gendaz_acessos (
    id bigserial PRIMARY KEY,
    empresa_id bigint NOT NULL REFERENCES empresas(id),
    email varchar(120) NOT NULL,
    nome varchar(120) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ATIVO',
    sessao_ativa varchar(80),
    usuario_legado_id bigint,
    data_criacao timestamp NOT NULL DEFAULT now(),
    data_atualizacao timestamp NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_meu_gendaz_acesso_empresa_email
    ON meu_gendaz_acessos (empresa_id, lower(trim(email)));

CREATE INDEX IF NOT EXISTS idx_meu_gendaz_acesso_empresa
    ON meu_gendaz_acessos (empresa_id);

CREATE INDEX IF NOT EXISTS idx_meu_gendaz_acesso_sessao
    ON meu_gendaz_acessos (sessao_ativa);

ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS sessao_ativa_meu_gendaz varchar(80);

ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS sessao_ativa varchar(80);

UPDATE meu_gendaz_acessos mga
SET
    nome = COALESCE(NULLIF(src.nome, ''), mga.nome),
    status = src.status,
    sessao_ativa = COALESCE(src.sessao_ativa, mga.sessao_ativa),
    usuario_legado_id = COALESCE(mga.usuario_legado_id, src.usuario_legado_id),
    data_atualizacao = now()
FROM (
    SELECT DISTINCT ON (u.empresa_id, lower(trim(u.email)))
        u.empresa_id,
        lower(trim(u.email)) AS email,
        COALESCE(NULLIF(trim(u.nome), ''), split_part(lower(trim(u.email)), '@', 1), 'Cliente') AS nome,
        u.status,
        u.sessao_ativa_meu_gendaz AS sessao_ativa,
        u.id AS usuario_legado_id
    FROM usuarios u
    WHERE u.empresa_id IS NOT NULL
      AND u.email IS NOT NULL
      AND (
            u.sessao_ativa_meu_gendaz IS NOT NULL
            OR (
                u.perfil = 'ATENDENTE'
                AND NOT EXISTS (
                    SELECT 1
                    FROM membresias m
                    WHERE m.usuario_id = u.id
                )
            )
      )
    ORDER BY u.empresa_id, lower(trim(u.email)), u.id
) src
WHERE mga.empresa_id = src.empresa_id
  AND lower(trim(mga.email)) = src.email;

INSERT INTO meu_gendaz_acessos (
    empresa_id,
    email,
    nome,
    status,
    sessao_ativa,
    usuario_legado_id,
    data_criacao,
    data_atualizacao
)
SELECT
    src.empresa_id,
    src.email,
    src.nome,
    src.status,
    src.sessao_ativa,
    src.usuario_legado_id,
    src.data_criacao,
    src.data_atualizacao
FROM (
    SELECT DISTINCT ON (u.empresa_id, lower(trim(u.email)))
        u.empresa_id,
        lower(trim(u.email)) AS email,
        COALESCE(NULLIF(trim(u.nome), ''), split_part(lower(trim(u.email)), '@', 1), 'Cliente') AS nome,
        u.status,
        u.sessao_ativa_meu_gendaz AS sessao_ativa,
        u.id AS usuario_legado_id,
        COALESCE(u.data_criacao, now()) AS data_criacao,
        u.data_atualizacao
    FROM usuarios u
    WHERE u.empresa_id IS NOT NULL
      AND u.email IS NOT NULL
      AND (
            u.sessao_ativa_meu_gendaz IS NOT NULL
            OR (
                u.perfil = 'ATENDENTE'
                AND NOT EXISTS (
                    SELECT 1
                    FROM membresias m
                    WHERE m.usuario_id = u.id
                )
            )
      )
    ORDER BY u.empresa_id, lower(trim(u.email)), u.id
) src
WHERE NOT EXISTS (
    SELECT 1
    FROM meu_gendaz_acessos mga
    WHERE mga.empresa_id = src.empresa_id
      AND lower(trim(mga.email)) = src.email
);

ALTER TABLE chamados
    ADD COLUMN IF NOT EXISTS meu_gendaz_acesso_id bigint NULL REFERENCES meu_gendaz_acessos(id);

ALTER TABLE chamados
    ALTER COLUMN usuario_id DROP NOT NULL;

UPDATE chamados c
SET meu_gendaz_acesso_id = mga.id
FROM meu_gendaz_acessos mga
WHERE c.origem = 'MEU_GENDAZ'
  AND c.usuario_id = mga.usuario_legado_id
  AND c.meu_gendaz_acesso_id IS NULL;

UPDATE chamados
SET usuario_id = NULL
WHERE origem = 'MEU_GENDAZ'
  AND meu_gendaz_acesso_id IS NOT NULL;

UPDATE usuarios u
SET
    email = left('legacy-meu-gendaz-' || u.id || '-' || lower(trim(u.email)), 120),
    status = 'REMOVIDO',
    sessao_ativa = NULL,
    sessao_ativa_meu_gendaz = NULL,
    data_atualizacao = now()
WHERE u.perfil = 'ATENDENTE'
  AND u.empresa_id IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM meu_gendaz_acessos mga
      WHERE mga.usuario_legado_id = u.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM membresias m
      WHERE m.usuario_id = u.id OR m.alterado_por_usuario_id = u.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM convites_empresa c
      WHERE c.criado_por_usuario_id = u.id OR c.aceito_por_usuario_id = u.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM chamados c
      WHERE c.usuario_id = u.id
  );

DO $$
DECLARE
    duplicate_email_count integer;
BEGIN
    SELECT COUNT(*) INTO duplicate_email_count
    FROM (
        SELECT lower(trim(email)) AS email_normalizado
        FROM usuarios
        GROUP BY lower(trim(email))
        HAVING COUNT(*) > 1
    ) dup;

    IF duplicate_email_count > 0 THEN
        RAISE EXCEPTION 'Migration aborted: ainda existem emails duplicados em usuarios apos separar Meu Gendaz.';
    END IF;
END $$;

ALTER TABLE usuarios
    DROP CONSTRAINT IF EXISTS uk_usuario_email;

DROP INDEX IF EXISTS uk_usuario_email;

ALTER TABLE usuarios
    ADD CONSTRAINT uk_usuario_email UNIQUE (email);
