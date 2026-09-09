ALTER TABLE whatsapp_notificacoes ADD COLUMN IF NOT EXISTS recipient VARCHAR(20) NULL;
ALTER TABLE whatsapp_notificacoes ADD COLUMN IF NOT EXISTS message_body VARCHAR(4096) NULL;
ALTER TABLE whatsapp_notificacoes ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP NULL;
ALTER TABLE whatsapp_notificacoes ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP NULL;
ALTER TABLE whatsapp_notificacoes ADD COLUMN IF NOT EXISTS send_started_at TIMESTAMP NULL;
ALTER TABLE whatsapp_notificacoes ADD COLUMN IF NOT EXISTS provider_message_id VARCHAR(120) NULL;
ALTER TABLE whatsapp_notificacoes ADD COLUMN IF NOT EXISTS quota_reserved BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE whatsapp_notificacoes ADD COLUMN IF NOT EXISTS quota_cycle_start DATE NULL;

CREATE INDEX IF NOT EXISTS idx_whatsapp_notif_fila
    ON whatsapp_notificacoes (status, next_attempt_at, scheduled_at);
