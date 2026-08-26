alter table pagamentos_planos
    add column if not exists external_reference varchar(120),
    add column if not exists pix_qr_code_base64 varchar(4000);

create unique index if not exists uk_pagamentos_planos_external_reference on pagamentos_planos(external_reference);
