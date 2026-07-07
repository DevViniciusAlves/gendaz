ALTER TABLE pagamentos_planos
    ADD COLUMN IF NOT EXISTS cakto_ref_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS subscription_id VARCHAR(120);

CREATE INDEX IF NOT EXISTS idx_pagamentos_planos_cakto_ref_id
    ON pagamentos_planos(cakto_ref_id);

CREATE INDEX IF NOT EXISTS idx_pagamentos_planos_subscription_id
    ON pagamentos_planos(subscription_id);
