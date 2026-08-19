ALTER TABLE clientes DROP CONSTRAINT IF EXISTS clientes_status_check;
ALTER TABLE clientes ADD CONSTRAINT clientes_status_check CHECK (status IN ('ATIVO', 'INATIVO', 'EXCLUIDO'));