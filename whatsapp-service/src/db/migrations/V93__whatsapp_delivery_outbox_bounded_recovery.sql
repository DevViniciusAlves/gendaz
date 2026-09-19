-- V93: Contador de ciclos de recovery persistente para bounded recovery
-- Garante que o limite global de recovery nao dependa de memoria do Render.
-- migration segura/idempotente: so adiciona coluna se nao existir.

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'whatsapp_delivery_outbox' AND column_name = 'recovery_count'
  ) THEN
    ALTER TABLE whatsapp_delivery_outbox ADD COLUMN recovery_count INTEGER NOT NULL DEFAULT 0;
  END IF;
END$$;

-- Comentario: este campo persiste o numero de ciclos de recuperacao global.
-- Cada vez que _recoverDeadCycle() e executado, o contador e incrementado.
-- Quando recovery_count >= MAX_AUTH_RECOVERY_CYCLES (auth) ou
-- MAX_TRANSIENT_RECOVERY_CYCLES (transitorio), a linha nao e mais selecionada.
-- O DEFAULT 0 garante idempotencia para instalacoes que ja tenham o schema.

COMMENT ON COLUMN whatsapp_delivery_outbox.recovery_count IS
  'Numero de ciclos de recovery global ja realizados.
   Limites: auth_error ate MAX_AUTH_RECOVERY_CYCLES=1, transitorio ate MAX_TRANSIENT_RECOVERY_CYCLES=2.
   Reiniciar o Render nao zera este valor (fonte: PostgreSQL).';

-- Index opcional para acelerar filtros por recovery_count.
-- Nao e unico para permitir auditoria historica.
CREATE INDEX IF NOT EXISTS idx_whatsapp_delivery_outbox_recovery_count
  ON whatsapp_delivery_outbox (recovery_count)
  WHERE recovery_count > 0;

-- Comentario de auditoria: trigger que grava quem mudou e quando,
-- se o projeto ja possuir trigger de auditoria; senao fica como comentario.
-- Isso evita perda de contexto futuro sobre por que o registro ficou DEAD.