'use strict';

// Ponto de entrada: sobe o servidor HTTP na porta configurada.
// Nunca loga tokens, QR ou segredos — apenas porta e estado.

const fs = require('fs');
const http = require('http');
const config = require('./config');
const { installLibsignalLogRedaction } = require('./whatsapp/logRedaction');

// Redige dumps de chaves Signal (libsignal usa console global direto, fora
// do alcance do logger pino). Instalado antes de qualquer socket existir.
installLibsignalLogRedaction(console);
const { createApp } = require('./app');
const { FileAuthStateStore } = require('./whatsapp/authStore');
const { SessionManager } = require('./whatsapp/sessionManager');
const { MessageSender } = require('./whatsapp/messageSender');
const { createSocket } = require('./whatsapp/socketFactory');
const { createShutdown } = require('./shutdown');

function buildSessions() {
  fs.mkdirSync(config.sessionsDir, { recursive: true, mode: 0o700 });
  return new SessionManager({
    authStore: new FileAuthStateStore(config.sessionsDir),
    createSocket,
    baseDelayMs: config.reconnectBaseDelayMs,
    maxDelayMs: config.reconnectMaxDelayMs,
    maxAttempts: config.reconnectMaxAttempts,
  });
}

function buildApp(sessions) {
  return createApp({
    sessions,
    messageSender: new MessageSender({
      sendFn: ({ companyId, recipient, text }) => sessions.sendText(companyId, recipient, text),
    }),
  });
}

// Bootstrap: restore completo ANTES de aceitar requests, para nao servir
// estado intermediario falso (ex.: NOT_CONNECTED antes do restore).
// Erro inesperado do initialize propaga e a porta nem abre.
async function bootstrap({ sessions, app, port, listen = (server, p) => new Promise((resolve) => {
  server.listen(p, resolve);
}) }) {
  await sessions.initialize();
  const server = http.createServer(app);
  server.on('clientError', (err, socket) => {
    console.error('[whatsapp-service] erro de protocolo HTTP:', err.message);
    socket.end('HTTP/1.1 400 Bad Request\r\n\r\n');
  });
  await listen(server, port);
  return server;
}

async function main() {
  const sessions = buildSessions();
  const server = await bootstrap({ sessions, app: buildApp(sessions), port: config.port });
  console.log(`[whatsapp-service] ouvindo na porta ${config.port}`);
  if (!config.internalToken) {
    console.log('[whatsapp-service] aviso: WHATSAPP_INTERNAL_TOKEN nao configurado; endpoints internos ficarao bloqueados');
  }

  const shutdown = createShutdown({ server, sessions });

  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('unhandledRejection', (reason) => {
    // So message/codigo: nunca despejar o objeto (pode conter auth/keys).
    const seguro = reason instanceof Error ? reason.message : String(reason);
    console.error('[whatsapp-service] promessa rejeitada sem tratamento:', seguro);
  });
  process.on('uncaughtException', (err) => {
    console.error('[whatsapp-service] excecao nao capturada:', err.message);
    process.exit(1);
  });
  return { server, sessions };
}

if (require.main === module) {
  main().catch((err) => {
    console.error('[whatsapp-service] falha fatal no bootstrap:', err.message);
    process.exit(1);
  });
}

module.exports = { bootstrap, buildSessions, buildApp, main };
