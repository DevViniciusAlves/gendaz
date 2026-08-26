ALTER TABLE agendamentos
ADD COLUMN IF NOT EXISTS protocolo VARCHAR(6);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agendamentos_protocolo
ON agendamentos (protocolo)
WHERE protocolo IS NOT NULL;
