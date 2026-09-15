'use strict';

// Ponto de entrada: sobe o servidor HTTP na porta configurada.
// O HTTP so comeca a aceitar requests DEPOIS que SessionManager.initialize()
// termina o restore das sessoes persistidas: sem essa ordem, um status
// consultado no boot poderia retornar NOT_CONNECTED incorretamente.
// Nunca loga tokens, QR ou segredos — apenas porta e estado.

const fs = require('fs');
const http = require('http');
const config = require('./config');
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

// Bootstrap testavel: faz o restore completo primeiro e so depois abre a
// porta. Falha em UMA sessao nao derruba as outras (SessionManager.initialize
// loga por empresa e continua); erro inesperado do initialize propaga e a
// porta nem abre — nunca servimos estado intermediario falso.
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
    console.error('[whatsapp-service] promessa rejeitada sem tratamento:', reason instanceof Error ? reason.message : reason);
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
