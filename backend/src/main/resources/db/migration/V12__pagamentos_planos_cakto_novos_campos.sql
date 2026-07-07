ALTER TABLE pagamentos_planos
    ADD COLUMN IF NOT EXISTS pix_copia_cola VARCHAR(600),
    ADD COLUMN IF NOT EXISTS cakto_subscription_id VARCHAR(120);

UPDATE pagamentos_planos
SET pix_copia_cola = pix_copia_ecola
WHERE pix_copia_cola IS NULL
  AND pix_copia_ecola IS NOT NULL;

UPDATE pagamentos_planos
SET cakto_subscription_id = subscription_id
WHERE cakto_subscription_id IS NULL
  AND subscription_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pagamentos_planos_cakto_subscription_id
    ON pagamentos_planos(cakto_subscription_id);

CREATE INDEX IF NOT EXISTS idx_pagamentos_planos_pix_copia_cola
    ON pagamentos_planos(pix_copia_cola);
