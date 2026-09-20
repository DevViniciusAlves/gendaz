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

function readRequiredString(name) {
  const raw = process.env[name];
  if (raw === undefined || raw === '' || raw.trim() === '') {
    throw new Error(`${name} e obrigatorio mas nao foi configurado`);
  }
  return raw.trim();
}

function readEncryptionKey() {
  const raw = process.env.WHATSAPP_AUTH_ENCRYPTION_KEY;
  if (raw === undefined || raw === '' || raw.trim() === '') {
    throw new Error('WHATSAPP_AUTH_ENCRYPTION_KEY e obrigatorio mas nao foi configurado');
  }
  const key = Buffer.from(raw.trim(), 'base64');
  if (key.length !== 32) {
    throw new Error('WHATSAPP_AUTH_ENCRYPTION_KEY deve ser 32 bytes (base64 de 44 chars)');
  }
  return key;
}

function validateBackendUrl(rawValue) {
  const raw = String(rawValue || '').trim();

  if (!raw) {
    return { valid: false, reason: 'missing', url: '' };
  }

  let parsed;

  try {
    parsed = new URL(raw);
  } catch {
    return { valid: false, reason: 'invalid_url', url: '' };
  }

  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
    return { valid: false, reason: 'invalid_protocol', url: '' };
  }

  if (parsed.username || parsed.password) {
    return { valid: false, reason: 'userinfo_not_allowed', url: '' };
  }

  if (parsed.search || parsed.hash) {
    return { valid: false, reason: 'query_or_fragment_not_allowed', url: '' };
  }

  const normalizedPath = parsed.pathname.replace(/\/+$/, '');

  if (normalizedPath !== '') {
    return { valid: false, reason: 'path_not_allowed', url: '' };
  }

  return {
    valid: true,
    reason: null,
    url: parsed.origin,
  };
}

const backendUrlValidation = validateBackendUrl(
  process.env.GENDAZ_BACKEND_URL
);

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
  reconnectRecoveryCooldownMs: readPositiveInt('WHATSAPP_RECONNECT_RECOVERY_COOLDOWN_MS', 60000),
  // Base do backend Spring para o callback interno de entrega
  // (POST {backendUrl}/internal/whatsapp/delivery). Sem valor, o callback e
  // ignorado com aviso (fail-safe) — nunca derruba o socket.
  backendUrl:
    backendUrlValidation.valid
      ? backendUrlValidation.url
      : '',
  backendUrlValidation,
  // Auth store configuration
  authStore: (process.env.WHATSAPP_AUTH_STORE || 'file').trim().toLowerCase(),
  databaseUrl: (process.env.WHATSAPP_DATABASE_URL || '').trim(),
  encryptionKey: process.env.WHATSAPP_AUTH_ENCRYPTION_KEY ? readEncryptionKey() : null,
};

module.exports = {
  ...config,
  validateBackendUrl,
};
