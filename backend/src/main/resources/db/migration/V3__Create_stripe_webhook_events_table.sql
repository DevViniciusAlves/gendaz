-- Cria tabela para idempotência de eventos Stripe
CREATE TABLE IF NOT EXISTS stripe_webhook_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(120) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Adiciona índice único para event_id (idempotência)
CREATE UNIQUE INDEX IF NOT EXISTS idx_stripe_webhook_events_event_id_unique ON stripe_webhook_events (event_id);