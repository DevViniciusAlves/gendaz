CREATE INDEX idx_agendamentos_empresa_data ON agendamentos (empresa_id, data);
CREATE INDEX idx_agendamentos_status ON agendamentos (status);
CREATE INDEX idx_agendamentos_lembrete_wpp_enviado ON agendamentos (lembrete_wpp_enviado);
CREATE INDEX idx_empresas_whatsapp_connected ON empresas (whatsapp_connected);
