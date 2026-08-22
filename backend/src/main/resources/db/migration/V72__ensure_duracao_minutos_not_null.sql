UPDATE servicos SET duracao_minutos = 30 WHERE duracao_minutos IS NULL;
ALTER TABLE servicos ALTER COLUMN duracao_minutos SET NOT NULL;
