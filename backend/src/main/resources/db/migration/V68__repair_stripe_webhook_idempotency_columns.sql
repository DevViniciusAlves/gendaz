-- Repara ambientes em que a V66 consta como aplicada, mas as colunas de
-- idempotencia do webhook Stripe nao existem no schema efetivo.
ALTER TABLE stripe_webhook_events
    ADD COLUMN IF NOT EXISTS object_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS deduplication_key VARCHAR(380);

-- Mantem a protecao contra o reprocessamento do mesmo evento Stripe.
CREATE UNIQUE INDEX IF NOT EXISTS idx_stripe_webhook_events_event_id_unique
    ON stripe_webhook_events (event_id);

-- Impede duplicidade da mesma operacao de negocio quando a chave e informada.
CREATE UNIQUE INDEX IF NOT EXISTS idx_stripe_webhook_events_deduplication_key_unique
    ON stripe_webhook_events (deduplication_key)
    WHERE deduplication_key IS NOT NULL;
