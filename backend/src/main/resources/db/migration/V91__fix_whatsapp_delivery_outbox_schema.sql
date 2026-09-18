-- V88: corrige schema legado da whatsapp_delivery_outbox.
-- V87 usa CREATE TABLE IF NOT EXISTS; em ambientes onde a tabela
-- ja existia com schema antigo, a migration foi marcada como aplicada
-- sem substituir a estrutura.
--
-- Esta migration reconcilia apenas o schema legado.
-- Em bancos onde V87 criou o schema correto, o bloco principal e no-op.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'whatsapp_delivery_outbox'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'whatsapp_delivery_outbox'
          AND column_name = 'id'
    ) THEN

        -- A estrutura antiga usa PK composta.
        ALTER TABLE whatsapp_delivery_outbox
            DROP CONSTRAINT IF EXISTS whatsapp_delivery_outbox_pkey;

        -- company_id antigo e VARCHAR; o codigo novo e a FK esperam BIGINT.
        -- Se existir dado nao numerico, a migration DEVE falhar em vez
        -- de truncar/corrigir silenciosamente.
        ALTER TABLE whatsapp_delivery_outbox
            ALTER COLUMN company_id TYPE BIGINT
            USING company_id::BIGINT;

        -- Contrato atual limita o provider_message_id a 120 caracteres.
        ALTER TABLE whatsapp_delivery_outbox
            ALTER COLUMN provider_message_id TYPE VARCHAR(120);

        -- Chave tecnica usada pelo DeliveryOutboxWorker.
        ALTER TABLE whatsapp_delivery_outbox
            ADD COLUMN id BIGSERIAL;

        ALTER TABLE whatsapp_delivery_outbox
            ADD CONSTRAINT whatsapp_delivery_outbox_pkey
            PRIMARY KEY (id);

        -- Campos renomeados entre o schema legado e o schema atual.
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'whatsapp_delivery_outbox'
              AND column_name = 'last_http_status'
        )
        AND NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'whatsapp_delivery_outbox'
              AND column_name = 'http_status'
        ) THEN
            ALTER TABLE whatsapp_delivery_outbox
                RENAME COLUMN last_http_status TO http_status;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'whatsapp_delivery_outbox'
              AND column_name = 'last_error_code'
        )
        AND NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'whatsapp_delivery_outbox'
              AND column_name = 'http_error_message'
        ) THEN
            ALTER TABLE whatsapp_delivery_outbox
                RENAME COLUMN last_error_code TO http_error_message;
        END IF;

        ALTER TABLE whatsapp_delivery_outbox
            ALTER COLUMN http_error_message TYPE TEXT;

        ALTER TABLE whatsapp_delivery_outbox
            ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMP;

        -- Campo legado nao utilizado pelo novo contrato.
        ALTER TABLE whatsapp_delivery_outbox
            DROP COLUMN IF EXISTS delivery_status;

        -- A PK antiga garantia essa unicidade.
        -- Depois da troca para PK(id), preservar a mesma garantia.
        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conrelid = 'public.whatsapp_delivery_outbox'::regclass
              AND conname = 'unique_provider_message_id'
        ) THEN
            ALTER TABLE whatsapp_delivery_outbox
                ADD CONSTRAINT unique_provider_message_id
                UNIQUE (company_id, provider_message_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conrelid = 'public.whatsapp_delivery_outbox'::regclass
              AND conname = 'fk_company_id'
        ) THEN
            ALTER TABLE whatsapp_delivery_outbox
                ADD CONSTRAINT fk_company_id
                FOREIGN KEY (company_id)
                REFERENCES empresas(id)
                ON DELETE CASCADE;
        END IF;

    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_outbox_state_next_attempt
    ON whatsapp_delivery_outbox(state, next_attempt_at)
    WHERE state IN ('PENDING', 'PROCESSING');

CREATE INDEX IF NOT EXISTS idx_outbox_company_id
    ON whatsapp_delivery_outbox(company_id);