ALTER TABLE empresas
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(60);

UPDATE empresas
SET timezone = 'America/Cuiaba'
WHERE timezone IS NULL OR TRIM(timezone) = '';

ALTER TABLE empresas
    ALTER COLUMN timezone SET DEFAULT 'America/Cuiaba';

ALTER TABLE empresas
    ALTER COLUMN timezone SET NOT NULL;
