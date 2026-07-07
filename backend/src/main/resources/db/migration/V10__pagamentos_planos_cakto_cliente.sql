ALTER TABLE pagamentos_planos
    ADD COLUMN IF NOT EXISTS customer_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS customer_email VARCHAR(120),
    ADD COLUMN IF NOT EXISTS customer_phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS customer_doc_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS customer_doc_number VARCHAR(20),
    ADD COLUMN IF NOT EXISTS antifraud_reference VARCHAR(120),
    ADD COLUMN IF NOT EXISTS cakto_offer_id VARCHAR(120);
