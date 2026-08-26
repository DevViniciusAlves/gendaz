CREATE TABLE IF NOT EXISTS profissional_dias_trabalho (
    profissional_id BIGINT NOT NULL,
    dia_semana VARCHAR(20) NOT NULL,
    CONSTRAINT pk_profissional_dias_trabalho PRIMARY KEY (profissional_id, dia_semana),
    CONSTRAINT fk_profissional_dias_trabalho_profissional FOREIGN KEY (profissional_id) REFERENCES profissionais(id) ON DELETE CASCADE,
    CONSTRAINT ck_profissional_dias_trabalho_dia CHECK (dia_semana IN ('SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO', 'DOMINGO'))
);

INSERT INTO profissional_dias_trabalho (profissional_id, dia_semana)
SELECT p.id, d.dia_semana
FROM profissionais p
CROSS JOIN (VALUES
    ('SEGUNDA'),
    ('TERCA'),
    ('QUARTA'),
    ('QUINTA'),
    ('SEXTA'),
    ('SABADO'),
    ('DOMINGO')
) AS d(dia_semana)
ON CONFLICT DO NOTHING;
