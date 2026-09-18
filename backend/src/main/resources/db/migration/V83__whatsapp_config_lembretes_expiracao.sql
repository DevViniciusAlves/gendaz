CREATE TABLE IF NOT EXISTS whatsapp_configuracoes (
    id BIGSERIAL PRIMARY KEY,
    empresa_id BIGINT NOT NULL REFERENCES empresas (id) ON DELETE CASCADE,
    lembretes_ativos BOOLEAN NOT NULL DEFAULT FALSE,
    data_criacao TIMESTAMP NOT NULL DEFAULT NOW(),
    data_atualizacao TIMESTAMP NULL,
    CONSTRAINT uk_whatsapp_config_empresa UNIQUE (empresa_id)
);

ALTER TABLE whatsapp_notificacoes ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP NULL;
