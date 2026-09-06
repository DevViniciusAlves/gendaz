'use strict';

// Ponto de entrada: sobe o servidor HTTP na porta configurada.
// Nunca loga tokens ou segredos — apenas porta e estado.

const http = require('http');
const config = require('./config');
const { createApp } = require('./app');

const server = http.createServer(createApp());

server.on('clientError', (err, socket) => {
  console.error('[whatsapp-service] erro de protocolo HTTP:', err.message);
  socket.end('HTTP/1.1 400 Bad Request\r\n\r\n');
});

server.listen(config.port, () => {
  console.log(`[whatsapp-service] ouvindo na porta ${config.port}`);
  if (!config.internalToken) {
    console.log('[whatsapp-service] aviso: WHATSAPP_INTERNAL_TOKEN nao configurado; endpoints internos futuros ficarao desabilitados');
  }
});

function shutdown(signal) {
  console.log(`[whatsapp-service] recebendo ${signal}, encerrando...`);
  server.close((err) => {
    if (err) {
      console.error('[whatsapp-service] erro ao encerrar:', err.message);
      process.exitCode = 1;
      return;
    }
    console.log('[whatsapp-service] encerrado');
  });
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
process.on('unhandledRejection', (reason) => {
  console.error('[whatsapp-service] promessa rejeitada sem tratamento:', reason instanceof Error ? reason.message : reason);
});
process.on('uncaughtException', (err) => {
  console.error('[whatsapp-service] excecao nao capturada:', err.message);
  process.exit(1);
});
