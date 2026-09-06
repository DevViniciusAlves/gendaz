'use strict';

// Validacao e normalizacao do companyId recebido nas rotas.
//
// O identificador e usado verbatim como UM unico segmento de diretorio
// (<sessionsDir>/<companyId>). O charset permitido exclui '.', '/' e
// espacos, o que impede path traversal e garante isolamento entre empresas.
// Identificadores sao case-sensitive e nao sofrem folding/normalizacao
// alem de trim, para evitar colisao entre empresas distintas.

const COMPANY_ID_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;

function normalizeCompanyId(raw) {
  if (typeof raw !== 'string') {
    return null;
  }
  const value = raw.trim();
  if (!COMPANY_ID_PATTERN.test(value)) {
    return null;
  }
  return value;
}

module.exports = { normalizeCompanyId, COMPANY_ID_PATTERN };
