ALTER TABLE pagamentos_planos
DROP CONSTRAINT IF EXISTS pagamentos_planos_metodo_pagamento_check;

ALTER TABLE pagamentos_planos
ADD CONSTRAINT pagamentos_planos_metodo_pagamento_check
CHECK (metodo_pagamento IN ('CREDIT_CARD', 'PIX', 'PIX_AUTO', 'CARTAO', 'DINHEIRO', 'BOLETO', 'OUTRO'));
