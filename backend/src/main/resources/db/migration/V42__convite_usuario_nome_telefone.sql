ALTER TABLE convites_empresa
    ADD COLUMN IF NOT EXISTS nome_convidado varchar(120);

ALTER TABLE convites_empresa
    ADD COLUMN IF NOT EXISTS telefone_convidado varchar(19);
