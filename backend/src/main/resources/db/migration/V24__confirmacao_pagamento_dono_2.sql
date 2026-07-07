ALTER TABLE agendamentos
    ADD COLUMN IF NOT EXISTS confirmacao_pagamento_dono_2_enviada BOOLEAN NOT NULL DEFAULT FALSE;
