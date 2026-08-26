CREATE TABLE IF NOT EXISTS formas_pagamento_empresa (
    id BIGSERIAL PRIMARY KEY,
    empresa_id BIGINT NOT NULL UNIQUE REFERENCES empresas(id),
    pix_ativo BOOLEAN NOT NULL DEFAULT TRUE,
    debito_ativo BOOLEAN NOT NULL DEFAULT TRUE,
    credito_ativo BOOLEAN NOT NULL DEFAULT TRUE,
    parcelado_ativo BOOLEAN NOT NULL DEFAULT FALSE,
    dinheiro_ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP NOT NULL DEFAULT NOW(),
    data_atualizacao TIMESTAMP NULL
);

ALTER TABLE pagamentos
    ADD COLUMN IF NOT EXISTS parcelas INTEGER NULL;

ALTER TABLE pagamentos
    ALTER COLUMN metodo_pagamento DROP NOT NULL;
