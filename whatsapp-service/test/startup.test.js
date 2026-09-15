'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { bootstrap } = require('../src/server');
const { SessionManager } = require('../src/whatsapp/sessionManager');

function fakeSocketFactory(created) {
  return () => {
    const sock = {
      handlers: {},
      ev: { on: (e, fn) => { ((sock.handlers[e] = sock.handlers[e] || [])).push(fn); } },
      end: async () => {},
      logout: async () => {},
    };
    created.push(sock);
    return sock;
  };
}

function managerWithCompanies(companies, created) {
  const store = {
    load: async () => ({ state: { creds: { registered: true } }, saveCreds: async () => {} }),
    clear: async () => {},
    listCompanies: async () => companies,
  };
  return new SessionManager({
    authStore: store,
    createSocket: fakeSocketFactory(created),
    baseDelayMs: 10,
    maxDelayMs: 40,
    maxAttempts: 3,
    log: { log: () => {}, info: () => {}, warn: () => {}, error: () => {} },
  });
}

describe('bootstrap', () => {
  it('aguarda initialize terminar antes do listen', async () => {
    const ordem = [];
    let liberarInit;
    const initGate = new Promise((resolve) => { liberarInit = resolve; });
    const sessions = {
      initialize: async () => {
        ordem.push('init-inicio');
        await initGate;
        ordem.push('init-fim');
      },
    };
    const app = (req, res) => res.end('ok');
    const prometido = bootstrap({
      sessions,
      app,
      port: 0,
      listen: async (server) => {
        ordem.push('listen');
        server.close();
      },
    });
    await new Promise((resolve) => setTimeout(resolve, 20));
    assert.deepEqual(ordem, ['init-inicio']);
    liberarInit();
    await prometido;
    assert.deepEqual(ordem, ['init-inicio', 'init-fim', 'listen']);
  });

  it('apos bootstrap, sessoes restauradas sem estado falso', async () => {
    const created = [];
    const sessions = managerWithCompanies(['empresa-a', 'empresa-b'], created);
    const app = (req, res) => res.end('ok');
    const server = await bootstrap({
      sessions,
      app,
      port: 0,
      listen: async (srv) => { srv.close(); },
    });
    assert.ok(server);
    assert.equal(created.length, 2);
    assert.equal(sessions.status('empresa-a').state, 'CONNECTING');
    assert.equal(sessions.status('empresa-b').state, 'CONNECTING');
    assert.equal(sessions.getQr('empresa-a'), null);
    await sessions.shutdownAll();
  });

  it('falha de restore em A nao mistura estado com B', async () => {
    const created = [];
    const store = {
      load: async (companyId) => {
        if (companyId === 'empresa-a') {
          throw new Error('auth-corrompido');
        }
        return { state: { creds: { registered: true } }, saveCreds: async () => {} };
      },
      clear: async () => {},
      listCompanies: async () => ['empresa-a', 'empresa-b'],
    };
    const sessions = new SessionManager({
      authStore: store,
      createSocket: fakeSocketFactory(created),
      log: { log: () => {}, info: () => {}, warn: () => {}, error: () => {} },
    });
    const app = (req, res) => res.end('ok');
    await bootstrap({ sessions, app, port: 0, listen: async (srv) => { srv.close(); } });
    assert.equal(created.length, 1);
    assert.equal(sessions.status('empresa-a').state, 'DISCONNECTED');
    assert.equal(sessions.status('empresa-b').state, 'CONNECTING');
    await sessions.shutdownAll();
  });

  it('erro inesperado no initialize impede abrir a porta', async () => {
    let listenChamado = false;
    const sessions = {
      initialize: async () => { throw new Error('falha-infra'); },
    };
    await assert.rejects(
      bootstrap({
        sessions,
        app: (req, res) => res.end('ok'),
        port: 0,
        listen: async (server) => { listenChamado = true; server.close(); },
      }),
      /falha-infra/
    );
    assert.equal(listenChamado, false);
  });

  it('initialize e idempotente e nao duplica sockets', async () => {
    const created = [];
    const sessions = managerWithCompanies(['empresa-a'], created);
    await sessions.initialize();
    await sessions.initialize();
    assert.equal(created.length, 1);
    await sessions.shutdownAll();
  });
});
