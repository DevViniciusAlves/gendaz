alter table if exists meu_gendaz_promocoes
    add column if not exists promocao_origem_id bigint;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'fk_meu_gendaz_promocoes_origem'
    ) then
        alter table meu_gendaz_promocoes
            add constraint fk_meu_gendaz_promocoes_origem
            foreign key (promocao_origem_id) references promocoes(id) on delete set null;
    end if;
end $$;

create unique index if not exists uk_meu_gendaz_promocoes_origem_id
    on meu_gendaz_promocoes (promocao_origem_id)
    where promocao_origem_id is not null;

update meu_gendaz_promocoes mg
set promocao_origem_id = p.id
from promocoes p
where mg.empresa_id = p.empresa_id
  and upper(mg.codigo) = upper(p.codigo)
  and mg.promocao_origem_id is null;
