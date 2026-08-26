-- Adiciona colunas stripe_invoice_id e stripe_event_id na tabela pagamentos_planos
ALTER TABLE pagamentos_planos ADD COLUMN IF NOT EXISTS stripe_invoice_id VARCHAR(255);
ALTER TABLE pagamentos_planos ADD COLUMN IF NOT EXISTS stripe_event_id VARCHAR(255);

-- Adiciona índice único para stripe_event_id (idempotência)
CREATE UNIQUE INDEX IF NOT EXISTS idx_pagamentos_planos_stripe_event_id_unique ON pagamentos_planos (stripe_event_id) WHERE stripe_event_id IS NOT NULL;

-- Adiciona índice único para stripe_invoice_id (idempotência)
CREATE UNIQUE INDEX IF NOT EXISTS idx_pagamentos_planos_stripe_invoice_id_unique ON pagamentos_planos (stripe_invoice_id) WHERE stripe_invoice_id IS NOT NULL;