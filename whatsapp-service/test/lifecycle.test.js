'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { SessionManager, STATES } = require('../src/whatsapp/sessionManager');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function makeSock() {
  const sock = {
    handlers: {},
    ev: { on(e, fn) { (sock.handlers[e] = sock.handlers[e] || []).push(fn); } },
    end: async () => {},
    logout: async () => {},
  };
  return sock;
}
function emit(sock, event, payload) {
  for (const fn of sock.handlers[event] || []) fn(payload);
}

describe('Lifecycle - correcao restauracao automatica', () => {
  it('1. connection=open muda creds.registered para true, chama saveCreds, mantem CONNECTED', async () => {
    let saved = 0;
    const creds = { registered: false };
    const auth = { state: { creds }, saveCreds: async () => { saved += 1; } };
    const store = {
      load: async () => auth,
      clear: async () => {},
      listCompanies: async () => [],
      listPersistedCompanies: async () => [],
      hasRegisteredSession: async () => false,
    };
    const created = [];
    const manager = new SessionManager({
      authStore: store,
      createSocket: () => { const s = makeSock(); created.push(s); return s; },
      log: { log: () => {}, info: () => {}, warn: () => {}, error: () => {} },
    });
    await manager.connect('empresa-1');
    assert.equal(created.length, 1);
    emit(created[0], 'connection.update', { connection: 'open' });
    await sleep(20);
    assert.equal(creds.registered, true);
    assert.equal(saved, 1);
    assert.equal(manager.status('empresa-1').state, STATES.CONNECTED);
    await manager.shutdownAll();
  });

  it('2. restart: sessao salva registered=true novo SessionManager initialize cria socket automaticamente', async () => {
    // Simula persistencia compartilhada entre dois managers
    const sharedCreds = { registered: true };
    let storeLoadCalls = 0;
    const auth = { state: { creds: sharedCreds }, saveCreds: async () => {} };
    const store = {
      load: async () => { storeLoadCalls += 1; return auth; },
      clear: async () => {},
      listCompanies: async () => ['empresa-restart'],
      listPersistedCompanies: async () => ['empresa-restart'],
      hasRegisteredSession: async () => true,
    };
    const created1 = [];
    const manager1 = new SessionManager({
      authStore: store,
      createSocket: () => { const s = makeSock(); created1.push(s); return s; },
      log: { log: () => {}, info: () => {}, warn: () => {}, error: () => {} },
    });
    await manager1.initialize();
    assert.equal(created1.length, 1);
    await manager1.shutdownAll();

    // Novo manager (restart)
    const created2 = [];
    const manager2 = new SessionManager({
      authStore: store,
      createSocket: () => { const s = makeSock(); created2.push(s); return s; },
      log: { log: () => {}, info: () => {}, warn: () => {}, error: () => {} },
    });
    await manager2.initialize();
    assert.equal(created2.length, 1);
    assert.equal(manager2.status('empresa-restart').state, STATES.CONNECTING);
    await manager2.shutdownAll();
  });

  it('3. legacy registered=false initialize tenta restore e se open vira registered=true', async () => {
    const creds = { registered: false };
    let saved = 0;
    const auth = { state: { creds }, saveCreds: async () => { saved += 1; } };
    const store = {
      load: async () => auth,
      clear: async () => {},
      listCompanies: async () => [],
      listPersistedCompanies: async () => ['empresa-legacy'],
      hasRegisteredSession: async () => false,
    };
    const created = [];
    const manager = new SessionManager({
      authStore: store,
      createSocket: () => { const s = makeSock(); created.push(s); return s; },
      log: { log: () => {}, info: () => {}, warn: () => {}, error: () => {} },
    });
    await manager.initialize();
    assert.equal(created.length, 1, 'deve tentar restore legacy');
    emit(created[0], 'connection.update', { connection: 'open' });
    await sleep(20);
    assert.equal(creds.registered, true);
    assert.equal(saved, 1);
    assert.equal(manager.status('empresa-legacy').state, STATES.CONNECTED);
    await manager.shutdownAll();
  });

  it('4. seguranca legacy: se tentativa automatica produzir QR, QR NAO fica disponivel e registered NAO vira true', async () => {
    const creds = { registered: false };
    const auth = { state: { creds }, saveCreds: async () => { assert.fail('nao deve chamar saveCreds em QR legacy'); } };
    const store = {
      load: async () => auth,
      clear: async () => {},
      listCompanies: async () => [],
      listPersistedCompanies: async () => ['empresa-legacy-qr'],
      hasRegisteredSession: async () => false,
    };
    const created = [];
    const manager = new SessionManager({
      authStore: store,
      createSocket: () => { const s = makeSock(); created.push(s); return s; },
      log: { log: () => {}, info: () => {}, warn: () => {}, error: () => {} },
    });
    await manager.initialize();
    assert.equal(created.length, 1);
    emit(created[0], 'connection.update', { qr: 'QR-LEGACY' });
    await sleep(20);
    assert.equal(manager.getQr('empresa-legacy-qr'), null, 'QR deve ser suprimido');
    assert.equal(creds.registered, false, 'registered nao deve virar true');
    assert.equal(manager.status('empresa-legacy-qr').state, STATES.DISCONNECTED);
    await manager.shutdownAll();
  });

  it('5. logout/401 ainda nao auto-reconectam', async () => {
    const Boom = require('@hapi/boom');
    const creds = { registered: true };
    const auth = { state: { creds }, saveCreds: async () => {} };
    let cleared = [];
    const store = {
      load: async () => auth,
      clear: async (id) => { cleared.push(id); },
      listCompanies: async () => [],
      listPersistedCompanies: async () => [],
      hasRegisteredSession: async () => true,
    };
    const created = [];
    const manager = new SessionManager({
      authStore: store,
      createSocket: () => { const s = makeSock(); created.push(s); return s; },
      baseDelayMs: 10, maxDelayMs: 20, maxAttempts: 2,
      log: { log: () => {}, info: () => {}, warn: () => {}, error: () => {} },
    });
    await manager.connect('empresa-logout');
    emit(created[0], 'connection.update', { connection: 'open' });
    await sleep(10);
    await manager.logout('empresa-logout');
    assert.equal(manager.status('empresa-logout').state, STATES.LOGGED_OUT);
    assert.deepEqual(cleared, ['empresa-logout']);
    // 401
    const created2 = [];
    const manager2 = new SessionManager({
      authStore: { load: async () => auth, clear: async (id) => { cleared.push(id); }, listCompanies: async () => [], listPersistedCompanies: async () => [] },
      createSocket: () => { const s = makeSock(); created2.push(s); return s; },
      baseDelayMs: 10, maxDelayMs: 20, maxAttempts: 2,
      log: { log: () => {}, info: () => {}, warn: () => {}, error: () => {} },
    });
    await manager2.connect('empresa-401');
    emit(created2[0], 'connection.update', { connection: 'close', lastDisconnect: { error: Boom.boomify(new Error('x'), { statusCode: 401 }) } });
    await sleep(30);
    assert.equal(manager2.status('empresa-401').state, STATES.LOGGED_OUT);
    assert.equal(created2.length, 1, 'nao deve reconectar apos 401');
    await manager.shutdownAll();
    await manager2.shutdownAll();
  });

  it('6. initialize repetido nao duplica sockets', async () => {
    const creds = { registered: true };
    const auth = { state: { creds }, saveCreds: async () => {} };
    const store = {
      load: async () => auth,
      clear: async () => {},
      listCompanies: async () => ['empresa-dedup'],
      listPersistedCompanies: async () => ['empresa-dedup'],
    };
    const created = [];
    const manager = new SessionManager({
      authStore: store,
      createSocket: () => { const s = makeSock(); created.push(s); return s; },
      log: { log: () => {}, info: () => {}, warn: () => {}, error: () => {} },
    });
    await manager.initialize();
    await manager.initialize();
    assert.equal(created.length, 1);
    await manager.shutdownAll();
  });
});
