'use strict';

const { describe, it, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const { createShutdown } = require('../src/shutdown');

let savedExitCode;

beforeEach(() => {
  savedExitCode = process.exitCode;
});

afterEach(() => {
  process.exitCode = savedExitCode;
});

function makeFakes() {
  const order = [];
  const logs = [];
  const server = {
    closeCalls: 0,
    close(callback) {
      this.closeCalls += 1;
      order.push('close');
      callback(null);
    }
  };
  const worker = {
    stopCalls: 0,
    async stop() {
      this.stopCalls += 1;
      order.push('worker.stop');
    }
  };
  const sessions = {
    shutdownCalls: 0,
    async shutdownAll() {
      this.shutdownCalls += 1;
      order.push('shutdownAll');
    }
  };
  const authStore = {
    flushCalls: 0,
    closeCalls: 0,
    async flushAll() {
      this.flushCalls += 1;
      order.push('flushAll');
    },
    async close() {
      this.closeCalls += 1;
      order.push('authStore.close');
    }
  };
  const log = {
    log: (msg) => logs.push(String(msg)),
    warn: (msg) => logs.push(String(msg)),
    error: (msg) => logs.push(String(msg)),
    info: (msg) => logs.push(String(msg)),
  };
  return { server, worker, sessions, authStore, log, order, logs };
}

describe('createShutdown', () => {
  it('ordem completa do shutdown', async () => {
    const f = makeFakes();
    const shutdown = createShutdown({
      server: f.server,
      sessions: f.sessions,
      authStore: f.authStore,
      worker: f.worker,
      log: f.log
    });

    await shutdown('SIGTERM');

    assert.deepEqual(f.order, [
      'close',
      'worker.stop',
      'shutdownAll',
      'flushAll',
      'authStore.close'
    ]);
  });

  it('single-flight com Promise.all', async () => {
    const f = makeFakes();
    const shutdown = createShutdown({
      server: f.server,
      sessions: f.sessions,
      authStore: f.authStore,
      worker: f.worker,
      log: f.log
    });

    const p1 = shutdown('SIGTERM');
    const p2 = shutdown('SIGINT');
    await Promise.all([p1, p2]);

    assert.equal(f.server.closeCalls, 1);
    assert.equal(f.worker.stopCalls, 1);
    assert.equal(f.sessions.shutdownCalls, 1);
    assert.equal(f.authStore.flushCalls, 1);
    assert.equal(f.authStore.closeCalls, 1);
  });

  it('falha no worker nao interrompe o restante', async () => {
    const f = makeFakes();

    f.worker.stop = async () => {
      f.order.push('worker.stop');
      throw new Error('worker failure');
    };

    const shutdown = createShutdown({
      server: f.server,
      sessions: f.sessions,
      authStore: f.authStore,
      worker: f.worker,
      log: f.log
    });

    await shutdown('SIGTERM');

    assert.ok(f.order.includes('shutdownAll'));
    assert.ok(f.order.includes('flushAll'));
    assert.ok(f.order.includes('authStore.close'));
    assert.equal(process.exitCode, 1);
  });

  it('log nao expoe segredo', async () => {
    const f = makeFakes();
    const err = new Error('worker failure');
    err.secret = 'SEGREDO_NAO_LOGAR';

    f.worker.stop = async () => {
      f.order.push('worker.stop');
      throw err;
    };

    const shutdown = createShutdown({
      server: f.server,
      sessions: f.sessions,
      authStore: f.authStore,
      worker: f.worker,
      log: f.log
    });

    await shutdown('SIGTERM');

    const allLogs = f.logs.join('\n');
    assert.equal(allLogs.includes('SEGREDO_NAO_LOGAR'), false);
  });

  it('shutdown define _shuttingDown para impedir novos sockets', async () => {
    const f = makeFakes();
    let shuttingDownValue = false;
    const sessions = {
      shutdownCalls: 0,
      _shuttingDown: false,
      async shutdownAll() {
        this.shutdownCalls += 1;
        this._shuttingDown = true;
        shuttingDownValue = true;
        f.order.push('shutdownAll');
      }
    };
    const shutdown = createShutdown({
      server: f.server,
      sessions,
      authStore: f.authStore,
      worker: f.worker,
      log: f.log
    });

    await shutdown('SIGTERM');
    assert.equal(shuttingDownValue, true);
    assert.equal(sessions._shuttingDown, true);
  });
});

const { SessionManager } = require('../src/whatsapp/sessionManager');

function deferred() {
  let resolve;
  const promise = new Promise((res) => { resolve = res; });
  return { promise, resolve };
}

describe('SessionManager - race establish vs shutdownAll', () => {
  it('shutdown durante authStore.load nao cria socket nem reconnect timer', async () => {
    const rejections = [];
    const onUnhandled = (reason) => { rejections.push(reason); };
    process.on('unhandledRejection', onUnhandled);

    const loadGate = deferred();
    let loadStarted;
    const loadStartedPromise = new Promise((resolve) => { loadStarted = resolve; });
    const created = [];
    const store = {
      load: async () => {
        loadStarted();
        await loadGate.promise;
        return { state: { creds: { registered: true } }, saveCreds: async () => {} };
      },
      clear: async () => {},
      listCompanies: async () => [],
      hasRegisteredSession: async () => true,
      flush: async () => {},
      flushAll: async () => {},
      close: async () => {},
    };
    const manager = new SessionManager({
      authStore: store,
      createSocket: () => {
        const sock = { ev: { on: () => {} }, end: async () => {}, logout: async () => {} };
        created.push(sock);
        return sock;
      },
      baseDelayMs: 10,
      maxDelayMs: 40,
      maxAttempts: 3,
      log: { log: () => {}, info: () => {}, warn: () => {}, error: () => {} },
    });

    try {
      const record = manager.getRecord('empresa-load-race');
      const connectPromise = manager.connect('empresa-load-race');
      await loadStartedPromise;
      assert.equal(created.length, 0);
      await manager.shutdownAll();
      loadGate.resolve();
      await connectPromise;
      assert.equal(created.length, 0);
      assert.equal(record.sock, null);
      assert.equal(record.reconnectTimer, null);
      assert.equal(rejections.length, 0);
    } finally {
      process.removeListener('unhandledRejection', onUnhandled);
    }
  });

  it('shutdown durante closeSocketQuietly nao cria socket novo nem timer', async () => {
    const rejections = [];
    const onUnhandled = (reason) => { rejections.push(reason); };
    process.on('unhandledRejection', onUnhandled);

    const endGate = deferred();
    let endStarted;
    const endStartedPromise = new Promise((resolve) => { endStarted = resolve; });
    const created = [];
    const store = {
      load: async () => ({ state: { creds: { registered: true } }, saveCreds: async () => {} }),
      clear: async () => {},
      listCompanies: async () => [],
      hasRegisteredSession: async () => true,
      flush: async () => {},
      flushAll: async () => {},
      close: async () => {},
    };
    const manager = new SessionManager({
      authStore: store,
      createSocket: () => {
        const sock = {
          ev: { on: () => {} },
          end: async () => {
            endStarted();
            await endGate.promise;
          },
          logout: async () => {},
        };
        created.push(sock);
        return sock;
      },
      baseDelayMs: 10,
      maxDelayMs: 40,
      maxAttempts: 3,
      log: { log: () => {}, info: () => {}, warn: () => {}, error: () => {} },
    });

    try {
      await manager.connect('empresa-end-race');
      assert.equal(created.length, 1);
      const record = manager.getRecord('empresa-end-race');
      record.state = 'DISCONNECTED';

      const reconnectPromise = manager.connect('empresa-end-race');
      await endStartedPromise;
      assert.equal(created.length, 1);

      const shutdownPromise = manager.shutdownAll();
      endGate.resolve();
      await reconnectPromise;
      await shutdownPromise;

      assert.equal(created.length, 1);
      assert.equal(record.sock, null);
      assert.equal(record.reconnectTimer, null);
      assert.equal(rejections.length, 0);
    } finally {
      process.removeListener('unhandledRejection', onUnhandled);
      endGate.resolve();
    }
  });
});
