-- V28: Normalize existing phones to E.164 format (digits only, with country code)
-- For Brazilian phones: prepend 55 if missing country code

-- Normalize empresa phones
UPDATE empresa
SET telefone = CASE
    WHEN LENGTH(REPLACE(REPLACE(REPLACE(REPLACE(telefone, ' ', ''), '-', ''), '(', ''), ')', '')) < 12
    THEN '55' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(telefone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '')
    ELSE REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(telefone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '')
END
WHERE telefone IS NOT NULL
  AND TRIM(telefone) != ''
  AND LENGTH(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(telefone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', ''), '.', '')) < 12;

-- Normalize cliente phones
UPDATE cliente
SET telefone = CASE
    WHEN LENGTH(REPLACE(REPLACE(REPLACE(REPLACE(telefone, ' ', ''), '-', ''), '(', ''), ')', '')) < 12
    THEN '55' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(telefone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '')
    ELSE REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(telefone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '')
END
WHERE telefone IS NOT NULL
  AND TRIM(telefone) != ''
  AND LENGTH(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(telefone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', ''), '.', '')) < 12;

-- Normalize usuario phones
UPDATE usuario
SET telefone = CASE
    WHEN LENGTH(REPLACE(REPLACE(REPLACE(REPLACE(telefone, ' ', ''), '-', ''), '(', ''), ')', '')) < 12
    THEN '55' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(telefone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '')
    ELSE REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(telefone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '')
END
WHERE telefone IS NOT NULL
  AND TRIM(telefone) != ''
  AND LENGTH(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(telefone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', ''), '.', '')) < 12;
