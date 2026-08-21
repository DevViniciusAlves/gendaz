ALTER TABLE empresas
    ADD COLUMN IF NOT EXISTS caixa_total NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS despesas_total NUMERIC(12, 2) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS caixa_despesas_log (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES empresas (id),
    tipo VARCHAR(40) NOT NULL,
    valor NUMERIC(12, 2) NOT NULL DEFAULT 0,
    descricao TEXT NULL,
    obs TEXT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    usuario_id BIGINT NULL REFERENCES usuarios (id),
    agendamento_id BIGINT NULL REFERENCES agendamentos (id)
);

CREATE INDEX IF NOT EXISTS idx_caixa_despesas_log_empresa_data
    ON caixa_despesas_log (business_id, criado_em DESC);
