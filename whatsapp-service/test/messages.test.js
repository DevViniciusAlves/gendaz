'use strict';

// Token fixo SOMENTE para este processo de teste (config e lida no require).
process.env.WHATSAPP_INTERNAL_TOKEN = 'test-token-para-testes';

const { describe, it, before, after } = require('node:test');
const assert = require('node:assert/strict');
const http = require('http');
const { createApp } = require('../src/app');
const { MessageSender } = require('../src/whatsapp/messageSender');
const { SessionManager } = require('../src/whatsapp/sessionManager');

const TOKEN = 'test-token-para-testes';
const RECIPIENT = '5511999999999';
const TEXT = 'Mensagem de teste';
const REQUEST_ID = 'chave-idempotente-1';

function silentLog() {
  return { log: () => {}, warn: () => {}, error: () => {} };
}

function postText(base, body, headers = {}) {
  return fetch(`${base}/internal/whatsapp/sessions/empresa-1/messages/text`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json', ...headers },
    body: typeof body === 'string' ? body : JSON.stringify(body),
  });
}

describe('messages/text (contrato HTTP)', () => {
  let server;
  let base;
  let calls;
  let behavior;

  before(async () => {
    calls = [];
    behavior = { mode: 'ok' };
    const sender = new MessageSender({
      sendFn: async ({ companyId, recipient, text }) => {
        calls.push({ companyId, recipient, text });
        if (behavior.mode === 'fail') {
          throw new Error('boom-send');
        }
        if (behavior.mode === 'not-connected') {
          throw Object.assign(new Error('session_not_connected'), {
            code: 'session_not_connected',
            state: 'DISCONNECTED',
          });
        }
        return 'BAILEYS-ID-1';
      },
      log: silentLog(),
    });
    server = http.createServer(createApp({ sessions: {}, messageSender: sender }));
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    base = `http://127.0.0.1:${server.address().port}`;
  });

  after(async () => {
    await new Promise((resolve) => server.close(resolve));
  });

  it('sem Bearer -> 401', async () => {
    const res = await fetch(`${base}/internal/whatsapp/sessions/empresa-1/messages/text`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ recipient: RECIPIENT, text: TEXT, requestId: REQUEST_ID }),
    });
    assert.equal(res.status, 401);
    assert.deepEqual(await res.json(), { error: 'unauthorized' });
  });

  it('token invalido -> 401', async () => {
    const res = await postText(base, { recipient: RECIPIENT, text: TEXT, requestId: REQUEST_ID }, {
      Authorization: 'Bearer errado',
    });
    assert.equal(res.status, 401);
  });

  it('json invalido -> 400 invalid_json', async () => {
    const res = await postText(base, '{nao-json');
    assert.equal(res.status, 400);
    assert.deepEqual(await res.json(), { error: 'invalid_json' });
  });

  it('recipient invalido -> 400 invalid_recipient', async () => {
    for (const recipient of ['abc', '1234567', '1234567890123456', '', null, 5511999999999]) {
      const res = await postText(base, { recipient, text: TEXT, requestId: `r-${Date.now()}-${recipient}` });
      assert.equal(res.status, 400, `recipient=${recipient} deveria ser 400`);
      assert.deepEqual(await res.json(), { error: 'invalid_recipient' });
    }
  });

  it('text vazio -> 400 invalid_message', async () => {
    for (const text of ['', '   ', null]) {
      const res = await postText(base, { recipient: RECIPIENT, text, requestId: `t-${Date.now()}-${text}` });
      assert.equal(res.status, 400);
      assert.deepEqual(await res.json(), { error: 'invalid_message' });
    }
  });

  it('text >4096 -> 400 invalid_message', async () => {
    const res = await postText(base, { recipient: RECIPIENT, text: 'x'.repeat(4097), requestId: 'longo-1' });
    assert.equal(res.status, 400);
    assert.deepEqual(await res.json(), { error: 'invalid_message' });
  });

  it('requestId invalido -> 400 invalid_request_id', async () => {
    for (const requestId of ['', '   ', null, 'x'.repeat(121)]) {
      const res = await postText(base, { recipient: RECIPIENT, text: TEXT, requestId });
      assert.equal(res.status, 400);
      assert.deepEqual(await res.json(), { error: 'invalid_request_id' });
    }
  });

  it('corpo gigante -> 413 sem travar', async () => {
    const res = await postText(base, { recipient: RECIPIENT, text: 'y'.repeat(20000), requestId: 'gigante-1' });
    assert.equal(res.status, 413);
    assert.deepEqual(await res.json(), { error: 'invalid_message' });
  });

  it('sessao nao conectada -> 409 session_not_connected', async () => {
    behavior.mode = 'not-connected';
    try {
      const res = await postText(base, { recipient: RECIPIENT, text: TEXT, requestId: 'nc-1' });
      assert.equal(res.status, 409);
      const body = await res.json();
      assert.equal(body.error, 'session_not_connected');
      assert.equal(body.state, 'DISCONNECTED');
    } finally {
      behavior.mode = 'ok';
    }
  });

  it('sucesso retorna message.key.id', async () => {
    const res = await postText(base, { recipient: RECIPIENT, text: TEXT, requestId: 'ok-1' });
    assert.equal(res.status, 200);
    assert.deepEqual(await res.json(), { status: 'sent', messageId: 'BAILEYS-ID-1', requestId: 'ok-1' });
  });

  it('erro do sendMessage -> 500 provider_send_failed sem vazar nada', async () => {
    behavior.mode = 'fail';
    try {
      const res = await postText(base, { recipient: RECIPIENT, text: TEXT, requestId: 'fail-1' });
      assert.equal(res.status, 500);
      assert.deepEqual(await res.json(), { error: 'provider_send_failed' });
    } finally {
      behavior.mode = 'ok';
    }
  });

  it('respostas de erro nao vazam dados sensiveis', async () => {
    const bodies = [];
    bodies.push(await (await postText(base, '{ops')).text());
    bodies.push(await (await postText(base, { recipient: 'xx', text: TEXT, requestId: 's1' })).text());
    bodies.push(await (await postText(base, { recipient: RECIPIENT, text: '', requestId: 's2' })).text());
    bodies.push(await (await postText(base, { recipient: RECIPIENT, text: TEXT, requestId: '' })).text());
    const all = bodies.join('\n').toLowerCase();
    for (const forbidden of [RECIPIENT.toLowerCase(), TEXT.toLowerCase(), 's.whatsapp.net', 'signal', 'token', 'stack', TOKEN]) {
      assert.ok(!all.includes(forbidden), `resposta contem dado sensivel: ${forbidden}`);
    }
  });
});

