ALTER TABLE agendamentos ADD COLUMN IF NOT EXISTS excluido_agenda BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE agendamentos SET excluido_agenda = FALSE WHERE excluido_agenda IS NULL;
