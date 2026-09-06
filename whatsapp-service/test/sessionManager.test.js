'use strict';

const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const Boom = require('@hapi/boom');
const { SessionManager, STATES } = require('../src/whatsapp/sessionManager');

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function makeHarness(options = {}) {
  const created = [];
  let saves = 0;
  let clears = [];
  const factory = () => {
    const sock = {
      endCalls: 0,
      logoutCalls: 0,
      handlers: {},
      ev: {
        on(event, fn) {
          sock.handlers[event] = sock.handlers[event] || [];
          sock.handlers[event].push(fn);
        },
      },
      end: async () => {
        sock.endCalls += 1;
      },
      logout: async () => {
        sock.logoutCalls += 1;
      },
    };
    created.push(sock);
    return sock;
  };
  const authStore = {
    load: async () => ({
      state: { creds: { registered: false } },
      saveCreds: async () => {
        saves += 1;
      },
    }),
    clear: async (companyId) => {
      clears.push(companyId);
    },
  };
  const manager = new SessionManager({
    authStore,
    createSocket: factory,
    baseDelayMs: 10,
    maxDelayMs: 40,
    maxAttempts: 3,
    log: { log: () => {}, warn: () => {}, error: () => {} },
    ...options,
  });
  return { manager, created, stats: () => ({ saves, clears: [...clears] }) };
}

function emit(sock, event, payload) {
  for (const fn of sock.handlers[event] || []) {
    fn(payload);
  }
}

function closeWith(sock, statusCode) {
  emit(sock, 'connection.update', {
    connection: 'close',
    lastDisconnect: { error: Boom.boomify(new Error('queda'), { statusCode }), date: new Date() },
  });
}

describe('SessionManager', () => {
  let h;
  beforeEach(() => {
    h = makeHarness();
  });

  it('duas chamadas simultaneas de connect criam um unico socket', async () => {
    const [a, b] = await Promise.all([h.manager.connect('empresa-1'), h.manager.connect('empresa-1')]);
    assert.equal(h.created.length, 1);
    assert.equal(a, b);
    assert.equal(a.state, STATES.CONNECTING);
  });

  it('connect repetido em CONNECTING reutiliza a sessao', async () => {
    await h.manager.connect('empresa-1');
    const again = await h.manager.connect('empresa-1');
    assert.equal(h.created.length, 1);
    assert.equal(again.state, STATES.CONNECTING);
  });

  it('empresas diferentes ficam isoladas', async () => {
    await h.manager.connect('empresa-1');
    await h.manager.connect('empresa-2');
    assert.equal(h.created.length, 2);
    assert.notEqual(h.manager.status('empresa-1'), h.manager.status('empresa-2'));
    assert.equal(h.manager.status('empresa-1').companyId, 'empresa-1');
  });

  it('loggedOut (401) nao reconecta', async () => {
    await h.manager.connect('empresa-1');
    closeWith(h.created[0], 401);
    await sleep(80);
    assert.equal(h.manager.status('empresa-1').state, STATES.LOGGED_OUT);
    assert.equal(h.created.length, 1);
    assert.equal(h.manager.getQr('empresa-1'), null);
  });

  it('queda definitiva 440 nao reconecta e nao fica LOGGED_OUT', async () => {
    await h.manager.connect('empresa-1');
    closeWith(h.created[0], 440);
    await sleep(80);
    assert.equal(h.manager.status('empresa-1').state, STATES.DISCONNECTED);
    assert.equal(h.created.length, 1);
  });

  it('falha temporaria entra em RECONNECTING e reconecta com backoff', async () => {
    await h.manager.connect('empresa-1');
    closeWith(h.created[0], 408);
    await sleep(5);
    assert.equal(h.manager.status('empresa-1').state, STATES.RECONNECTING);
    await sleep(80);
    assert.equal(h.created.length, 2);
    emit(h.created[1], 'connection.update', { connection: 'open' });
    await sleep(10);
    const status = h.manager.status('empresa-1');
    assert.equal(status.state, STATES.CONNECTED);
    assert.equal(status.reconnectAttempts, 0);
  });

  it('esgota tentativas e para em DISCONNECTED sem loop', async () => {
    h = makeHarness({ maxAttempts: 2 });
    await h.manager.connect('empresa-1');
    closeWith(h.created[0], 408);
    await sleep(30);
    closeWith(h.created[1], 408);
    await sleep(30);
    closeWith(h.created[2], 408);
    await sleep(120);
    assert.equal(h.manager.status('empresa-1').state, STATES.DISCONNECTED);
    assert.equal(h.created.length, 3);
  });

  it('logout desvincula, limpa credenciais e nao reconecta', async () => {
    await h.manager.connect('empresa-1');
    const record = await h.manager.logout('empresa-1');
    assert.equal(record.state, STATES.LOGGED_OUT);
    assert.equal(h.created[0].logoutCalls, 1);
    assert.deepEqual(h.stats().clears, ['empresa-1']);
    await sleep(80);
    assert.equal(h.created.length, 1);
    assert.equal(h.manager.status('empresa-1').state, STATES.LOGGED_OUT);
  });

  it('creds.update persiste imediatamente', async () => {
    await h.manager.connect('empresa-1');
    emit(h.created[0], 'creds.update', {});
    await sleep(10);
    assert.equal(h.stats().saves, 1);
  });

  it('qr e mantido em memoria e limpo ao abrir', async () => {
    await h.manager.connect('empresa-1');
    emit(h.created[0], 'connection.update', { qr: 'QR-1' });
    await sleep(5);
    assert.deepEqual(h.manager.getQr('empresa-1').qr, 'QR-1');
    emit(h.created[0], 'connection.update', { qr: 'QR-2' });
    await sleep(5);
    assert.deepEqual(h.manager.getQr('empresa-1').qr, 'QR-2');
    emit(h.created[0], 'connection.update', { connection: 'open' });
    await sleep(5);
    assert.equal(h.manager.getQr('empresa-1'), null);
  });

  it('companyId invalido e rejeitado', async () => {
    await assert.rejects(h.manager.connect('../x'), /invalid_company_id/);
    assert.throws(() => h.manager.status('a/b'), /invalid_company_id/);
  });
});
