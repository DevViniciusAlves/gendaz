CREATE TABLE IF NOT EXISTS ip_tracking (
    id BIGSERIAL PRIMARY KEY,
    ip_address VARCHAR(255) NOT NULL UNIQUE,
    tentativas_falhadas INTEGER DEFAULT 0,
    ultimo_acesso TIMESTAMP,
    bloqueado BOOLEAN DEFAULT false,
    bloqueado_ate TIMESTAMP,
    motivo_bloqueio VARCHAR(255),
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ip_tracking_bloqueado ON ip_tracking(bloqueado, bloqueado_ate);
CREATE INDEX IF NOT EXISTS idx_ip_tracking_ultimo_acesso ON ip_tracking(ultimo_acesso);
