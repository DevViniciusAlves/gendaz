-- Remocao LGPD: a gendaz nao coleta CPF/CNPJ/documento fiscal.
-- A coluna 'documento' em empresas e legada e nao deve mais existir em runtime.
-- DROP COLUMN remove automaticamente constraints/indexes associados.
ALTER TABLE IF EXISTS empresas DROP COLUMN IF EXISTS documento;