CREATE TABLE IF NOT EXISTS logs_atividade (
    id BIGSERIAL PRIMARY KEY,
    empresa_id BIGINT NOT NULL REFERENCES empresas (id),
    usuario_id BIGINT NULL REFERENCES usuarios (id),
    nome_usuario VARCHAR(120) NOT NULL,
    entidade VARCHAR(40) NOT NULL,
    entidade_id BIGINT NULL,
    acao VARCHAR(500) NOT NULL,
    detalhes VARCHAR(1000) NULL,
    ip VARCHAR(120) NULL,
    data_hora TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_logs_atividade_empresa_data
    ON logs_atividade (empresa_id, data_hora DESC);

CREATE INDEX IF NOT EXISTS idx_logs_atividade_entidade
    ON logs_atividade (entidade);
