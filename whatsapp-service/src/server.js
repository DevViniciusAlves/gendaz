'use strict';

// Ponto de entrada: sobe o servidor HTTP na porta configurada.
// O HTTP comeca a aceitar requests ANTES de SessionManager.initialize()
// terminar o restore das sessoes persistidas: assim o /health e status
// ficam disponiveis durante o cold start, evitando 502 no Render.
// Nunca loga tokens, QR ou segredos — apenas porta e estado.

const fs = require('fs');
const http = require('http');
const { Pool } = require('pg');
const config = require('./config');
const { installLibsignalLogRedaction } = require('./whatsapp/logRedaction');

// Redige dumps de chaves Signal (libsignal usa console global direto, fora
// do alcance do logger pino). Instalado antes de qualquer socket existir.
installLibsignalLogRedaction(console);
const { createApp } = require('./app');
const { FileAuthStateStore, PostgresAuthStateStore } = require('./whatsapp/authStore');
const { SessionManager } = require('./whatsapp/sessionManager');
const { MessageSender } = require('./whatsapp/messageSender');
const { DeliveryReporter } = require('./whatsapp/deliveryReporter');
const { DeliveryOutbox } = require('./whatsapp/deliveryOutbox');
const { createSocket } = require('./whatsapp/socketFactory');
const { createShutdown } = require('./shutdown');
const { createWorker } = require('./whatsapp/deliveryOutboxWorker');

// Reporter unico do processo: dedup em memoria + POST ao backend Spring.
// Falha no callback nunca derruba o socket (DeliveryReporter nunca rejeita
// de forma fatal; o listener ainda envolve em catch por defesa).
function buildDeliveryReporter(outbox = null) {
  return new DeliveryReporter({
    backendUrl: config.backendUrl,
    internalToken: config.internalToken,
    outbox,
  });
}

function buildAuthStore(pool) {
  if (config.authStore === 'postgres') {
    if (!config.databaseUrl && !pool) {
      throw new Error('WHATSAPP_DATABASE_URL e obrigatorio quando WHATSAPP_AUTH_STORE=postgres');
    }
    if (!config.encryptionKey) {
      throw new Error('WHATSAPP_AUTH_ENCRYPTION_KEY e obrigatorio quando WHATSAPP_AUTH_STORE=postgres');
    }
    const poolInstance = pool || new Pool({
      connectionString: config.databaseUrl,
      max: 5,
      idleTimeoutMillis: 30000,
      connectionTimeoutMillis: 10000,
    });
    return new PostgresAuthStateStore({ pool: poolInstance, encryptionKey: config.encryptionKey });
  }
  // file store (desenvolvimento/local)
  fs.mkdirSync(config.sessionsDir, { recursive: true, mode: 0o700 });
  return new FileAuthStateStore(config.sessionsDir);
}

function buildSessions(deliveryReporter, authStore) {
  const reporter = deliveryReporter || buildDeliveryReporter();
  return new SessionManager({
    authStore,
    createSocket,
    onDelivery: ({ companyId, messageId }) => reporter.report(companyId, messageId),
    baseDelayMs: config.reconnectBaseDelayMs,
    maxDelayMs: config.reconnectMaxDelayMs,
    maxAttempts: config.reconnectMaxAttempts,
    recoveryCooldownMs: config.reconnectRecoveryCooldownMs,
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

async function bootstrap({ sessions, app, port, listen = (server, p) => new Promise((resolve) => {
  server.listen(p, resolve);
}) }) {
  const server = http.createServer(app);
  server.on('clientError', (err, socket) => {
    console.error('[whatsapp-service] erro de protocolo HTTP:', err.message);
    socket.end('HTTP/1.1 400 Bad Request\r\n\r\n');
  });
  await listen(server, port);

  const initializationPromise = Promise.resolve()
    .then(() => sessions.initialize())
    .catch((err) => {
      console.error(
        '[whatsapp-service] falha no restore inicial. erroTipo=' +
          (err && err.name ? err.name : 'Error'),
      );
    });

  initializationPromise.then(() => {
    // restore concluido — status ja disponivel durante todo o processo
  });

  return { server, initializationPromise };
}

async function main() {
  let sharedPool = null;
  if (config.databaseUrl) {
    sharedPool = new Pool({
      connectionString: config.databaseUrl,
      max: 5,
      idleTimeoutMillis: 30000,
      connectionTimeoutMillis: 10000,
    });
  }

  const outbox = sharedPool
    ? new DeliveryOutbox({ pool: sharedPool })
    : null;

  const authStore = buildAuthStore(sharedPool);
  const deliveryReporter = buildDeliveryReporter(outbox);
  const sessions = buildSessions(deliveryReporter, authStore);
  const { server } = await bootstrap({ sessions, app: buildApp(sessions), port: config.port });
  console.log(`[whatsapp-service] ouvindo na porta ${config.port}`);
  if (!config.internalToken) {
    console.log('[whatsapp-service] aviso: WHATSAPP_INTERNAL_TOKEN nao configurado; endpoints internos ficarao bloqueados');
  }

  const worker = sharedPool ? createWorker(sharedPool, {
    backendUrl: config.backendUrl,
    internalToken: config.internalToken,
  }) : null;
  if (worker) {
    worker.start();
  }

  const shutdown = createShutdown({ server, sessions, authStore, worker });

  process.on('SIGTERM', async () => {
    await shutdown('SIGTERM');
    process.exit(process.exitCode || 0);
  });
  process.on('SIGINT', async () => {
    await shutdown('SIGINT');
    process.exit(process.exitCode || 0);
  });
  process.on('unhandledRejection', (reason) => {
    const seguro = reason instanceof Error ? reason.message : String(reason);
    console.error('[whatsapp-service] promessa rejeitada sem tratamento:', seguro);
  });
  process.on('uncaughtException', (err) => {
    console.error('[whatsapp-service] excecao nao capturada:', err.message);
    process.exit(1);
  });
  return { server, sessions, authStore, worker };
}

if (require.main === module) {
  main().catch((err) => {
    console.error('[whatsapp-service] falha fatal no bootstrap:', err.message);
    process.exit(1);
  });
}

module.exports = { bootstrap, buildSessions, buildApp, buildAuthStore, main };
