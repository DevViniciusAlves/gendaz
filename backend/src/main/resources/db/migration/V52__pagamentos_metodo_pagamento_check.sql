ALTER TABLE IF EXISTS pagamentos
    DROP CONSTRAINT IF EXISTS pagamentos_metodo_pagamento_check;

ALTER TABLE IF EXISTS pagamentos
    ADD CONSTRAINT pagamentos_metodo_pagamento_check
    CHECK (
        metodo_pagamento IS NULL
        OR metodo_pagamento IN ('PIX', 'PIX_AUTO', 'CREDIT_CARD', 'CARTAO', 'DEBITO', 'CREDITO', 'DINHEIRO', 'BOLETO', 'OUTRO')
    ) NOT VALID;

DO $$
BEGIN
    IF to_regclass('public.pagamentos') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pagamentos
           WHERE metodo_pagamento IS NOT NULL
             AND metodo_pagamento NOT IN ('PIX', 'PIX_AUTO', 'CREDIT_CARD', 'CARTAO', 'DEBITO', 'CREDITO', 'DINHEIRO', 'BOLETO', 'OUTRO')
       ) THEN
        ALTER TABLE pagamentos VALIDATE CONSTRAINT pagamentos_metodo_pagamento_check;
    END IF;
END $$;
