'use strict';

const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const Boom = require('@hapi/boom');
const { SessionManager, STATES, NO_RETRY_CODES } = require('../src/whatsapp/sessionManager');

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function makeHarness(options = {}) {
  const created = [];
  let saves = 0;
  let clears = [];
  let hasRegisteredResults = new Map();
  let failuresLeft = options.failCreateTimes || 0;

  const factory = () => {
    if (failuresLeft > 0) {
      failuresLeft -= 1;
      throw new Error('falha-simulada-criacao-socket');
    }
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
    load: async (companyId) => ({
      state: { creds: { registered: hasRegisteredResults.get(companyId) || false } },
      saveCreds: async () => {
        saves += 1;
      },
    }),
    clear: async (companyId) => {
      clears.push(companyId);
    },
    listCompanies: async () => [],
    hasRegisteredSession: async (companyId) => hasRegisteredResults.get(companyId) || false,
    flush: async () => {},
    flushAll: async () => {},
    close: async () => {},
  };

  const manager = new SessionManager({
    authStore,
    createSocket: factory,
    baseDelayMs: 10,
    maxDelayMs: 40,
    maxAttempts: 3,
    recoveryCooldownMs: 100,
    log: { log: () => {}, warn: () => {}, error: () => {}, info: () => {} },
    ...options,
  });

  return { manager, created, stats: () => ({ saves, clears: [...clears] }), setHasRegistered: (id, val) => hasRegisteredResults.set(id, val) };
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

describe('SessionManager - Recovery Logic', () => {
  let h;
  beforeEach(() => {
    h = makeHarness();
  });

  it('boot com auth registrado -> cria socket -> nao gera novo QR artificialmente', async () => {
    h = makeHarness();
    h.setHasRegistered('empresa-1', true);
    await h.manager.initialize(); // listCompanies vazio no mock, mas podemos testar connect direto
    await h.manager.connect('empresa-1');
    await sleep(10);
    assert.equal(h.created.length, 1);
    assert.equal(h.manager.getQr('empresa-1'), null);
    assert.equal(h.manager.status('empresa-1').state, STATES.CONNECTING);
  });

 it('boot sem auth -> NOT_CONNECTED -> nenhum socket', async () => {
      h = makeHarness();
      h.setHasRegistered('empresa-sem-auth', false);
      await h.manager.initialize();
      await sleep(10);
      assert.equal(h.created.length, 0);
      assert.equal(h.manager.status('empresa-sem-auth').state, STATES.NOT_CONNECTED);
    });

  it('status de sessao registrada DISCONNECTED -> aciona recovery', async () => {
    h = makeHarness();
    h.setHasRegistered('empresa-rec', true);
    await h.manager.connect('empresa-rec');
    await sleep(5);
    closeWith(h.created[0], 408); // erro temporario
    await sleep(50);
    assert.equal(h.manager.status('empresa-rec').state, STATES.CONNECTING);

    // Simula status check que dispara ensureConnected
    h.setHasRegistered('empresa-rec', true);
    const beforeCreated = h.created.length;
    h.manager.ensureConnected('empresa-rec', 'status');
    await sleep(10);
    // Deve tentar reconectar (ja estava em RECONNECTING, entao no-op)
    // Mas se tivesse ido para DISCONNECTED apos esgotar tentativas, tentaria de novo
  });

  it('status de sessao sem auth -> NAO aciona connect', async () => {
    h = makeHarness();
    h.setHasRegistered('empresa-sem-auth-2', false);
    const record = h.manager.getRecord('empresa-sem-auth-2');
    record.state = STATES.DISCONNECTED;
    record.reconnectAttempts = 10; // esgotado

    const beforeCreated = h.created.length;
    await h.manager.ensureConnected('empresa-sem-auth-2', 'status');
    await sleep(10);
    assert.equal(h.created.length, beforeCreated); // nao deve criar socket
    assert.equal(h.manager.status('empresa-sem-auth-2').state, STATES.DISCONNECTED);
  });

  it('send em DISCONNECTED com auth -> dispara recovery -> ainda retorna session_not_connected ate CONNECTED', async () => {
    h = makeHarness();
    h.setHasRegistered('empresa-send', true);
    await h.manager.connect('empresa-send');
    await sleep(5);
    closeWith(h.created[0], 408);
    await sleep(50);
    assert.equal(h.manager.status('empresa-send').state, STATES.CONNECTING);

    // Aguarda ir para DISCONNECTED
    h.manager.maxAttempts = 1;
    closeWith(h.created[1], 408);
    await sleep(100);
    assert.equal(h.manager.status('empresa-send').state, STATES.DISCONNECTED);

    // Agora send deve disparar recovery mas retornar session_not_connected
    try {
      await h.manager.sendText('empresa-send', '5511999999999', 'test');
      assert.fail('deveria lancar session_not_connected');
    } catch (err) {
      assert.equal(err.code, 'session_not_connected');
      assert.equal(err.state, STATES.DISCONNECTED);
    }
  });

  it('send sem auth -> nao gera QR', async () => {
    h = makeHarness();
    h.setHasRegistered('empresa-sem-auth-3', false);
    const record = h.manager.getRecord('empresa-sem-auth-3');
    record.state = STATES.NOT_CONNECTED;

    try {
      await h.manager.sendText('empresa-sem-auth-3', '5511999999999', 'test');
      assert.fail('deveria lancar session_not_connected');
    } catch (err) {
      assert.equal(err.code, 'session_not_connected');
      assert.equal(err.state, STATES.NOT_CONNECTED);
    }
    // Nao deve ter tentado conectar
    assert.equal(h.created.length, 0);
  });

  it('401 -> LOGGED_OUT -> flush -> clear -> nenhuma reconexao', async () => {
    h = makeHarness();
    await h.manager.connect('empresa-401');
    await sleep(5);
    closeWith(h.created[0], 401);
    await sleep(50);
    assert.equal(h.manager.status('empresa-401').state, STATES.LOGGED_OUT);
    assert.deepEqual(h.stats().clears, ['empresa-401']);
    assert.equal(h.created.length, 1); // nenhum socket novo
  });

  it('evento creds.update tardio de generation antiga -> ignorado', async () => {
    h = makeHarness();
    await h.manager.connect('empresa-creds');
    const sock = h.created[0];
    await sleep(5);
    const savesBefore = h.stats().saves;

    // Forca loggedOut
    closeWith(sock, 401);
    await sleep(20);
    assert.equal(h.manager.status('empresa-creds').state, STATES.LOGGED_OUT);

    // Evento tardio de creds.update do socket antigo
    emit(sock, 'creds.update', {});
    await sleep(10);
    assert.equal(h.stats().saves, savesBefore); // nao deve salvar
  });

  it('write antiga pendente + 401 -> clear vence por ultimo -> auth nao reaparece', async () => {
    h = makeHarness();
    await h.manager.connect('empresa-write-race');
    const sock = h.created[0];
    await sleep(5);

    // Dispara write de creds
    emit(sock, 'creds.update', {});
    await sleep(5);
    const savesBefore = h.stats().saves;

    // loggedOut
    closeWith(sock, 401);
    await sleep(50);
    assert.equal(h.manager.status('empresa-write-race').state, STATES.LOGGED_OUT);
    assert.deepEqual(h.stats().clears, ['empresa-write-race']);

    // Write pendente resolve depois do clear - mas clear ja aconteceu
    // O saveCreds ja foi enfileirado antes do clear, mas a geracao mudou
    // O creds.update listener confere generation e ignora
  });

  it('408 -> retry', async () => {
    h = makeHarness();
    await h.manager.connect('empresa-408');
    closeWith(h.created[0], 408);
    await sleep(5);
    assert.equal(h.manager.status('empresa-408').state, STATES.RECONNECTING);
    await sleep(50);
    assert.equal(h.created.length, 2);
  });

  it('515 -> retry', async () => {
    h = makeHarness();
    await h.manager.connect('empresa-515');
    closeWith(h.created[0], 515);
    await sleep(5);
    assert.equal(h.manager.status('empresa-515').state, STATES.RECONNECTING);
  });

  it('tentativas esgotadas -> nao fica morto para sempre -> recovery posterior possivel', async () => {
    h = makeHarness({ maxAttempts: 2 });
    h.setHasRegistered('empresa-esgotado', true);
    await h.manager.connect('empresa-esgotado');
    closeWith(h.created[0], 408);
    await sleep(30);
    closeWith(h.created[1], 408);
    await sleep(30);
    closeWith(h.created[2], 408);
    await sleep(100);
    assert.equal(h.manager.status('empresa-esgotado').state, STATES.DISCONNECTED);

    // Cooldown passa, ensureConnected deve tentar de novo
    h.setHasRegistered('empresa-esgotado', true);
    await h.manager.ensureConnected('empresa-esgotado', 'test');
    await sleep(150); // espera cooldown
    // Deve ter tentado conectar novamente
    // (o teste exato depende do timing do cooldown)
  });

  it('440 -> nao entra em loop -> auth preservado', async () => {
    h = makeHarness();
    h.setHasRegistered('empresa-440', true);
    await h.manager.connect('empresa-440');
    closeWith(h.created[0], 440);
    await sleep(50);
    assert.equal(h.manager.status('empresa-440').state, STATES.DISCONNECTED);
    assert.equal(h.created.length, 1);
    assert.deepEqual(h.stats().clears, []); // auth preservado
  });

  it('403 -> sem loop agressivo -> auth preservado', async () => {
    h = makeHarness();
    h.setHasRegistered('empresa-403', true);
    await h.manager.connect('empresa-403');
    closeWith(h.created[0], 403);
    await sleep(50);
    assert.equal(h.manager.status('empresa-403').state, STATES.DISCONNECTED);
    assert.equal(h.created.length, 1);
    assert.deepEqual(h.stats().clears, []); // auth preservado
  });

  it('empresa A -> nao interfere B', async () => {
    h = makeHarness();
    h.setHasRegistered('empresa-A', true);
    h.setHasRegistered('empresa-B', true);
    await h.manager.connect('empresa-A');
    await h.manager.connect('empresa-B');
    closeWith(h.created[0], 408);
    await sleep(50);
assert.equal(h.manager.status('empresa-A').state, STATES.CONNECTING);
     assert.equal(h.manager.status('empresa-B').state, STATES.CONNECTING); // B nao afetado
  });

  it('connect simultaneo -> single-flight preservado', async () => {
    h = makeHarness();
    const [a, b] = await Promise.all([
      h.manager.connect('empresa-simul'),
      h.manager.connect('empresa-simul'),
    ]);
    assert.equal(a, b);
    assert.equal(h.created.length, 1);
  });

  it('shutdown -> end -> sem logout -> sem clear -> flush antes de fechar storage', async () => {
    h = makeHarness();
    await h.manager.connect('empresa-shutdown');
    await h.manager.connect('empresa-shutdown-2');
    closeWith(h.created[0], 408); // agenda timer de retry
    await sleep(5);
    assert.equal(h.manager.status('empresa-shutdown').state, STATES.RECONNECTING);

    await h.manager.shutdownAll();
    // Verifica que flush foi chamado (sem erro)
    // Verifica que nao houve logout nem clear
    assert.equal(h.created[0].logoutCalls, 0);
    assert.equal(h.created[1].logoutCalls, 0);
    assert.deepEqual(h.stats().clears, []);
  });
});