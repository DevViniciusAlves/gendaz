ALTER TABLE crm_contatos ADD COLUMN IF NOT EXISTS whatsapp_notificacao_id BIGINT NULL
    REFERENCES whatsapp_notificacoes (id) ON DELETE SET NULL;

ALTER TABLE crm_contatos ADD CONSTRAINT uk_crm_contatos_whatsapp_notif
    UNIQUE (whatsapp_notificacao_id);
