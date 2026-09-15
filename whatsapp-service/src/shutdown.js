'use strict';

// Orquestracao do desligamento gracioso (SIGTERM/SIGINT).
// Ordem proposital:
//  1. trava de execucao unica (shuttingDown);
//  2. server.close() primeiro — para de aceitar novas conexoes;
//  3. sessions.shutdownAll() depois — encerra sockets/timers SEM logout;
//  4. authStore.flushAll() — aguarda writes pendentes de auth;
//  5. authStore.close() — encerra pool PostgreSQL.
// Erros sao logados apenas com err.message (sem dados sensiveis).

function createShutdown({ server, sessions, authStore, log = console } = {}) {
  if (!server || !sessions) {
    throw new Error('createShutdown requer server e sessions');
  }
  
  let shuttingDown = false;
  let shutdownPromise = null;

  return async function shutdown(signal) {
    // Single-flight: se ja esta encerrando, retorna a promise em progresso
    if (shuttingDown) {
      if (shutdownPromise) {
        return await shutdownPromise;
      }
      return;
    }
    
    shuttingDown = true;
    
    // Cria a promise que representa todo o shutdown
    shutdownPromise = (async () => {
      log.log(`[whatsapp-service] recebendo ${signal}, encerrando...`);
      
      try {
        // PASSO 1: Fechar server (para de aceitar conexoes)
        await new Promise((resolve, reject) => {
          server.close((err) => {
            if (err) {
              log.error('[whatsapp-service] erro ao encerrar servidor:', err.message);
              process.exitCode = 1;
            }
            resolve();
          });
        });
        
        // PASSO 2: Encerrar sessoes (fecha sockets SEM logout)
        if (typeof sessions.shutdownAll === 'function') {
          try {
            await sessions.shutdownAll();
          } catch (sessionErr) {
            log.error('[whatsapp-service] erro ao encerrar sessões:', sessionErr && sessionErr.message);
            process.exitCode = 1;
          }
        }
        
        // PASSO 3: Fluxo de writes pendentes de auth
        if (authStore && typeof authStore.flushAll === 'function') {
          try {
            await authStore.flushAll();
          } catch (flushErr) {
            log.error('[whatsapp-service] erro ao flush auth:', flushErr && flushErr.message);
            process.exitCode = 1;
          }
        }
        
        // PASSO 4: Fechar pool PostgreSQL
        if (authStore && typeof authStore.close === 'function') {
          try {
            await authStore.close();
          } catch (closeErr) {
            log.error('[whatsapp-service] erro ao fechar pool:', closeErr && closeErr.message);
            process.exitCode = 1;
          }
        }
        
        log.log('[whatsapp-service] encerrado com sucesso');
      } catch (err) {
        log.error('[whatsapp-service] erro inesperado no shutdown:', err && err.message);
        process.exitCode = 1;
      }
    })();
    
    return await shutdownPromise;
  };
}

module.exports = { createShutdown };