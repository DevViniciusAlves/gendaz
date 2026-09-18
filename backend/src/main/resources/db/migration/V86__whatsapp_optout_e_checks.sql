ALTER TABLE clientes ADD COLUMN IF NOT EXISTS receber_whatsapp BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE whatsapp_uso_ciclos ADD CONSTRAINT chk_whatsapp_uso_lemb_res
    CHECK (lembretes_reservados >= 0);
ALTER TABLE whatsapp_uso_ciclos ADD CONSTRAINT chk_whatsapp_uso_lemb_env
    CHECK (lembretes_enviados >= 0);
ALTER TABLE whatsapp_uso_ciclos ADD CONSTRAINT chk_whatsapp_uso_crm_res
    CHECK (crm_reservados >= 0);
ALTER TABLE whatsapp_uso_ciclos ADD CONSTRAINT chk_whatsapp_uso_crm_env
    CHECK (crm_enviados >= 0);
