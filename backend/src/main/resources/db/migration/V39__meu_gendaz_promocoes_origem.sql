alter table if exists meu_gendaz_promocoes
    add column if not exists promocao_origem_id bigint;

create index if not exists idx_meu_gendaz_promocoes_origem on meu_gendaz_promocoes (promocao_origem_id);
