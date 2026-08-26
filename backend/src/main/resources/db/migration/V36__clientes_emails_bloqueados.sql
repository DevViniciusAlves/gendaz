create table if not exists clientes_emails_bloqueados (
    id bigserial primary key,
    empresa_id bigint not null references empresas(id),
    email varchar(120) not null,
    motivo varchar(255),
    data_bloqueio timestamp not null default now()
);

create unique index if not exists ux_clientes_emails_bloqueados_empresa_email
    on clientes_emails_bloqueados (empresa_id, lower(email));
