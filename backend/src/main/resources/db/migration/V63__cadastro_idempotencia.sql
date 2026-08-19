-- Idempotencia do cadastro de contas.
-- Garante que a mesma tentativa logica de criar conta nunca execute criacao duplicada,
-- mesmo que o navegador, usuario, proxy ou outra camada reenvie a mesma request.
--
-- REGRA DE SEGURANCA: nunca persistir dados sensiveis nesta tabela.
-- Proibido: senha, hash de senha, token de sessao, cookie, CSRF, segredo Stripe/checkout,
-- API key ou payload completo. Guardar apenas hashes (SHA-256) e IDs de reconstrucao.
CREATE TABLE IF NOT EXISTS cadastro_idempotencia (
    id BIGSERIAL PRIMARY KEY,
    key_hash VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    empresa_id BIGINT,
    usuario_id BIGINT,
    assinatura_id BIGINT,
    pagamento_plano_id BIGINT,
    status_conta VARCHAR(30),
    ultimo_request_id VARCHAR(64),
    criado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL,
    expira_em TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cadastro_idempotencia_key_hash ON cadastro_idempotencia (key_hash);
CREATE INDEX IF NOT EXISTS idx_cadastro_idempotencia_status ON cadastro_idempotencia (status);
CREATE INDEX IF NOT EXISTS idx_cadastro_idempotencia_expira_em ON cadastro_idempotencia (expira_em);
