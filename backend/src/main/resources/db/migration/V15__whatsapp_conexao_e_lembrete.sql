ALTER TABLE empresas
    ADD COLUMN IF NOT EXISTS whatsapp_connected BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS whatsapp_phone VARCHAR(30),
    ADD COLUMN IF NOT EXISTS whatsapp_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS whatsapp_secretaria_ia_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE agendamentos
    ADD COLUMN IF NOT EXISTS lembrete_wpp_enviado BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE empresas
   SET whatsapp_connected = COALESCE(whatsapp_connected, FALSE),
       whatsapp_notifications_enabled = COALESCE(whatsapp_notifications_enabled, TRUE),
       whatsapp_secretaria_ia_enabled = COALESCE(whatsapp_secretaria_ia_enabled, TRUE);

UPDATE agendamentos
   SET lembrete_wpp_enviado = COALESCE(lembrete_wpp_enviado, FALSE);
