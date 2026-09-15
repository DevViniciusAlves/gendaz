'use strict';

// Ponto de entrada: sobe o servidor HTTP na porta configurada.
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

fs.mkdirSync(config.sessionsDir, { recursive: true, mode: 0o700 });

const sessions = new SessionManager({
  authStore: new FileAuthStateStore(config.sessionsDir),
  createSocket,
  baseDelayMs: config.reconnectBaseDelayMs,
  maxDelayMs: config.reconnectMaxDelayMs,
  maxAttempts: config.reconnectMaxAttempts,
});

// Restauracao automatica de sessoes persistidas apos boot.
fs.readdirSync(config.sessionsDir).forEach((companyId) => {
  if (fs.statSync(require('path').join(config.sessionsDir, companyId)).isDirectory()) {
    sessions.connect(companyId).catch((err) => {
      console.error(`[whatsapp-service] falha ao restaurar sessao empresa=${companyId}: ${err.message}`);
    });
  }
});

const server = http.createServer(createApp({
  sessions,
  messageSender: new MessageSender({
    sendFn: ({ companyId, recipient, text }) => sessions.sendText(companyId, recipient, text),
  }),
}));

sessions.initialize().catch(err => {
  console.error('[whatsapp-service] erro fatal na inicializacao das sessoes:', err.message);
});

server.on('clientError', (err, socket) => {
  console.error('[whatsapp-service] erro de protocolo HTTP:', err.message);
  socket.end('HTTP/1.1 400 Bad Request\r\n\r\n');
});

server.listen(config.port, () => {
  console.log(`[whatsapp-service] ouvindo na porta ${config.port}`);
  if (!config.internalToken) {
    console.log('[whatsapp-service] aviso: WHATSAPP_INTERNAL_TOKEN nao configurado; endpoints internos ficarao bloqueados');
  }
});

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
