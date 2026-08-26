-- Corrige registros com NULL para evitar NullPointerException
UPDATE usuarios SET tentativas_login_falhadas = 0 WHERE tentativas_login_falhadas IS NULL;

-- Define DEFAULT 0 para novos registros
ALTER TABLE usuarios ALTER COLUMN tentativas_login_falhadas SET DEFAULT 0;

-- Torna a coluna NOT NULL para prevenir valores nulos futuros
ALTER TABLE usuarios ALTER COLUMN tentativas_login_falhadas SET NOT NULL;
