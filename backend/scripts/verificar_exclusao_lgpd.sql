-- ============================================================================
-- Verificação da Exclusão Definitiva de Dados (LGPD Art. 16)
-- gendaz — script de checagem pós-exclusão (rodar em STAGING)
-- ============================================================================
--
-- OBJETIVO: confirmar que, após chamar DELETE /api/lgpd/excluir-dados,
-- NENHUMA linha da empresa excluída permanece em nenhuma tabela com empresa_id.
--
-- COMO USAR:
--   1. Substitua o valor de empresa_id_alvo pelo id da empresa recém-excluída.
--   2. Rode o bloco abaixo no psql / console do Neon:
--        psql "$DATABASE_URL" -v empresa_id_alvo=123 -f verificar_exclusao_lgpd.sql
--      ou altere a constante dentro do bloco e rode direto.
--
-- RESULTADO ESPERADO: todas as linhas impressas devem mostrar count = 0.
-- Se alguma mostrar count > 0, há tabela não coberta pela exclusão.
-- ============================================================================

DO $$
DECLARE
    v_empresa_id BIGINT := 123;  -- <<< ALTERE para o id da empresa excluída
    r RECORD;
    cnt BIGINT;
BEGIN
    RAISE NOTICE '=== Verificação de exclusão para empresa_id = % ===', v_empresa_id;

    FOR r IN
        SELECT table_name
        FROM information_schema.columns
        WHERE column_name = 'empresa_id'
          AND table_schema = 'public'
    LOOP
        EXECUTE format('SELECT count(*) FROM %I WHERE empresa_id = $1', r.table_name)
        USING v_empresa_id
        INTO cnt;
        RAISE NOTICE '%, count=%', r.table_name, cnt;
    END LOOP;

    -- Tabelas-filha cuja FK NÃO usa empresa_id (checagem extra de redundância)
    FOR r IN
        SELECT unnest(ARRAY[
            'mensagens',
            'crm_contatos',
            'notificacoes',
            'promocao_notificacao',
            'promocao_uso',
            'meu_gendaz_promocao_notificacoes',
            'meu_gendaz_promocao_uso',
            'password_reset_tokens'
        ]) AS t
    LOOP
        BEGIN
            EXECUTE format('SELECT count(*) FROM %I WHERE empresa_id = $1', r.t)
            USING v_empresa_id
            INTO cnt;
            RAISE NOTICE 'filha(empresa_id) %, count=%', r.t, cnt;
        EXCEPTION WHEN undefined_column THEN
            RAISE NOTICE 'filha %: sem coluna empresa_id (esperado, checar via pai)', r.t;
        END;
    END LOOP;

    RAISE NOTICE '=== Fim da verificação ===';
END $$;
