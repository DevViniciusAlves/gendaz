-- Índices recomendados para reduzir latência nas consultas mais frequentes
-- Aplicar no Neon/PostgreSQL em janela controlada.

CREATE INDEX IF NOT EXISTS idx_clientes_empresa_id ON clientes (empresa_id);
CREATE INDEX IF NOT EXISTS idx_clientes_empresa_telefone ON clientes (empresa_id, telefone);

CREATE INDEX IF NOT EXISTS idx_servicos_empresa_id ON servicos (empresa_id);
CREATE INDEX IF NOT EXISTS idx_profissionais_empresa_id ON profissionais (empresa_id);

CREATE INDEX IF NOT EXISTS idx_agendamentos_empresa_id_data ON agendamentos (empresa_id, data);
CREATE INDEX IF NOT EXISTS idx_agendamentos_cliente_id ON agendamentos (cliente_id);
CREATE INDEX IF NOT EXISTS idx_agendamentos_profissional_data_hora ON agendamentos (profissional_id, data, hora_inicio, hora_fim);
CREATE INDEX IF NOT EXISTS idx_agendamentos_lembrete ON agendamentos (lembrete_wpp_enviado, status, data, hora_inicio);

CREATE INDEX IF NOT EXISTS idx_conversas_empresa_ultima_msg ON conversas (empresa_id, data_ultima_mensagem DESC);
CREATE INDEX IF NOT EXISTS idx_mensagens_conversa_data ON mensagens (conversa_id, data_envio ASC);

CREATE INDEX IF NOT EXISTS idx_pagamentos_empresa_status ON pagamentos (empresa_id, status);
CREATE INDEX IF NOT EXISTS idx_pagamentos_empresa_data ON pagamentos (empresa_id, data_pagamento);
CREATE INDEX IF NOT EXISTS idx_pagamentos_cliente_id ON pagamentos (cliente_id);
CREATE INDEX IF NOT EXISTS idx_pagamentos_agendamento_id ON pagamentos (agendamento_id);

CREATE INDEX IF NOT EXISTS idx_pagamentos_planos_empresa_status_data ON pagamentos_planos (empresa_id, status, data_criacao DESC);
CREATE INDEX IF NOT EXISTS idx_pagamentos_planos_empresa_data ON pagamentos_planos (empresa_id, data_criacao DESC);

