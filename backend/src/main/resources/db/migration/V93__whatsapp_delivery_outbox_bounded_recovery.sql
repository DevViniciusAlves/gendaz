-- V93: Contador de ciclos de recovery persistente para bounded recovery
-- Garante que o limite global de recovery nao dependa de memoria do Render.
-- migration segura/idempotente: so adiciona coluna se nao existir.
-- Registros antigos recebem recovery_count = 0 (DEFAULT 0).

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'whatsapp_delivery_outbox' AND column_name = 'recovery_count'
  ) THEN
    ALTER TABLE whatsapp_delivery_outbox ADD COLUMN recovery_count INTEGER NOT NULL DEFAULT 0;
  END IF;
END$$;

-- Comentario: este campo persiste o numero de ciclos de recovery global.
-- Cada vez que _recoverDeadCycle() e executado, o contador e incrementado.
-- Quando recovery_count >= MAX_AUTH_RECOVERY_CYCLES (auth) ou
-- MAX_TRANSIENT_RECOVERY_CYCLES (transitorio), a linha nao e mais selecionada.
-- O DEFAULT 0 garante idempotencia para instalacoes que ja tenham o schema.

COMMENT ON COLUMN whatsapp_delivery_outbox.recovery_count IS
  'Numero de ciclos de recovery global ja realizados.
   Limites: auth_error ate MAX_AUTH_RECOVERY_CYCLES=1, transitorio ate MAX_TRANSIENT_RECOVERY_CYCLES=2.
   Reiniciar o Render nao zera este valor (fonte: PostgreSQL).';

-- Index parcial compatível com a query REAL do dead recovery:
-- state = 'DEAD' + http_error_message + recovery_count + updated_at
CREATE INDEX IF NOT EXISTS idx_outbox_dead_recovery_bounded
    ON whatsapp_delivery_outbox(
        http_error_message,
        recovery_count,
        updated_at
    )
    WHERE state = 'DEAD';

-- Auditoria: trigger que grava quem mudou e quando,
-- se o projeto ja possuir trigger de auditoria; senao fica como comentario.
-- Isso evita perda de contexto futuro sobre por que o registro ficou DEAD.
-- trigger legacy: nao criado aqui para evitar side effects indesejados.