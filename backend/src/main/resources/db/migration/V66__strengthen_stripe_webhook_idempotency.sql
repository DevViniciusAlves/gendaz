ALTER TABLE stripe_webhook_events
    ADD COLUMN IF NOT EXISTS object_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS deduplication_key VARCHAR(380);

-- event_id continua sendo a primeira camada de idempotencia.
CREATE UNIQUE INDEX IF NOT EXISTS idx_stripe_webhook_events_event_id_unique
    ON stripe_webhook_events (event_id);

-- Usada apenas para tipos em que type + object_id representa a mesma operacao
-- de negocio. Eventos recorrentes, como subscription.updated, ficam sem chave.
CREATE UNIQUE INDEX IF NOT EXISTS idx_stripe_webhook_events_deduplication_key_unique
    ON stripe_webhook_events (deduplication_key)
    WHERE deduplication_key IS NOT NULL;
