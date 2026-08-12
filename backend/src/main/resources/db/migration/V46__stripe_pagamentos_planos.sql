ALTER TABLE pagamentos_planos
    ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS stripe_session_id VARCHAR(120);

ALTER TABLE pagamentos_planos
    ADD COLUMN IF NOT EXISTS subscription_id VARCHAR(120);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'pagamentos_planos'
          AND column_name = 'cakto_subscription_id'
    ) THEN
        UPDATE pagamentos_planos
        SET subscription_id = cakto_subscription_id
        WHERE subscription_id IS NULL
          AND cakto_subscription_id IS NOT NULL;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_pagamentos_planos_stripe_session_id
    ON pagamentos_planos(stripe_session_id)
    WHERE stripe_session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pagamentos_planos_subscription_id
    ON pagamentos_planos(subscription_id);

ALTER TABLE pagamentos_planos
    DROP COLUMN IF EXISTS cakto_ref_id,
    DROP COLUMN IF EXISTS cakto_offer_id,
    DROP COLUMN IF EXISTS pix_copia_cola,
    DROP COLUMN IF EXISTS pix_qr_code_base64,
    DROP COLUMN IF EXISTS cakto_subscription_id;
