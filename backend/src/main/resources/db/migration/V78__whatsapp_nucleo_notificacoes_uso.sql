CREATE TABLE IF NOT EXISTS whatsapp_notificacoes (
    id BIGSERIAL PRIMARY KEY,
    empresa_id BIGINT NOT NULL REFERENCES empresas (id) ON DELETE CASCADE,
    tipo VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    data_criacao TIMESTAMP NOT NULL DEFAULT NOW(),
    data_atualizacao TIMESTAMP NULL,
    scheduled_at TIMESTAMP NULL,
    sent_at TIMESTAMP NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    cliente_id BIGINT NULL REFERENCES clientes (id) ON DELETE SET NULL,
    agendamento_id BIGINT NULL REFERENCES agendamentos (id) ON DELETE SET NULL,
    CONSTRAINT uk_whatsapp_notif_empresa_chave UNIQUE (empresa_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_notif_empresa_status
    ON whatsapp_notificacoes (empresa_id, status);

CREATE INDEX IF NOT EXISTS idx_whatsapp_notif_empresa_scheduled
    ON whatsapp_notificacoes (empresa_id, scheduled_at);

CREATE TABLE IF NOT EXISTS whatsapp_uso_ciclos (
    id BIGSERIAL PRIMARY KEY,
    empresa_id BIGINT NOT NULL REFERENCES empresas (id) ON DELETE CASCADE,
    ciclo_inicio DATE NOT NULL,
    lembretes_reservados INTEGER NOT NULL DEFAULT 0,
    lembretes_enviados INTEGER NOT NULL DEFAULT 0,
    crm_reservados INTEGER NOT NULL DEFAULT 0,
    crm_enviados INTEGER NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP NOT NULL DEFAULT NOW(),
    data_atualizacao TIMESTAMP NULL,
    CONSTRAINT uk_whatsapp_uso_empresa_ciclo UNIQUE (empresa_id, ciclo_inicio)
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_uso_empresa_ciclo
    ON whatsapp_uso_ciclos (empresa_id, ciclo_inicio);
