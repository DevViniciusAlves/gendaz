ALTER TABLE agendamentos
    ADD COLUMN IF NOT EXISTS confirmacao_pagamento_dono_2_enviada BOOLEAN;

UPDATE agendamentos
SET confirmacao_pagamento_dono_2_enviada = FALSE
WHERE confirmacao_pagamento_dono_2_enviada IS NULL;

ALTER TABLE agendamentos
    ALTER COLUMN confirmacao_pagamento_dono_2_enviada SET DEFAULT FALSE;

ALTER TABLE agendamentos
    ALTER COLUMN confirmacao_pagamento_dono_2_enviada SET NOT NULL;
