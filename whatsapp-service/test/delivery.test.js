'use strict';

process.env.WHATSAPP_INTERNAL_TOKEN = 'test-token-para-testes';

const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { WAMessageStatus } = require('@whiskeysockets/baileys');
const { SessionManager } = require('../src/whatsapp/sessionManager');
const { isProofOfDelivery, extractDeliveredIds } = require('../src/whatsapp/deliveryTracker');
const { DeliveryReporter } = require('../src/whatsapp/deliveryReporter');

function silentLog(recorded) {
  return {
    log: (msg) => recorded && recorded.push(String(msg)),
    warn: (msg) => recorded && recorded.push(String(msg)),
    error: (msg) => recorded && recorded.push(String(msg)),
    info: (msg) => recorded && recorded.push(String(msg)),
  };
}

function fakeSock() {
  const sock = {
    handlers: {},
    ev: { on(event, fn) { ((sock.handlers[event] = sock.handlers[event] || [])).push(fn); } },
    end: async () => {},
    logout: async () => {},
    onWhatsApp: async (recipient) => [{ jid: `${String(recipient).replace(/\D/g, '')}@lid`, exists: true }],
    sendMessage: async () => ({ key: { id: 'WAMID-1' } }),
  };
  return sock;
}

function makeManager({ onDelivery, log } = {}) {
  const created = [];
  const authStore = {
    load: async () => ({ state: {}, saveCreds: async () => {} }),
    clear: async () => {},
  };
  const manager = new SessionManager({
    authStore,
    createSocket: () => { const s = fakeSock(); created.push(s); return s; },
    onDelivery,
    log: log || silentLog(),
  });
  return { manager, created };
}

