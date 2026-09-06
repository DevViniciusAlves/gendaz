'use strict';

// Leitura centralizada de configuracao via variaveis de ambiente.
// Nenhum segredo possui valor padrao utilizavel: sem WHATSAPP_INTERNAL_TOKEN,
// os futuros endpoints internos permanecem desabilitados (fail-closed).

function readPort() {
  const raw = (process.env.PORT || '3001').trim();
  const port = Number.parseInt(raw, 10);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(`PORT invalida: "${raw}"`);
  }
  return port;
}

const config = {
  port: readPort(),
  internalToken: (process.env.WHATSAPP_INTERNAL_TOKEN || '').trim(),
};

module.exports = config;
