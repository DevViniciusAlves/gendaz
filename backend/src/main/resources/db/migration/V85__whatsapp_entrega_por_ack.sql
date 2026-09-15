-- Entrega WhatsApp por ACK (DELIVERY_ACK/READ/PLAYED do Baileys).
--
-- A cota CRM/lembretes so e convertida (reservado -> enviado) com prova de
-- entrega. O callback Node -> Spring pode chegar antes do worker persistir o
-- providerMessageId na notificacao (corrida) ou apos restart: por isso o
-- receipt e persistido primeiro (upsert idempotente por empresa + messageId)
-- e reconciliado quando a notificacao recebe o providerMessageId.
-- Nenhum contador historico e alterado por esta migration.

CREATE TABLE IF NOT EXISTS whatsapp_entrega_receipts (
    id BIGSERIAL PRIMARY KEY,
    empresa_id BIGINT NOT NULL REFERENCES empresas (id) ON DELETE CASCADE,
    provider_message_id VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DELIVERED',
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    data_criacao TIMESTAMP NOT NULL DEFAULT NOW(),
    data_atualizacao TIMESTAMP NULL,
    CONSTRAINT uk_whatsapp_receipt_empresa_msg UNIQUE (empresa_id, provider_message_id)
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_receipt_empresa_msg
    ON whatsapp_entrega_receipts (empresa_id, provider_message_id);

-- Lookup da notificacao pelo par tenant + providerMessageId na confirmacao.
CREATE INDEX IF NOT EXISTS idx_whatsapp_notif_empresa_provider_msg
    ON whatsapp_notificacoes (empresa_id, provider_message_id);
