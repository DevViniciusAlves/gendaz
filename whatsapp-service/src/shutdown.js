'use strict';

// Orquestracao do desligamento gracioso (SIGTERM/SIGINT).
//
// Ordem proposital:
//  1. trava de execucao unica (shuttingDown);
//  2. server.close() primeiro — para de aceitar novas conexoes o mais cedo
//     possivel, eliminando a janela em que um /connect criaria sessao apos
//     o encerramento das sessoes;
//  3. sessions.shutdownAll() depois — encerra sockets/timers Baileys sem
//     logout() e sem apagar auth state;
//  4. authStore.flushAll() — aguarda writes pendentes de auth;
//  5. authStore.close() — encerra pool PostgreSQL (se aplicavel).
// Erros sao logados apenas com err.message (sem dados sensiveis).

function createShutdown({ server, sessions, authStore, log = console } = {}) {
  if (!server || !sessions) {
    throw new Error('createShutdown requer server e sessions');
  }
  let shuttingDown = false;

  return function shutdown(signal) {
    if (shuttingDown) {
      return;
    }
    shuttingDown = true;
    log.log(`[whatsapp-service] recebendo ${signal}, encerrando...`);
    server.close((err) => {
      if (err) {
        log.error('[whatsapp-service] erro ao encerrar:', err.message);
        process.exitCode = 1;
      }
      sessions.shutdownAll().then(
        async () => {
          try {
            if (authStore && typeof authStore.flushAll === 'function') {
              await authStore.flushAll();
            }
            if (authStore && typeof authStore.close === 'function') {
              await authStore.close();
            }
            log.log('[whatsapp-service] encerrado');
          } catch (shutdownErr) {
            log.error('[whatsapp-service] erro ao encerrar sessoes/auth:', shutdownErr && shutdownErr.message);
            process.exitCode = 1;
          }
        },
        (shutdownErr) => {
          log.error('[whatsapp-service] erro ao encerrar sessoes:', shutdownErr && shutdownErr.message);
          process.exitCode = 1;
        }
      );
    });
  };
}

module.exports = { createShutdown };
