-- V87: Persistent outbox para delivery events (DELIVERY_ACK, READ, PLAYED)
-- Tabela duravelmente salva em PostgreSQL, processada por worker Node
-- para notificar Spring sobre entregas comprovadas.

CREATE TABLE IF NOT EXISTS whatsapp_delivery_outbox (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    provider_message_id VARCHAR(120) NOT NULL,
    state VARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSING, DONE, DEAD
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP,
    next_attempt_at TIMESTAMP DEFAULT NOW(),
    locked_until TIMESTAMP,
    http_status INTEGER,
    http_error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_company_id FOREIGN KEY (company_id) REFERENCES empresas(id) ON DELETE CASCADE,
    CONSTRAINT unique_provider_message_id UNIQUE(company_id, provider_message_id)
);

CREATE INDEX idx_outbox_state_next_attempt ON whatsapp_delivery_outbox(state, next_attempt_at)
    WHERE state IN ('PENDING', 'PROCESSING');
    
CREATE INDEX idx_outbox_company_id ON whatsapp_delivery_outbox(company_id);

