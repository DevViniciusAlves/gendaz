create table if not exists pagamentos_planos (
    id bigserial primary key,
    empresa_id bigint not null references empresas(id),
    plano_id bigint not null references planos(id),
    assinatura_id bigint references assinaturas(id),
    valor numeric(10, 2) not null,
    metodo_pagamento varchar(50) not null,
    status varchar(50) not null,
    provider varchar(120) not null,
    provider_payment_id varchar(180) not null unique,
    checkout_url varchar(600),
    pix_copia_ecola varchar(600),
    data_criacao timestamp,
    data_atualizacao timestamp,
    data_expiracao timestamp,
    data_pagamento timestamp
);

create index if not exists idx_pagamentos_planos_empresa on pagamentos_planos(empresa_id);
create index if not exists idx_pagamentos_planos_provider on pagamentos_planos(provider_payment_id);
