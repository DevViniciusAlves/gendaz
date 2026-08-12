DO $$
DECLARE
    constraint_record record;
    index_record record;
BEGIN
    FOR constraint_record IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE rel.relname = 'usuarios'
          AND nsp.nspname = current_schema()
          AND con.contype = 'u'
          AND array_length(con.conkey, 1) = 1
          AND EXISTS (
              SELECT 1
              FROM unnest(con.conkey) AS key_column(attnum_value)
              JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = key_column.attnum_value
              WHERE att.attname = 'email'
          )
    LOOP
        EXECUTE format('ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS %I', constraint_record.conname);
    END LOOP;

    FOR index_record IN
        SELECT idx.indexname
        FROM pg_indexes idx
        WHERE idx.schemaname = current_schema()
          AND idx.tablename = 'usuarios'
          AND idx.indexdef ILIKE '%UNIQUE INDEX%'
          AND idx.indexdef ILIKE '%(email)%'
          AND idx.indexdef NOT ILIKE '%empresa_id%'
    LOOP
        EXECUTE format('DROP INDEX IF EXISTS %I', index_record.indexname);
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_usuario_empresa_email
    ON usuarios (empresa_id, email);
