-- V86__whatsapp_auth_persistence.sql
-- Tabelas para persistência durável do estado de autenticação do WhatsApp (Baileys)
-- creds criptografados + Signal Keys indexadas por HMAC

CREATE TABLE IF NOT EXISTS whatsapp_auth_sessions (
    company_id VARCHAR(64) PRIMARY KEY,
    payload TEXT NOT NULL,
    registered BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS whatsapp_auth_keys (
    company_id VARCHAR(64) NOT NULL,
    key_type VARCHAR(64) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (company_id, key_type, key_hash)
);

-- Índice para listagem eficiente de sessões registradas
CREATE INDEX IF NOT EXISTS idx_whatsapp_auth_sessions_registered
    ON whatsapp_auth_sessions (registered)
    WHERE registered = TRUE;

-- Trigger para atualizar updated_at automaticamente
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_whatsapp_auth_sessions_updated_at ON whatsapp_auth_sessions;
CREATE TRIGGER update_whatsapp_auth_sessions_updated_at
    BEFORE UPDATE ON whatsapp_auth_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_whatsapp_auth_keys_updated_at ON whatsapp_auth_keys;
CREATE TRIGGER update_whatsapp_auth_keys_updated_at
    BEFORE UPDATE ON whatsapp_auth_keys
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();