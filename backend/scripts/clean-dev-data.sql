-- =====================================================
-- SCRIPT: Limpeza de Dados de Desenvolvimento
-- Projeto: SaaS Gendaz (PostgreSQL / Neon)
-- =====================================================
-- Remove todos os registros de negocio preservando:
--   - Estrutura do banco (tabelas, colunas, tipos)
--   - Migrations (flyway_schema_history)
--   - Indices, constraints, funcoes, triggers, extensoes
--   - Usuario SUPER_ADMIN
-- =====================================================

-- =====================================================
-- BLOCO 1: Desabilitar triggers e constraints
-- =====================================================
-- Impede que triggers de auditoria/validacao atrapalhem a limpeza.

ALTER TABLE IF EXISTS mensagens                    DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS conversas                    DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS notificacoes                 DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS entregas                     DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS notas_fiscais                DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS pagamentos_planos            DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS pagamentos                   DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS agendamentos                 DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS agenda_blocked_days          DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS horarios_atendimento         DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS servicos                     DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS profissionais                DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS clientes                     DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS assinaturas                  DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS password_reset_tokens        DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS admin_impersonation_sessions DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS audit_logs                   DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS chamados                     DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS usuarios                     DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS empresas                     DISABLE TRIGGER ALL;
ALTER TABLE IF EXISTS planos                       DISABLE TRIGGER ALL;


-- =====================================================
-- BLOCO 2: Preservar usuario SUPER_ADMIN
-- =====================================================
-- Salva o registro do SUPER_ADMIN para reinserir depois.

CREATE TEMPORARY TABLE _tmp_super_admin AS
SELECT * FROM usuarios WHERE perfil = 'SUPER_ADMIN';


-- =====================================================
-- BLOCO 3: Limpar dados de negocio (ordem por FK)
-- =====================================================
-- TRUNCATE CASCADE remove registros de todas as tabelas
-- que dependem via FK, respeitando a ordem do grafo.

-- Tabelas sem FK de negocio (folhas)
TRUNCATE TABLE planos                       CASCADE;
TRUNCATE TABLE flyway_schema_history        CASCADE;

-- Todas as tabelas que referenciam empresas (raiz)
-- CASCADE remove em cascade: clientes, servicos, profissionais,
-- agendamentos, pagamentos, assinaturas, conversas, mensagens,
-- audit_logs, admin_impersonation_sessions, password_reset_tokens,
-- horarios_atendimento, agenda_blocked_days, pagamentos_planos,
TRUNCATE TABLE empresas                     CASCADE;


-- =====================================================
-- BLOCO 4: Limpar usuarios (preservar SUPER_ADMIN)
-- =====================================================
-- DELETE remove todos exceto o SUPER_ADMIN.
-- A FK de empresas ja foi limpa pelo CASCADE acima.

DELETE FROM usuarios WHERE perfil != 'SUPER_ADMIN';


-- =====================================================
-- BLOCO 5: Reabilitar triggers
-- =====================================================

ALTER TABLE IF EXISTS mensagens                    ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS conversas                    ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS notificacoes                 ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS entregas                     ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS notas_fiscais                ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS pagamentos_planos            ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS pagamentos                   ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS agendamentos                 ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS agenda_blocked_days          ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS horarios_atendimento         ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS servicos                     ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS profissionais                ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS clientes                     ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS assinaturas                  ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS password_reset_tokens        ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS admin_impersonation_sessions ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS audit_logs                   ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS chamados                     ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS usuarios                     ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS empresas                     ENABLE TRIGGER ALL;
ALTER TABLE IF EXISTS planos                       ENABLE TRIGGER ALL;


-- =====================================================
-- BLOCO 6: Reinserir SUPER_ADMIN
-- =====================================================

INSERT INTO usuarios (
    id, nome, email, senha, perfil, status,
    data_criacao, data_atualizacao,
    aceitou_termos, data_aceite_termos, data_aceite_politica,
    versao_termos, versao_politica, sessao_ativa, empresa_id
)
SELECT
    id, nome, email, senha, perfil, status,
    data_criacao, data_atualizacao,
    aceitou_termos, data_aceite_termos, data_aceite_politica,
    versao_termos, versao_politica, sessao_ativa, empresa_id
FROM _tmp_super_admin;

DROP TABLE IF EXISTS _tmp_super_admin;


-- =====================================================
-- BLOCO 7: Reiniciar todas as sequences
-- =====================================================
-- Zera o contador de IDENTITY para todas as tabelas.

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT sequencename
        FROM pg_sequences
        WHERE schemaname = 'public'
    LOOP
        EXECUTE 'ALTER SEQUENCE ' || quote_ident(r.sequencename) || ' RESTART WITH 1';
    END LOOP;
END $$;


-- =====================================================
-- FIM DO SCRIPT
-- =====================================================
