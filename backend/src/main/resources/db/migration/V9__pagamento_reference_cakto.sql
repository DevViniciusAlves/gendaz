ALTER TABLE pagamentos_planos
    ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(120);

UPDATE pagamentos_planos
SET payment_reference = external_reference
WHERE payment_reference IS NULL
  AND external_reference IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_pagamentos_planos_payment_reference
    ON pagamentos_planos(payment_reference)
    WHERE payment_reference IS NOT NULL;
