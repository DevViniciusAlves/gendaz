-- =====================================================================
-- V64: Snapshot financeiro do cupom no agendamento
-- ---------------------------------------------------------------------
-- O agendamento passa a ser o SNAPSHOT financeiro do atendimento.
-- Depois de criado, alterar/desativar/excluir a promocao ou alterar o
-- preco do servico NAO pode modificar o historico financeiro.
-- =====================================================================

-- 1) Novas colunas em agendamentos (nullable para compatibilidade
--    com agendamentos antigos).
alter table if exists agendamentos
    add column if not exists valor_original numeric(10,2),
    add column if not exists valor_desconto numeric(10,2),
    add column if not exists valor_final numeric(10,2),
    add column if not exists cupom_codigo varchar(80),
    add column if not exists tipo_promocao_aplicada varchar(20),
    add column if not exists valor_promocao_aplicada numeric(10,2),
    add column if not exists promocao_origem_id bigint;

-- 2) FK preservadora: excluir a promocao NAO apaga o agendamento,
--    apenas limpa a referencia (o snapshot textual/financeiro fica).
alter table if exists agendamentos
    add constraint if not exists fk_agendamentos_promocao_origem
        foreign key (promocao_origem_id)
        references promocoes(id)
        on delete set null;

-- 3) CHECKs seguros (consideram NULL dos registros antigos).
alter table if exists agendamentos
    add constraint if not exists ck_agendamentos_valor_original_ge_0
        check (valor_original is null or valor_original >= 0);
alter table if exists agendamentos
    add constraint if not exists ck_agendamentos_valor_desconto_ge_0
        check (valor_desconto is null or valor_desconto >= 0);
alter table if exists agendamentos
    add constraint if not exists ck_agendamentos_valor_final_ge_0
        check (valor_final is null or valor_final >= 0);
alter table if exists agendamentos
    add constraint if not exists ck_agendamentos_valor_final_le_original
        check (valor_final is null or valor_original is null or valor_final <= valor_original);

-- 4) Reforco no banco da regra de uso aplicada pelo codigo:
--    um cliente nao pode usar novamente a mesma promocao.
--    Diagnostico antes de criar o UNIQUE: se existirem duplicatas,
--    PARAR a migracao (deploy falha alto, humano corrige).
do $$
declare
    duplicados_promocao_cliente integer;
    duplicados_agendamento integer;
begin
    select count(*) into duplicados_promocao_cliente
    from (
        select promocao_id, cliente_id
        from meu_gendaz_promocao_uso
        group by promocao_id, cliente_id
        having count(*) > 1
    ) d;

    if duplicados_promocao_cliente > 0 then
        raise exception 'DADOS INCOMPATIVEIS: % par(es) promocao/cliente com mais de um uso em meu_gendaz_promocao_uso. Corrija os dados antes de aplicar o UNIQUE (promocao_id, cliente_id).', duplicados_promocao_cliente;
    end if;

    select count(*) into duplicados_agendamento
    from (
        select agendamento_id
        from meu_gendaz_promocao_uso
        where agendamento_id is not null
        group by agendamento_id
        having count(*) > 1
    ) d;

    if duplicados_agendamento > 0 then
        raise exception 'DADOS INCOMPATIVEIS: % agendamento(s) com mais de um uso de cupom em meu_gendaz_promocao_uso. O produto aceita apenas um cupom por agendamento. Corrija os dados antes de aplicar o UNIQUE (agendamento_id).', duplicados_agendamento;
    end if;
end $$;

alter table if exists meu_gendaz_promocao_uso
    add constraint if not exists uk_meu_gendaz_promocao_uso_promocao_cliente
        unique (promocao_id, cliente_id);

create unique index if not exists uk_meu_gendaz_promocao_uso_agendamento
    on meu_gendaz_promocao_uso (agendamento_id)
    where agendamento_id is not null;

-- 5) Indices para leitura do snapshot por empresa.
create index if not exists idx_agendamentos_promocao_origem
    on agendamentos (promocao_origem_id);