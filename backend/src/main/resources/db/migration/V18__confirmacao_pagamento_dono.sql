ALTER TABLE agendamentos
    ADD COLUMN IF NOT EXISTS confirmacao_pagamento_dono_enviada BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS confirmacao_pagamento_dono_enviada_em TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS segunda_confirmacao_pagamento_dono_enviada BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS segunda_confirmacao_pagamento_dono_enviada_em TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS confirmacao_pagamento_dono_respondida BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS confirmacao_pagamento_dono_respondida_em TIMESTAMP NULL;
