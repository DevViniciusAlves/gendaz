'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { createShutdown } = require('../src/shutdown');

function makeFakes() {
  const order = [];
  const logs = [];
  const server = {
    closeCalls: 0,
    close: (cb) => {
      server.closeCalls += 1;
      order.push('close');
      cb(null);
    },
  };
  const sessions = {
    shutdownCalls: 0,
    shutdownAll: async () => {
      sessions.shutdownCalls += 1;
      order.push('shutdownAll');
    },
  };
  const log = {
    log: (msg) => logs.push(['log', String(msg)]),
    error: (msg, detail) => logs.push(['error', String(msg), String(detail)]),
  };
  return { server, sessions, log, order, logs };
}

describe('createShutdown', () => {
  it('fecha o HTTP antes das sessoes e executa uma unica vez', async () => {
    const f = makeFakes();
    const shutdown = createShutdown(f);
    shutdown('SIGTERM');
    shutdown('SIGTERM'); // segundo sinal nao reexecuta
    await new Promise((resolve) => setImmediate(resolve));
    assert.deepEqual(f.order, ['close', 'shutdownAll']);
    assert.equal(f.server.closeCalls, 1);
    assert.equal(f.sessions.shutdownCalls, 1);
  });

  it('erro no shutdown das sessoes nao expoe dados sensiveis', async () => {
    const f = makeFakes();
    const secret = 'segredo-nunca-logar-123';
    f.sessions.shutdownAll = async () => {
      const err = new Error('falha');
      err.secret = secret;
      throw err;
    };
    const previousExitCode = process.exitCode;
    const shutdown = createShutdown(f);
    shutdown('SIGINT');
    await new Promise((resolve) => setImmediate(resolve));
    await new Promise((resolve) => setImmediate(resolve));
    const logged = f.logs.map((entry) => entry.join(' ')).join('\n');
    assert.ok(!logged.includes(secret));
    assert.ok(logged.includes('erro ao encerrar sessoes'));
    assert.equal(process.exitCode, 1);
    process.exitCode = previousExitCode;
  });
});
