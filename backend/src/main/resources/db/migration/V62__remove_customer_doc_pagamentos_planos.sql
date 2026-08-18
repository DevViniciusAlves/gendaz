-- Remocao LGPD: a gendaz nao coleta CPF/CNPJ/documento e nao persiste dados de documento
-- no checkout. Remove as colunas legadas de documento do cliente no pagamento do plano.
ALTER TABLE IF EXISTS pagamentos_planos DROP COLUMN IF EXISTS customer_doc_type;
ALTER TABLE IF EXISTS pagamentos_planos DROP COLUMN IF EXISTS customer_doc_number;