'use strict';

// Token fixo SOMENTE para este processo de teste (config e lida no require).
process.env.WHATSAPP_INTERNAL_TOKEN = 'test-token-para-testes';

const { describe, it, before, after } = require('node:test');
const assert = require('node:assert/strict');
const http = require('http');
const { createApp } = require('../src/app');
const { SessionManager } = require('../src/whatsapp/sessionManager');

const TOKEN = 'test-token-para-testes';

function fakeSessions() {
  const created = [];
  const authStore = {
    load: async () => ({ state: {}, saveCreds: async () => {} }),
    clear: async () => {},
  };
  const manager = new SessionManager({
    authStore,
    createSocket: () => {
      const sock = {
        handlers: {},
        ev: { on: (e, fn) => { ((sock.handlers[e] = sock.handlers[e] || [])).push(fn); } },
        end: async () => {},
        logout: async () => {},
      };
      created.push(sock);
      return sock;
    },
    baseDelayMs: 10,
    maxDelayMs: 40,
    maxAttempts: 2,
    log: { log: () => {}, warn: () => {}, error: () => {} },
  });
  return manager;
}

describe('HTTP', () => {
  let server;
  let base;

  before(async () => {
    server = http.createServer(createApp({ sessions: fakeSessions() }));
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    base = `http://127.0.0.1:${server.address().port}`;
  });

  after(async () => {
    await new Promise((resolve) => server.close(resolve));
  });

  it('/health continua 200 sem token', async () => {
    const res = await fetch(`${base}/health`);
    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.status, 'ok');
  });

  it('endpoint interno sem token -> 401', async () => {
    const res = await fetch(`${base}/internal/whatsapp/sessions/empresa-1/status`);
    assert.equal(res.status, 401);
  });

  it('endpoint interno com token errado -> 401', async () => {
    const res = await fetch(`${base}/internal/whatsapp/sessions/empresa-1/status`, {
      headers: { Authorization: 'Bearer token-errado' },
    });
    assert.equal(res.status, 401);
  });

  it('endpoint interno com token correto -> funciona', async () => {
    const headers = { Authorization: `Bearer ${TOKEN}` };
    const connect = await fetch(`${base}/internal/whatsapp/sessions/empresa-1/connect`, { method: 'POST', headers });
    assert.equal(connect.status, 200);
    const status = await (await fetch(`${base}/internal/whatsapp/sessions/empresa-1/status`, { headers })).json();
    assert.equal(status.companyId, 'empresa-1');
    assert.equal(status.state, 'CONNECTING');
  });

  it('companyId invalido -> 400', async () => {
    const headers = { Authorization: `Bearer ${TOKEN}` };
    const res = await fetch(`${base}/internal/whatsapp/sessions/a.b/status`, { headers });
    assert.equal(res.status, 400);
    assert.deepEqual(await res.json(), { error: 'invalid_company_id' });
  });

  it('qr indisponivel -> 404 com estado, sem dados sensiveis', async () => {
    const headers = { Authorization: `Bearer ${TOKEN}` };
    const res = await fetch(`${base}/internal/whatsapp/sessions/empresa-qr/qr`, { headers });
    assert.equal(res.status, 404);
    const body = await res.json();
    assert.equal(body.error, 'qr_unavailable');
    assert.ok(body.state);
  });

  it('nao existe endpoint de envio', async () => {
    const headers = { Authorization: `Bearer ${TOKEN}` };
    for (const [method, path] of [
      ['POST', '/internal/whatsapp/send'],
      ['POST', '/internal/whatsapp/sessions/empresa-1/send'],
      ['GET', '/send'],
    ]) {
      const res = await fetch(`${base}${path}`, { method, headers });
      assert.equal(res.status, 404, `${method} ${path} deveria ser 404`);
    }
  });

  it('respostas nao vazam dados sensiveis', async () => {
    const headers = { Authorization: `Bearer ${TOKEN}` };
    const bodies = [];
    bodies.push(await (await fetch(`${base}/health`)).text());
    bodies.push(await (await fetch(`${base}/internal/whatsapp/sessions/empresa-1/status`, { headers })).text());
    bodies.push(await (await fetch(`${base}/internal/whatsapp/sessions/empresa-1/connect`, { method: 'POST', headers })).text());
    bodies.push(await (await fetch(`${base}/internal/whatsapp/sessions/empresa-1/qr`, { headers })).text());
    const all = bodies.join('\n').toLowerCase();
    for (const forbidden of ['creds', 'signal', 'token', 'secret', 'private', 'stack', 'bearer', TOKEN]) {
      assert.ok(!all.includes(forbidden), `resposta contem dado sensivel: ${forbidden}`);
    }
  });

  it('logout via HTTP desvincula', async () => {
    const headers = { Authorization: `Bearer ${TOKEN}` };
    const res = await fetch(`${base}/internal/whatsapp/sessions/empresa-1/logout`, { method: 'POST', headers });
    assert.equal(res.status, 200);
    assert.equal((await res.json()).state, 'LOGGED_OUT');
  });
});
