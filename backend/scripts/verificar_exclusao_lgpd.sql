-- ============================================================================
-- Verificação da Exclusão Definitiva de Dados (LGPD Art. 16)
-- gendaz — script de checagem pós-exclusão (rodar em STAGING)
-- ============================================================================
--
-- OBJETIVO: confirmar que, após chamar DELETE /api/lgpd/excluir-dados,
-- NENHUMA linha da empresa excluída permanece em nenhuma tabela do tenant.
--
-- COMO USAR:
--   1. Substitua o valor de empresa_id_alvo pelo id da empresa recém-excluída.
--   2. Rode no psql / console do Neon:
--        psql "$DATABASE_URL" -v empresa_id_alvo=123 -f verificar_exclusao_lgpd.sql
--
-- RESULTADO ESPERADO: todas as linhas impressas devem mostrar count = 0.
-- ============================================================================

DO $$
DECLARE
    v_empresa_id BIGINT := 123;  -- <<< ALTERE para o id da empresa excluída
    r RECORD;
    cnt BIGINT;
BEGIN
    RAISE NOTICE '=== Verificação de exclusão para empresa_id = % ===', v_empresa_id;

    -- Tabelas com coluna empresa_id (inclui business_id tratado abaixo)
    FOR r IN
        SELECT table_name
        FROM information_schema.columns
        WHERE column_name = 'empresa_id'
          AND table_schema = 'public'
    LOOP
        EXECUTE format('SELECT count(*) FROM %I WHERE empresa_id = $1', r.table_name)
        USING v_empresa_id
        INTO cnt;
        RAISE NOTICE 'empresa_id %, count=%', r.table_name, cnt;
    END LOOP;

    -- Tabelas-filha / de junção sem coluna empresa_id (verificadas via pai)
    FOR r IN
        SELECT * FROM (VALUES
            ('mensagens', 'conversa_id', 'conversas'),
            ('profissional_dias_trabalho', 'profissional_id', 'profissionais'),
            ('promocao_servico', 'promocao_id', 'promocoes'),
            ('meu_gendaz_promocao_servico', 'promocao_id', 'meu_gendaz_promocoes'),
            ('pagamentos_planos_cobrancas', 'pagamento_plano_id', 'pagamentos_planos'),
            ('password_reset_tokens', 'usuario_id', 'usuarios'),
            ('promocao_uso', 'cliente_id', 'clientes'),
            ('promocao_notificacao', 'cliente_id', 'clientes'),
            ('meu_gendaz_promocao_uso', 'cliente_id', 'clientes'),
            ('meu_gendaz_promocao_notificacao', 'cliente_id', 'clientes'),
            ('crm_contatos', 'cliente_id', 'clientes'),
            ('notificacoes', 'cliente_id', 'clientes'),
            ('notas_fiscais', 'cliente_id', 'clientes'),
            ('entregas', 'cliente_id', 'clientes'),
            ('pagamentos', 'cliente_id', 'clientes')
        ) AS t(tabela, fk, pai)
    LOOP
        BEGIN
            EXECUTE format('SELECT count(*) FROM %I WHERE %I IN (SELECT id FROM %I WHERE empresa_id = $1)',
                           r.tabela, r.fk, r.pai)
            USING v_empresa_id
            INTO cnt;
            RAISE NOTICE 'filha % (via %), count=%', r.tabela, r.pai, cnt;
        EXCEPTION WHEN undefined_table OR undefined_column THEN
            RAISE NOTICE 'filha %: tabela/coluna inexistente (ignorado)', r.tabela;
        END;
    END LOOP;

    -- caixa_despesas_log usa a coluna business_id (== empresa)
    BEGIN
        EXECUTE 'SELECT count(*) FROM caixa_despesas_log WHERE business_id = $1' USING v_empresa_id INTO cnt;
        RAISE NOTICE 'caixa_despesas_log (business_id), count=%', cnt;
    EXCEPTION WHEN undefined_column THEN
        RAISE NOTICE 'caixa_despesas_log: sem business_id (ignorado)';
    END;

    RAISE NOTICE '=== Fim da verificação ===';
END $$;
