-- Adiciona coluna stripe_customer_id na tabela empresas
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(255);

-- Adiciona índice único para evitar duplicidade de clientes Stripe
CREATE UNIQUE INDEX IF NOT EXISTS idx_empresas_stripe_customer_id_unique ON empresas (stripe_customer_id) WHERE stripe_customer_id IS NOT NULL;