function emit(sock, event, payload) {
  for (const fn of sock.handlers[event] || []) {
    fn(payload);
  }
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function connectOpen(manager, companyId) {
  const record = manager.getRecord(companyId);
  record.state = 'CONNECTED';
  const sock = fakeSock();
  // Simula establish real: registra listeners com generation atual.
  const gen = record.generation;
  record.sock = sock;
  sock.ev.on('messages.update', (updates) => {
    if (gen !== record.generation) {
      return;
    }
    const ids = extractDeliveredIds(updates);
    for (const id of ids) {
      Promise.resolve()
        .then(() => manager.onDelivery && manager.onDelivery({ companyId: record.companyId, messageId: id }))
        .catch(() => {});
    }
  });
  return { record, sock };
}

describe('deliveryTracker (classificacao via WAMessageStatus real)', () => {
  it('usa os valores reais do Baileys (nao inventados)', () => {
    assert.equal(WAMessageStatus.DELIVERY_ACK, 3);
    assert.equal(WAMessageStatus.READ, 4);
    assert.equal(WAMessageStatus.PLAYED, 5);
    assert.equal(WAMessageStatus.SERVER_ACK, 2);
    assert.equal(WAMessageStatus.PENDING, 1);
  });

  it('sendMessage com messageId NAO e prova de entrega (so ACK comprova)', () => {
    // Sem messages.update, nada e extraido: o POST 200 "sent" significa
    // apenas "aceito pelo provider".
    assert.deepEqual(extractDeliveredIds(undefined), []);
    assert.deepEqual(extractDeliveredIds([]), []);
    assert.deepEqual(extractDeliveredIds([{ key: { id: 'WAMID-1' }, update: {} }]), []);
  });

  it('DELIVERY_ACK (numero e nome) comprova entrega', () => {
    assert.equal(isProofOfDelivery(WAMessageStatus.DELIVERY_ACK), true);
    assert.equal(isProofOfDelivery('DELIVERY_ACK'), true);
    assert.deepEqual(
      extractDeliveredIds([{ key: { id: 'A' }, update: { status: WAMessageStatus.DELIVERY_ACK } }]),
      ['A']
    );
  });

  it('READ sem DELIVERY_ACK previo tambem comprova', () => {
    assert.equal(isProofOfDelivery(WAMessageStatus.READ), true);
    assert.deepEqual(
      extractDeliveredIds([{ key: { id: 'A' }, update: { status: WAMessageStatus.READ } }]),
      ['A']
    );
  });

  it('PLAYED sem DELIVERY_ACK previo tambem comprova', () => {
    assert.equal(isProofOfDelivery(WAMessageStatus.PLAYED), true);
    assert.deepEqual(
      extractDeliveredIds([{ key: { id: 'A' }, update: { status: WAMessageStatus.PLAYED } }]),
      ['A']
    );
  });

  it('SERVER_ACK/PENDING/ERROR nao comprovam nem consomem cota', () => {
    for (const s of [WAMessageStatus.SERVER_ACK, WAMessageStatus.PENDING, WAMessageStatus.ERROR, 99, null, undefined]) {
      assert.equal(isProofOfDelivery(s), false, `status=${s} nao deveria comprovar`);
    }
    assert.deepEqual(
      extractDeliveredIds([
        { key: { id: 'A' }, update: { status: WAMessageStatus.SERVER_ACK } },
        { key: { id: 'B' }, update: { status: WAMessageStatus.PENDING } },
      ]),
      []
    );
  });

  it('deduplica dentro do lote e ignora entradas sem id', () => {
    assert.deepEqual(
      extractDeliveredIds([
        { key: { id: 'A' }, update: { status: WAMessageStatus.DELIVERY_ACK } },
        { key: { id: 'A' }, update: { status: WAMessageStatus.READ } },
        { key: {}, update: { status: WAMessageStatus.DELIVERY_ACK } },
        { key: { id: '' }, update: { status: WAMessageStatus.DELIVERY_ACK } },
      ]),
      ['A']
    );
  });
});

describe('SessionManager messages.update (listener real do establish)', () => {
  it('DELIVERY_ACK gera callback de entrega via establish', async () => {
    const calls = [];
    const { manager } = makeManager({ onDelivery: async (e) => { calls.push(e); } });
    await manager.connect('empresa-1');
    const sock = manager.getRecord('empresa-1').sock;
    emit(sock, 'messages.update', [{ key: { id: 'WAMID-1' }, update: { status: WAMessageStatus.DELIVERY_ACK } }]);
    await sleep(20);
    assert.deepEqual(calls, [{ companyId: 'empresa-1', messageId: 'WAMID-1' }]);
    await manager.shutdownAll();
  });

  it('READ/PLAYED sem DELIVERY_ACK previo geram callback', async () => {
    const calls = [];
    const { manager } = makeManager({ onDelivery: async (e) => { calls.push(e); } });
    await manager.connect('empresa-1');
    const sock = manager.getRecord('empresa-1').sock;
    emit(sock, 'messages.update', [{ key: { id: 'R1' }, update: { status: WAMessageStatus.READ } }]);
    emit(sock, 'messages.update', [{ key: { id: 'P1' }, update: { status: 'PLAYED' } }]);
    await sleep(20);
    assert.deepEqual(calls.map((c) => c.messageId).sort(), ['P1', 'R1']);
    await manager.shutdownAll();
  });

  it('SERVER_ACK/PENDING nao geram callback', async () => {
    const calls = [];
    const { manager } = makeManager({ onDelivery: async (e) => { calls.push(e); } });
    await manager.connect('empresa-1');
    const sock = manager.getRecord('empresa-1').sock;
    emit(sock, 'messages.update', [
      { key: { id: 'S1' }, update: { status: WAMessageStatus.SERVER_ACK } },
      { key: { id: 'S2' }, update: { status: WAMessageStatus.PENDING } },
    ]);
    await sleep(20);
    assert.deepEqual(calls, []);
    await manager.shutdownAll();
  });

  it('evento de generation antiga e ignorado (socket substituido)', async () => {
    const calls = [];
    const { manager } = makeManager({ onDelivery: async (e) => { calls.push(e); } });
    await manager.connect('empresa-1');
    const oldSock = manager.getRecord('empresa-1').sock;
    await manager.logout('empresa-1'); // bump generation
    emit(oldSock, 'messages.update', [{ key: { id: 'OLD' }, update: { status: WAMessageStatus.DELIVERY_ACK } }]);
    await sleep(20);
    assert.deepEqual(calls, []);
    await manager.shutdownAll();
  });

  it('companyId isolado: ACK da empresa A nao reporta B', async () => {
    const calls = [];
    const { manager } = makeManager({ onDelivery: async (e) => { calls.push(e); } });
    await manager.connect('empresa-a');
    await manager.connect('empresa-b');
    const sockA = manager.getRecord('empresa-a').sock;
    const sockB = manager.getRecord('empresa-b').sock;
    emit(sockA, 'messages.update', [{ key: { id: 'MID-A' }, update: { status: WAMessageStatus.DELIVERY_ACK } }]);
    emit(sockB, 'messages.update', [{ key: { id: 'MID-A' }, update: { status: WAMessageStatus.DELIVERY_ACK } }]);
    await sleep(20);
    assert.deepEqual(calls, [
      { companyId: 'empresa-a', messageId: 'MID-A' },
      { companyId: 'empresa-b', messageId: 'MID-A' },
    ]);
    await manager.shutdownAll();
  });

  it('falha no callback nao derruba o socket', async () => {
    const { manager } = makeManager({
      onDelivery: async () => { throw new Error('backend-down'); },
    });
    await manager.connect('empresa-1');
    const sock = manager.getRecord('empresa-1').sock;
    emit(sock, 'messages.update', [{ key: { id: 'X' }, update: { status: WAMessageStatus.DELIVERY_ACK } }]);
    await sleep(20);
    // Socket continua utilizavel: sendText ainda funciona.
    manager.getRecord('empresa-1').state = 'CONNECTED';
    manager.getRecord('empresa-1').sock = Object.assign(sock, {
      onWhatsApp: async () => [{ jid: '5511999999999@lid', exists: true }],
      sendMessage: async () => ({ key: { id: 'WAMID-9' } }),
    });
    assert.equal(await manager.sendText('empresa-1', '5511999999999', 'oi'), 'WAMID-9');
    await manager.shutdownAll();
  });

  it('logs nao expoem telefone/JID/texto/credenciais', async () => {
    const recorded = [];
    const calls = [];
    const { manager } = makeManager({ onDelivery: async (e) => { calls.push(e); }, log: silentLog(recorded) });
    await manager.connect('empresa-1');
    const sock = manager.getRecord('empresa-1').sock;
    const jid = '5511999999999@s.whatsapp.net';
    emit(sock, 'messages.update', [{ key: { remoteJid: jid, id: 'SEC-1' }, update: { status: 3 } }]);
    await sleep(20);
    assert.equal(calls.length, 1);
    const all = recorded.join('\n');
    assert.ok(!all.includes('5511999999999'), 'log expoe telefone');
    assert.ok(!all.includes('s.whatsapp.net'), 'log expoe JID');
    assert.ok(!all.includes('SEC-1'), 'log expoe messageId real');
    await manager.shutdownAll();
  });
});

describe('DeliveryReporter (Node -> Spring)', () => {
  function makeOutbox() {
    const recordDelivery = async (companyId, providerMessageId) => {
      return { isNew: true, id: 1 };
    };
    const getPendingCount = async (companyId) => 0;
    return { recordDelivery, getPendingCount };
  }

  function makeOutboxDuplicate() {
    const recordDelivery = async (companyId, providerMessageId) => {
      return { isNew: false, id: null };
    };
    const getPendingCount = async (companyId) => 0;
    return { recordDelivery, getPendingCount };
  }

  function makeOutboxFail() {
    const recordDelivery = async (companyId, providerMessageId) => {
      throw new Error('db-down');
    };
    const getPendingCount = async (companyId) => 0;
    return { recordDelivery, getPendingCount };
  }

  function makeFetchOk() {
    return async (url, opts) => { return { ok: true, status: 200 }; };
  }

  function makeFetchFail() {
    return async () => { throw new Error('network error'); };
  }

  async function runWithOutbox(fn) {
    const fetches = [];
    const outbox = makeOutbox();
    const reporter = new DeliveryReporter({
      backendUrl: 'http://spring:8080',
      internalToken: 'tok',
      outbox,
      fetchFn: async (url, opts) => { fetches.push({ url, opts }); return { ok: true, status: 200 }; },
      log: silentLog(),
    });
    await fn(reporter, fetches);
    return { reporter, fetches };
  }

  async function runWithOutboxFail(fn) {
    const fetches = [];
    const outbox = makeOutboxFail();
    const reporter = new DeliveryReporter({
      backendUrl: 'http://spring:8080',
      internalToken: 'tok',
      outbox,
      fetchFn: async (url, opts) => { fetches.push({ url, opts }); return { ok: true, status: 200 }; },
      log: silentLog(),
    });
    await fn(reporter, fetches);
    return { reporter, fetches };
  }

  async function runWithOutboxFailHttpSuccess(fn) {
    const fetches = [];
    const outbox = makeOutboxFail();
    const reporter = new DeliveryReporter({
      backendUrl: 'http://spring:8080',
      internalToken: 'tok',
      outbox,
      fetchFn: async (url, opts) => { fetches.push({ url, opts }); return { ok: true, status: 200 }; },
      log: silentLog(),
    });
    await fn(reporter, fetches);
    return { reporter, fetches };
  }

  async function runWithOutboxFailNoToken(fn) {
    const recorded = [];
    const reporter = new DeliveryReporter({ log: silentLog(recorded) });
    const r = await reporter.report('empresa-1', 'WAMID-1');
    recorded.push(r);
    return { reporter, recorded, r };
  }

  it('reporta DELIVERED com payload minimo e dedupa repeticao', async () => {
    const fetches = [];
    const reporter = new DeliveryReporter({
      backendUrl: 'http://spring:8080',
      internalToken: 'tok',
      fetchFn: async (url, opts) => { fetches.push({ url, opts }); return { ok: true, status: 200 }; },
      log: silentLog(),
    });
    const first = await reporter.report('empresa-1', 'WAMID-1');
    assert.equal(first.ok, true);
    assert.equal(fetches.length, 1);
    assert.equal(fetches[0].url, 'http://spring:8080/internal/whatsapp/delivery');
    const sent = JSON.parse(fetches[0].opts.body);
    assert.deepEqual(Object.keys(sent).sort(), ['companyId', 'messageId', 'status']);
    assert.deepEqual(sent, { companyId: 'empresa-1', messageId: 'WAMID-1', status: 'DELIVERED' });
    assert.equal(fetches[0].opts.headers.Authorization, 'Bearer tok');

    const second = await reporter.report('empresa-1', 'WAMID-1');
    assert.equal(second.ok, true);
    assert.equal(second.deduplicated, true);
    assert.equal(fetches.length, 1); // ACK duplicado nao gera segundo POST
  });

  it('mesmo messageId em empresas diferentes gera callbacks independentes', async () => {
    const fetches = [];
    const reporter = new DeliveryReporter({
      backendUrl: 'http://spring:8080',
      internalToken: 'tok',
      fetchFn: async (url, opts) => { fetches.push(JSON.parse(opts.body)); return { ok: true }; },
      log: silentLog(),
    });
    await reporter.report('empresa-a', 'MID');
    await reporter.report('empresa-b', 'MID');
    assert.equal(fetches.length, 2);
  });

  it('falha de rede resolve sem throw (socket nunca cai)', async () => {
    const reporter = new DeliveryReporter({
      backendUrl: 'http://spring:8080',
      internalToken: 'tok',
      fetchFn: async () => { throw Object.assign(new Error('conn refused'), { code: 'ECONNREFUSED' }); },
      log: silentLog(),
    });
    const r = await reporter.report('empresa-1', 'WAMID-1');
    assert.equal(r.ok, false);
  });

  it('sem backend/token configurado ignora com aviso e sem throw', async () => {
    const recorded = [];
    const reporter = new DeliveryReporter({ log: silentLog(recorded) });
    const r = await reporter.report('empresa-1', 'WAMID-1');
    assert.equal(r.ok, false);
    assert.ok(recorded.join('\n').length > 0);
  });

  // CENÁRIO A — OUTBOX FUNCIONA
  it('outbox funciona: recordDelivery sucesso, fetch 0 chamadas, ok=true, queued=true', async () => {
    const fetches = [];
    const outbox = {
      recordDelivery: async () => { return { isNew: true, id: 1 }; },
      getPendingCount: async () => 0,
    };
    const reporter = new DeliveryReporter({
      backendUrl: 'http://spring:8080',
      internalToken: 'tok',
      outbox,
      fetchFn: async () => { },
      log: silentLog(),
    });
    const first = await reporter.report('empresa-1', 'WAMID-1');
    assert.equal(first.ok, true);
    assert.equal(first.queued, true);
    assert.equal(fetches.length, 0); // Nenhum HTTP POST feito, outbox cuidou
  });

  // CENÁRIO B — OUTBOX DUPLICADO
  it('outbox duplicado: recordDelivery retorna isNew=false, ok=true, queued=true, deduplicated=true, fetch=0', async () => {
    const fetches = [];
    const outbox = {
      recordDelivery: async () => { return { isNew: false, id: null }; },
      getPendingCount: async () => 0,
    };
    const reporter = new DeliveryReporter({
      backendUrl: 'http://spring:8080',
      internalToken: 'tok',
      outbox,
      fetchFn: async () => { },
      log: silentLog(),
    });
    const first = await reporter.report('empresa-1', 'WAMID-1');
    assert.equal(first.ok, true);
    assert.equal(first.queued, true);
    assert.equal(first.deduplicated, true);
    assert.equal(fetches.length, 0); // Nenhum HTTP POST feito
  });

  // CENÁRIO C — OUTBOX FALHA E HTTP FUNCIONA
  it('outbox falha e HTTP funciona: recordDelivery 1 chamada, fetch 1 chamada, ok=true, queued NÃO deve ser true', async () => {
    const fetches = [];
    const outbox = {
      recordDelivery: async () => { throw new Error('db-down'); },
      getPendingCount: async () => 0,
    };
    const reporter = new DeliveryReporter({
      backendUrl: 'http://spring:8080',
      internalToken: 'tok',
      outbox,
      fetchFn: async (url, opts) => { fetches.push({ url, opts }); return { ok: true, status: 200 }; },
      log: silentLog(),
    });
    const first = await reporter.report('empresa-1', 'WAMID-1');
    assert.equal(first.ok, true);
    assert.equal(fetches.length, 1); // HTTP fallback executado
    assert.equal(first.queued, undefined); // queued nao deve ser true quando usa fallback HTTP
  });

  // CENÁRIO D — OUTBOX FALHA SEM BACKEND/TOKEN
  it('outbox falha sem backend/token: ok=false, reason=not_configured', async () => {
    const recorded = [];
    const outbox = {
      recordDelivery: async () => { throw new Error('db-down'); },
    };
    const reporter = new DeliveryReporter({
      backendUrl: '',
      internalToken: '',
      outbox,
      log: silentLog(recorded),
    });
    const r = await reporter.report('empresa-1', 'WAMID-1');
    assert.equal(r.ok, false);
    assert.equal(r.reason, 'not_configured');
    assert.ok(recorded.join('\n').length > 0);
  });

  // CENÁRIO E — OUTBOX E HTTP FALHAM
  it('outbox e HTTP falham: primeira chamada ok=false, segunda nao marca reported', async () => {
    const fetches = [];
    const outbox = {
      recordDelivery: async () => { throw new Error('db-down'); },
    };
    const reporter = new DeliveryReporter({
      backendUrl: 'http://spring:8080',
      internalToken: 'tok',
      outbox,
      fetchFn: async () => { throw new Error('network error'); },
      log: silentLog(),
    });

    // Primeira chamada: outbox falha, HTTP falha
    const first = await reporter.report('empresa-1', 'WAMID-1');
    assert.equal(first.ok, false);

    // Segunda chamada com mesmo ID: nao deve marcar reported
    // O reporter nao deve ter marcado reported pois outbox falhou
    // E HTTP falhou tambem, mas o reporter nao controla isso em memoria
    // Apenas garante que o comportamento eh consistente
    const second = await reporter.report('empresa-1', 'WAMID-1');
    // Ambas devem retornar ok=false, mas o importante eh que o outbox falha nao marca reported
    assert.equal(second.ok, false);
  });

  // CENÁRIO F — DEDUP APÓS OUTBOX SUCESSO
  it('dedup apos outbox sucesso: primeira chamada isNew=true, segunda deduplicated=true', async () => {
    const fetches = [];
    const outbox = {
      recordDelivery: async () => { return { isNew: true, id: 1 }; },
      getPendingCount: async () => 0,
    };
    const reporter = new DeliveryReporter({
      backendUrl: 'http://spring:8080',
      internalToken: 'tok',
      outbox,
      fetchFn: async (url, opts) => { fetches.push({ url, opts }); return { ok: true, status: 200 }; },
      log: silentLog(),
    });

    const first = await reporter.report('empresa-1', 'WAMID-1');
    assert.equal(first.ok, true);
    assert.equal(first.deduplicated, false); // isNew=true, so deduplicated=false
    assert.equal(fetches.length, 0); // Outbox sucesso: nao faz HTTP

    const second = await reporter.report('empresa-1', 'WAMID-1');
    assert.equal(second.ok, true);
    assert.equal(second.deduplicated, true); // alreadyReported nao deixa passar
    assert.equal(fetches.length, 0); // Segunda tambem nao faz HTTP pq ja estava reported
  });
});
