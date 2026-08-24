CREATE TABLE admin_audit (
    id BIGSERIAL PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    usuario_nome VARCHAR(255) NOT NULL,
    acao VARCHAR(100) NOT NULL,
    entidade VARCHAR(100) NOT NULL,
    entidade_id BIGINT,
    descricao VARCHAR(1000) NOT NULL,
    data_hora TIMESTAMP NOT NULL,
    ip VARCHAR(45),
    user_agent VARCHAR(500)
);

CREATE INDEX idx_admin_audit_empresa_id ON admin_audit(empresa_id);
CREATE INDEX idx_admin_audit_data_hora ON admin_audit(data_hora DESC);
