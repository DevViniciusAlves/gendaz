'use strict';

// Ponto de entrada: sobe o servidor HTTP na porta configurada.
// Nunca loga tokens, QR ou segredos — apenas porta e estado.

const fs = require('fs');
const http = require('http');
const config = require('./config');
const { createApp } = require('./app');
const { FileAuthStateStore } = require('./whatsapp/authStore');
const { SessionManager } = require('./whatsapp/sessionManager');
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

const server = http.createServer(createApp({ sessions }));

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
