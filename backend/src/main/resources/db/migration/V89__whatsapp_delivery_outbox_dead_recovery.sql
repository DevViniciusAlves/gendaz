-- V89: indice de apoio ao dead recovery do delivery outbox.
-- O worker Node reprocessa periodicamente registros DEAD por erro
-- recuperavel (auth_error, network_error, server_error, rate_limit,
-- not_configured) com updated_at antigo, em lote pequeno. Registros DEAD
-- por erro permanente (permanent_error, unexpected_http_status) nunca sao
-- tocados pelo recovery. Apenas leitura/escrita do worker; sem alteracao
-- de dados existentes e sem mudar o contrato da tabela.
CREATE INDEX IF NOT EXISTS idx_outbox_dead_recovery
    ON whatsapp_delivery_outbox(http_error_message, updated_at)
    WHERE state = 'DEAD';
