'use strict';

// Leitura centralizada de configuracao via variaveis de ambiente.
// Nenhum segredo possui valor padrao utilizavel: sem WHATSAPP_INTERNAL_TOKEN,
// os endpoints internos permanecem bloqueados (fail-closed).

const path = require('path');

function readPort() {
  const raw = (process.env.PORT || '3001').trim();
  const port = Number.parseInt(raw, 10);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(`PORT invalida: "${raw}"`);
  }
  return port;
}

function readPositiveInt(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined || raw === '') {
    return fallback;
  }
  const value = Number.parseInt(String(raw).trim(), 10);
  if (!Number.isInteger(value) || value < 0) {
    throw new Error(`${name} invalida: "${raw}"`);
  }
  return value;
}

const config = {
  port: readPort(),
  internalToken: (process.env.WHATSAPP_INTERNAL_TOKEN || '').trim(),
  // Diretorio de runtime das credenciais Baileys (fora do Git e da imagem Docker).
  sessionsDir:
    (process.env.WHATSAPP_SESSIONS_DIR || '').trim() ||
    path.resolve(__dirname, '..', '..', '.sessions'),
  reconnectBaseDelayMs: readPositiveInt('WHATSAPP_RECONNECT_BASE_DELAY_MS', 2000),
  reconnectMaxDelayMs: readPositiveInt('WHATSAPP_RECONNECT_MAX_DELAY_MS', 60000),
  reconnectMaxAttempts: readPositiveInt('WHATSAPP_RECONNECT_MAX_ATTEMPTS', 10),
};

module.exports = config;
