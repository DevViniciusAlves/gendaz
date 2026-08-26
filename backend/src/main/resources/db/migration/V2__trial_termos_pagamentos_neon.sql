alter table if exists empresas alter column documento drop not null;

alter table if exists usuarios add column if not exists aceitou_termos boolean not null default false;
alter table if exists usuarios add column if not exists data_aceite_termos timestamp;
alter table if exists usuarios add column if not exists versao_termos varchar(255);

alter table if exists assinaturas add column if not exists data_inicio_teste date;
alter table if exists assinaturas add column if not exists data_fim_teste date;

insert into planos (nome, descricao, valor_mensal, status)
values ('BASICO', 'Agenda, clientes e servicos.', 69.99, 'ATIVO')
on conflict (nome) do nothing;

insert into planos (nome, descricao, valor_mensal, status)
values ('PRO', 'Agenda com financeiro, pagamentos e relatorios.', 110.00, 'ATIVO')
on conflict (nome) do nothing;
