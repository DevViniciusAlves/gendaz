CREATE TABLE IF NOT EXISTS meu_gendaz_otp_challenges (
    id bigserial PRIMARY KEY,
    empresa_id bigint NOT NULL REFERENCES empresas(id),
    email varchar(120) NOT NULL,
    otp_hash varchar(128),
    otp_expira_em timestamp NULL,
    tentativas_falhas integer NOT NULL DEFAULT 0,
    ultima_solicitacao timestamp NULL,
    reenviar_disponivel_em timestamp NULL,
    janela_solicitacoes_inicio timestamp NULL,
    solicitacoes_na_janela integer NOT NULL DEFAULT 0,
    bloqueado_ate timestamp NULL,
    validado_em timestamp NULL,
    onboarding_session_hash varchar(128),
    onboarding_session_expira_em timestamp NULL,
    data_criacao timestamp NOT NULL DEFAULT now(),
    data_atualizacao timestamp NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_meu_gendaz_otp_empresa_email
    ON meu_gendaz_otp_challenges (empresa_id, lower(trim(email)));

CREATE INDEX IF NOT EXISTS idx_meu_gendaz_otp_empresa_email
    ON meu_gendaz_otp_challenges (empresa_id, email);

CREATE INDEX IF NOT EXISTS idx_meu_gendaz_otp_onboarding_hash
    ON meu_gendaz_otp_challenges (onboarding_session_hash);

CREATE INDEX IF NOT EXISTS idx_meu_gendaz_otp_expira
    ON meu_gendaz_otp_challenges (otp_expira_em);

CREATE INDEX IF NOT EXISTS idx_meu_gendaz_onboarding_expira
    ON meu_gendaz_otp_challenges (onboarding_session_expira_em);

CREATE TABLE IF NOT EXISTS security_rate_limit_entries (
    id bigserial PRIMARY KEY,
    scope_key varchar(128) NOT NULL,
    janela_inicio timestamp NOT NULL,
    quantidade integer NOT NULL DEFAULT 0,
    bloqueado_ate timestamp NULL,
    expira_em timestamp NOT NULL,
    data_criacao timestamp NOT NULL DEFAULT now(),
    data_atualizacao timestamp NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_security_rate_limit_scope
    ON security_rate_limit_entries (scope_key);

CREATE INDEX IF NOT EXISTS idx_security_rate_limit_expira
    ON security_rate_limit_entries (expira_em);
