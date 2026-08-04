create table if not exists meu_gendaz_promocoes (
    id bigserial primary key,
    empresa_id bigint not null references empresas(id) on delete cascade,
    codigo varchar(80) not null,
    descricao varchar(180) not null,
    tipo varchar(20) not null,
    valor numeric(10,2) not null,
    data_inicio timestamp not null,
    data_fim timestamp not null,
    quantidade_limite integer,
    quantidade_usada integer not null default 0,
    status varchar(30) not null,
    aplicar_todos_servicos boolean not null default true,
    data_criacao timestamp not null default now(),
    data_notificacao timestamp,
    constraint uk_meu_gendaz_promocoes_empresa_codigo unique (empresa_id, codigo)
);

create table if not exists meu_gendaz_promocao_servico (
    promocao_id bigint not null references meu_gendaz_promocoes(id) on delete cascade,
    servico_id bigint not null references servicos(id) on delete cascade,
    primary key (promocao_id, servico_id)
);

create table if not exists meu_gendaz_promocao_uso (
    id bigserial primary key,
    promocao_id bigint not null references meu_gendaz_promocoes(id) on delete cascade,
    cliente_id bigint not null references clientes(id) on delete cascade,
    agendamento_id bigint references agendamentos(id) on delete set null,
    valor_desconto numeric(10,2) not null,
    data_uso timestamp not null default now()
);

create table if not exists meu_gendaz_promocao_notificacao (
    id bigserial primary key,
    promocao_id bigint not null references meu_gendaz_promocoes(id) on delete cascade,
    cliente_id bigint not null references clientes(id) on delete cascade,
    lido boolean not null default false,
    data_envio timestamp not null default now(),
    data_leitura timestamp
);
