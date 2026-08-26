-- Cria tabela para histórico de cobranças mensais
CREATE TABLE IF NOT EXISTS pagamentos_planos_cobrancas (
    id BIGSERIAL PRIMARY KEY,
    pagamento_plano_id BIGINT NOT NULL,
    subscription_id VARCHAR(120) NOT NULL,
    stripe_invoice_id VARCHAR(120) NOT NULL,
    status VARCHAR(50) NOT NULL,
    valor DECIMAL(10, 2) NOT NULL,
    periodo_inicio DATE NOT NULL,
    periodo_fim DATE NOT NULL,
    data_pagamento TIMESTAMP WITHOUT TIME ZONE,
    data_criacao TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pagamentos_planos_cobrancas_pagamento_plano_id
        FOREIGN KEY (pagamento_plano_id)
        REFERENCES pagamentos_planos(id)
        ON DELETE CASCADE
);

-- Adiciona índice único para stripe_invoice_id (idempotência)
CREATE UNIQUE INDEX IF NOT EXISTS idx_pagamentos_planos_cobrancas_stripe_invoice_id_unique ON pagamentos_planos_cobrancas (stripe_invoice_id);

-- Adiciona índice para pagamento_plano_id (consultas por assinatura)
CREATE INDEX IF NOT EXISTS idx_pagamentos_planos_cobrancas_pagamento_plano_id ON pagamentos_planos_cobrancas (pagamento_plano_id);

-- Adiciona índice para subscription_id (consultas por subscription)
CREATE INDEX IF NOT EXISTS idx_pagamentos_planos_cobrancas_subscription_id ON pagamentos_planos_cobrancas (subscription_id);