describe('messages/text (Baileys wiring)', () => {
  let server;
  let base;
  let sent;
  let manager;

  function fakeSock() {
    return {
      sentCalls: [],
      ev: { on: () => {} },
      end: async () => {},
      logout: async () => {},
      sendMessage: async (jid, content) => {
        sent.push({ jid, content });
        return { key: { id: 'WAMID-123' } };
      },
    };
  }

  before(async () => {
    sent = [];
    const authStore = {
      load: async () => ({ state: {}, saveCreds: async () => {} }),
      clear: async () => {},
    };
    manager = new SessionManager({
      authStore,
      createSocket: () => fakeSock(),
      log: silentLog(),
    });
    const sender = new MessageSender({
      sendFn: ({ companyId, recipient, text }) => manager.sendText(companyId, recipient, text),
      log: silentLog(),
    });
    server = http.createServer(createApp({ sessions: manager, messageSender: sender }));
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    base = `http://127.0.0.1:${server.address().port}`;
  });

  after(async () => {
    await new Promise((resolve) => server.close(resolve));
  });

  function connectCompany(companyId) {
    const record = manager.getRecord(companyId);
    record.state = 'CONNECTED';
    record.sock = fakeSock();
    return record;
  }

  it('sessao nunca conectada -> 409 com estado', async () => {
    const res = await postText(base, { recipient: RECIPIENT, text: TEXT, requestId: 'w1' });
    assert.equal(res.status, 409);
    const body = await res.json();
    assert.equal(body.error, 'session_not_connected');
    assert.equal(body.state, 'NOT_CONNECTED');
  });

  it('CONNECTED chama exatamente sock.sendMessage(jid, { text })', async () => {
    connectCompany('empresa-1');
    const before = sent.length;
    const res = await postText(base, { recipient: RECIPIENT, text: TEXT, requestId: 'w2' });
    assert.equal(res.status, 200);
    assert.equal(sent.length, before + 1);
    assert.deepEqual(sent[sent.length - 1], {
      jid: `${RECIPIENT}@s.whatsapp.net`,
      content: { text: TEXT },
    });
    assert.deepEqual(await res.json(), { status: 'sent', messageId: 'WAMID-123', requestId: 'w2' });
  });

  it('duas requisicoes concorrentes com mesmo requestId executam um unico send', async () => {
    connectCompany('empresa-1');
    const before = sent.length;
    const payload = { recipient: RECIPIENT, text: TEXT, requestId: 'w-dup' };
    const [r1, r2] = await Promise.all([postText(base, payload), postText(base, payload)]);
    assert.equal(r1.status, 200);
    assert.equal(r2.status, 200);
    assert.deepEqual(await r1.json(), await r2.json());
    assert.equal(sent.length, before + 1);
  });

  it('sucesso repetido dentro do TTL nao reenvia', async () => {
    connectCompany('empresa-1');
    const before = sent.length;
    const payload = { recipient: RECIPIENT, text: TEXT, requestId: 'w-ttl' };
    await postText(base, payload);
    const res = await postText(base, payload);
    assert.equal(res.status, 200);
    assert.equal(sent.length, before + 1);
  });

  it('requestId iguais em empresas diferentes nao se misturam', async () => {
    connectCompany('empresa-1');
    const before = sent.length;
    const headers = { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' };
    const url = (c) => `${base}/internal/whatsapp/sessions/${c}/messages/text`;
    const payload = JSON.stringify({ recipient: RECIPIENT, text: TEXT, requestId: 'w-shared' });
    const r1 = await fetch(url('empresa-1'), { method: 'POST', headers, body: payload });
    // empresa-2 nunca conectou: mesmo requestId nao reaproveita nada, da 409 proprio.
    const r2 = await fetch(url('empresa-2'), { method: 'POST', headers, body: payload });
    assert.equal(r1.status, 200);
    assert.equal(r2.status, 409);
    assert.equal(sent.length, before + 1);
  });
});

describe('MessageSender (unidade)', () => {
  it('serializa por empresa e nao quebra a fila apos falha', async () => {
    const order = [];
    let calls = 0;
    const sender = new MessageSender({
      sendFn: async ({ text }) => {
        const id = text;
        order.push(`start-${id}`);
        await new Promise((resolve) => setTimeout(resolve, 20));
        order.push(`end-${id}`);
        if (++calls === 1) {
          throw new Error('falha-q1');
        }
        return `id-${id}`;
      },
      log: silentLog(),
    });
    const p1 = sender.send({ companyId: 'a', recipient: RECIPIENT, text: 'q1', requestId: 'q1' });
    const p2 = sender.send({ companyId: 'a', recipient: RECIPIENT, text: 'q2', requestId: 'q2' });
    await assert.rejects(p1);
    const r2 = await p2;
    assert.equal(r2.messageId, 'id-q2');
    assert.deepEqual(order, ['start-q1', 'end-q1', 'start-q2', 'end-q2']);
  });

  it('cache expira apos TTL', async () => {
    let n = 0;
    const sender = new MessageSender({
      sendFn: async () => `id-${++n}`,
      ttlMs: 30,
      log: silentLog(),
    });
    const first = await sender.send({ companyId: 'a', recipient: RECIPIENT, text: TEXT, requestId: 'e1' });
    assert.equal(first.deduplicated, false);
    await new Promise((resolve) => setTimeout(resolve, 60));
    const second = await sender.send({ companyId: 'a', recipient: RECIPIENT, text: TEXT, requestId: 'e1' });
    assert.equal(second.deduplicated, false);
    assert.equal(n, 2);
  });

  it('cache respeita limite de tamanho', async () => {
    let n = 0;
    const sender = new MessageSender({
      sendFn: async () => `id-${++n}`,
      maxEntries: 2,
      log: silentLog(),
    });
    const base = { companyId: 'a', recipient: RECIPIENT, text: TEXT };
    await sender.send({ ...base, requestId: 'c1' });
    await sender.send({ ...base, requestId: 'c2' });
    await sender.send({ ...base, requestId: 'c3' }); // expulsa c1
    const again = await sender.send({ ...base, requestId: 'c1' });
    assert.equal(again.deduplicated, false);
    assert.equal(n, 4);
  });

  it('pares ("ab","c") e ("a","bc") geram envios independentes', async () => {
    const seen = [];
    const sender = new MessageSender({
      sendFn: async ({ companyId, text }) => {
        seen.push(`${companyId}:${text}`);
        return `id-${companyId}-${text}`;
      },
      log: silentLog(),
    });
    const base = { recipient: RECIPIENT, text: TEXT };
    // Concorrentes: sem cache/inflight cruzado, sao dois sends.
    const [r1, r2] = await Promise.all([
      sender.send({ ...base, companyId: 'ab', requestId: 'c' }),
      sender.send({ ...base, companyId: 'a', requestId: 'bc' }),
    ]);
    assert.equal(r1.deduplicated, false);
    assert.equal(r2.deduplicated, false);
    assert.equal(seen.length, 2);
    // Repeticao: cada par acerta o proprio cache, sem cruzar.
    const [r3, r4] = await Promise.all([
      sender.send({ ...base, companyId: 'ab', requestId: 'c' }),
      sender.send({ ...base, companyId: 'a', requestId: 'bc' }),
    ]);
    assert.equal(r3.deduplicated, true);
    assert.equal(r4.deduplicated, true);
    assert.equal(r3.messageId, 'id-ab-Mensagem de teste');
    assert.equal(r4.messageId, 'id-a-Mensagem de teste');
    assert.equal(seen.length, 2);
  });
});
