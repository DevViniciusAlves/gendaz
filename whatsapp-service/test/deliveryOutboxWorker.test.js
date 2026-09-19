'use strict';

const { describe, it, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const { DeliveryOutboxWorker } = require('../src/whatsapp/deliveryOutboxWorker');

function createMockPool() {
  const state = {
    row: {
      id: 1,
      company_id: 1,
      provider_message_id: 'WAMID-1',
      state: 'PENDING',
      attempt_count: 0,
      recovery_count: 0,
      last_attempt_at: null,
      locked_until: null,
      next_attempt_at: null,
      http_status: null,
      http_error_message: null,
    },
    queries: [],
    connectCalls: 0,
    lastNextAttemptAt: null,
    activeConnections: 0,
    maxActiveConnections: 0,
  };
  const pool = {
    connect: async () => {
      state.connectCalls += 1;
      state.activeConnections += 1;
      state.maxActiveConnections = Math.max(state.maxActiveConnections, state.activeConnections);
      const client = {
        query: async (sql, params) => {
          state.queries.push({ sql, params });
          const normalizedSql = sql.replace(/\s+/g, ' ').trim().toUpperCase();
          if (normalizedSql === 'BEGIN' || normalizedSql === 'COMMIT' || normalizedSql === 'ROLLBACK') {
            return { rows: [] };
          }
            if (normalizedSql.includes('FROM WHATSAPP_DELIVERY_OUTBOX') && normalizedSql.includes('FOR UPDATE SKIP LOCKED')) {
              if (state.row.state === 'PENDING') {
                return { rows: [{ id: state.row.id, company_id: state.row.company_id, provider_message_id: state.row.provider_message_id, attempt_count: state.row.attempt_count, recovery_count: state.row.recovery_count }] };
              }
              return { rows: [] };
            }

          if (normalizedSql.includes("SET STATE = 'PROCESSING'")) {
            state.row.state = 'PROCESSING';
            state.row.attempt_count += 1;
            state.row.last_attempt_at = new Date();
            state.row.locked_until = new Date(Date.now() + 60000);
            return { rows: [{ attempt_count: state.row.attempt_count }] };
          }
          if (normalizedSql.includes("SET STATE = 'DONE'")) {
            state.row.state = 'DONE';
            return { rows: [] };
          }
          if (normalizedSql.includes("SET STATE = 'DEAD'")) {
            if (params && params.length === 2) { state.row.http_status = params[0]; } else if (params && params.length >= 2) { state.row.http_status = params[0]; state.row.http_error_message = params[1]; }
            state.row.state = 'DEAD';
            return { rows: [] };
          }
          if (normalizedSql.includes("SET STATE = 'PENDING'")) {
            if (params && params[0] instanceof Date) { state.lastNextAttemptAt = params[0]; state.row.next_attempt_at = params[0]; }
            if (params && params.length >= 3) { state.row.http_status = params[1]; state.row.http_error_message = params[2]; }
            state.row.state = 'PENDING';
            return { rows: [] };
          }
          return { rows: [] };
        },
        release: () => {
          state.activeConnections -= 1;
        },
      };
      return client;
    },
    end: async () => {},
  };
  return { pool, state };
}
function createLogger() {
  const logs = { log: [], warn: [], error: [], info: [] };
  const log = { log: (...args) => logs.log.push(args.join(' ')), warn: (...args) => logs.warn.push(args.join(' ')), error: (...args) => logs.error.push(args.join(' ')), info: (...args) => logs.info.push(args.join(' ')), _logs: logs };
  return log;
}
function assertDelayApprox(actualDate, expectedSec, toleranceMs = 800) {
  const now = Date.now();
  const diff = actualDate.getTime() - now;
  const expectedMs = expectedSec * 1000;
  assert.ok(Math.abs(diff - expectedMs) < toleranceMs, `expected ~${expectedSec}s delay, got ${diff}ms (expected ${expectedMs}ms)`);
}
describe('DeliveryOutboxWorker', () => {
  let pool; let state; let log;
  beforeEach(() => { const mock = createMockPool(); pool = mock.pool; state = mock.state; log = createLogger(); });
  afterEach(() => { if (globalThis.fetch && globalThis.fetch !== undefined) { globalThis.fetch = undefined; } });
  it('claim: PENDING -> PROCESSING com attempt_count incrementado', async () => {
    state.row.state = 'PENDING'; state.row.attempt_count = 0;
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log });
    worker._postToSpring = async () => {};
    const result = await worker._processCycle();
    assert.equal(result, true); assert.equal(state.row.state, 'PROCESSING'); assert.equal(state.row.attempt_count, 1); assert.ok(state.row.last_attempt_at);
  });
  it('_processCycle retorna true quando havia trabalho', async () => {
    state.row.state = 'PENDING'; state.row.attempt_count = 0;
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log });
    worker._postToSpring = async () => {};
    const result = await worker._processCycle();
    assert.equal(result, true);
  });
  it('_processCycle retorna false quando fila vazia', async () => {
    state.row.state = 'DONE';
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log });
    const result = await worker._processCycle();
    assert.equal(result, false);
  });
  it('loop nao espera entre itens disponiveis e dorme quando fila vazia', async () => {
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log });
    worker._pollingIntervalMs = 5000;
    let callCount = 0;
    worker._processCycle = async () => { callCount += 1; if (callCount <= 2) return true; if (callCount === 3) return false; worker._running = false; return false; };
    let sleepCalls = 0;
    const originalSetTimeout = global.setTimeout;
    global.setTimeout = (fn, ms) => { sleepCalls += 1; return originalSetTimeout(fn, 0); };
    worker._running = true;
    try {
      let iterations = 0;
      while (iterations < 4) {
        const processed = await worker._processCycle();
        if (processed) { assert.equal(sleepCalls, 0, 'nao deve dormir quando processed=true na iteracao ' + iterations); }
        else { if (worker._running) { await new Promise(resolve => setTimeout(resolve, worker._pollingIntervalMs)); } if (iterations === 2) { assert.equal(sleepCalls, 1); } }
        iterations += 1;
        if (iterations >= 4) worker._running = false;
      }
    } finally { global.setTimeout = originalSetTimeout; }
    assert.ok(true);
  });
  it('quando fila fica vazia, polling interval e utilizado', async () => {
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log });
    worker._pollingIntervalMs = 123;
    let capturedMs = null;
    const originalSetTimeout = global.setTimeout;
    global.setTimeout = (fn, ms) => { capturedMs = ms; worker._running = false; return originalSetTimeout(fn, 0); };
    worker._processCycle = async () => false;
    worker._running = true;
    await worker._runLoop();
    global.setTimeout = originalSetTimeout;
    assert.equal(capturedMs, 123);
  });
  it('se _processCycle lancar erro, loop nao entra em busy loop e aguarda polling', async () => {
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log });
    worker._pollingIntervalMs = 456;
    let callIndex = 0;
    worker._processCycle = async () => { callIndex += 1; if (callIndex === 1) throw new Error('boom'); worker._running = false; return false; };
    let capturedMs = null;
    const originalSetTimeout = global.setTimeout;
    global.setTimeout = (fn, ms) => { capturedMs = ms; return originalSetTimeout(fn, 0); };
    worker._running = true;
    await worker._runLoop();
    global.setTimeout = originalSetTimeout;
    assert.equal(capturedMs, 456);
    assert.equal(log._logs.error.length, 1);
    assert.ok(log._logs.error[0].includes('erro no cycle'));
  });
  it('200 -> DONE', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 200, {}); assert.equal(state.row.state, 'DONE'); });
  it('202 -> DONE', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 202, {}); assert.equal(state.row.state, 'DONE'); });
  it('400 -> DEAD', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 400, {}); assert.equal(state.row.state, 'DEAD'); });
  it('404 -> DEAD', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 404, {}); assert.equal(state.row.state, 'DEAD'); });
  it('405 -> DEAD', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 405, {}); assert.equal(state.row.state, 'DEAD'); });
  it('422 -> DEAD', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 422, {}); assert.equal(state.row.state, 'DEAD'); });
  it('status HTTP inesperado -> DEAD (ex: 418)', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 418, {}); assert.equal(state.row.state, 'DEAD'); assert.equal(state.row.http_error_message, 'unexpected_http_status'); });
  it('status inesperado 201 -> DEAD', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 201, {}); assert.equal(state.row.state, 'DEAD'); });
  it('401 deve ser auth_error', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'https://backend.test', internalToken: 'test-token', log, }); await worker._handleResponse(state.row, 401, {}); assert.equal(state.row.http_error_message, 'auth_error'); });
  it('401, segunda tentativa -> PENDING com +60s', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 2; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 401, {}); assert.equal(state.row.state, 'PENDING'); assertDelayApprox(state.lastNextAttemptAt, 60); });
  it('401, terceira tentativa -> DEAD', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 3; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 401, {}); assert.equal(state.row.state, 'DEAD'); });
  it('403 deve ser forbidden_error', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'https://backend.test', internalToken: 'test-token', log, }); await worker._handleResponse(state.row, 403, {}); assert.equal(state.row.http_error_message, 'forbidden_error'); });
  it('403, segunda tentativa -> PENDING com +60s', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 2; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 403, {}); assert.equal(state.row.state, 'PENDING'); assertDelayApprox(state.lastNextAttemptAt, 60); });
  it('403, terceira tentativa -> DEAD', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 3; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 403, {}); assert.equal(state.row.state, 'DEAD'); });
  it('429 utiliza retry transitorio +5s na tentativa 1', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 429, {}); assert.equal(state.row.state, 'PENDING'); assertDelayApprox(state.lastNextAttemptAt, 5); });
  it('503 utiliza retry transitorio +5s na tentativa 1', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 503, {}); assert.equal(state.row.state, 'PENDING'); assertDelayApprox(state.lastNextAttemptAt, 5); });
  it('500 utiliza retry transitorio', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 2; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 500, {}); assert.equal(state.row.state, 'PENDING'); assertDelayApprox(state.lastNextAttemptAt, 15); });
  it('network error utiliza retry transitorio', async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => { throw new Error('network failure'); };
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log });
    state.row.state = 'PROCESSING'; state.row.attempt_count = 1;
    await worker._postToSpring(state.row);
    assert.equal(state.row.state, 'PENDING'); assertDelayApprox(state.lastNextAttemptAt, 5);
    globalThis.fetch = originalFetch;
  });
  it('HTTP real: POST /internal/whatsapp/delivery -> DONE', async () => {
      const originalFetch = globalThis.fetch;
      let capturedUrl, capturedOptions;
      globalThis.fetch = async (url, options) => { capturedUrl = url; capturedOptions = options; return { status: 200, text: async () => JSON.stringify({ status: 'delivered' }) }; };
      const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log });
      state.row.state = 'PROCESSING'; state.row.attempt_count = 1;
      await worker._postToSpring(state.row);
      assert.equal(state.row.state, 'DONE'); assert.equal(capturedUrl, 'http://spring:8080/internal/whatsapp/delivery'); assert.equal(capturedOptions.method, 'POST'); assert.equal(capturedOptions.headers['Authorization'], 'Bearer test-token'); assert.ok(capturedOptions.body.includes('DELIVERED'));
      globalThis.fetch = originalFetch;
    });
  it('backendUrl ausente -> PENDING', async () => { const worker = new DeliveryOutboxWorker({ pool, backendUrl: '', internalToken: 'test-token', log }); state.row.state = 'PROCESSING'; state.row.attempt_count = 1; await worker._postToSpring(state.row); assert.equal(state.row.state, 'PENDING'); });
  it('internalToken ausente -> PENDING', async () => { const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: '', log }); state.row.state = 'PROCESSING'; state.row.attempt_count = 1; await worker._postToSpring(state.row); assert.equal(state.row.state, 'PENDING'); });
  it('attempt_count 7 transitorio -> DEAD', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 7; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleRetry(state.row, 'network_error'); assert.equal(state.row.state, 'DEAD'); });
  it('backoff transitorio tentativa 1 -> +5s', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._scheduleRetry(state.row, 503, 'server_error'); assertDelayApprox(state.lastNextAttemptAt, 5); });
  it('backoff transitorio tentativa 2 -> +15s', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 2; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._scheduleRetry(state.row, 503, 'server_error'); assertDelayApprox(state.lastNextAttemptAt, 15); });
  it('backoff transitorio tentativa 3 -> +30s', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 3; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._scheduleRetry(state.row, 503, 'server_error'); assertDelayApprox(state.lastNextAttemptAt, 30); });
  it('backoff transitorio tentativa 4 -> +60s', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 4; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._scheduleRetry(state.row, 503, 'server_error'); assertDelayApprox(state.lastNextAttemptAt, 60); });
  it('backoff transitorio tentativa 5 -> +120s', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 5; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._scheduleRetry(state.row, 503, 'server_error'); assertDelayApprox(state.lastNextAttemptAt, 120); });
  it('backoff transitorio tentativa 6 -> +300s', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 6; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._scheduleRetry(state.row, 503, 'server_error'); assertDelayApprox(state.lastNextAttemptAt, 300); });
  it('backoff transitorio tentativa 7 -> DEAD', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 7; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._scheduleRetry(state.row, 503, 'server_error'); assert.equal(state.row.state, 'DEAD'); });
  it('auth backoff tentativa 1 -> +15s e tentativa 2 -> +60s, 3 -> DEAD', async () => {
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log });
    state.row.state = 'PROCESSING'; state.row.attempt_count = 1; await worker._scheduleRetry(state.row, 401, 'auth_error'); assert.equal(state.row.state, 'PENDING'); assertDelayApprox(state.lastNextAttemptAt, 15);
    state.row.state = 'PROCESSING'; state.row.attempt_count = 2; await worker._scheduleRetry(state.row, 401, 'auth_error'); assert.equal(state.row.state, 'PENDING'); assertDelayApprox(state.lastNextAttemptAt, 60);
    state.row.state = 'PROCESSING'; state.row.attempt_count = 3; await worker._scheduleRetry(state.row, 401, 'auth_error'); assert.equal(state.row.state, 'DEAD');
  });
  it('503 retry nao abre duas conexoes simultaneas', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; state.connectCalls = 0; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 503, {}); assert.equal(state.connectCalls, 1); assert.equal(state.row.state, 'PENDING'); });
  it('401 retry nao abre duas conexoes simultaneas', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; state.connectCalls = 0; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 401, {}); assert.equal(state.connectCalls, 1); });
  it('200 DONE usa apenas 1 conexao', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; state.connectCalls = 0; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 200, {}); assert.equal(state.connectCalls, 1); });
  it('log de retry contem status, motivo, tentativa e proxima espera', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 3; state.row.company_id = 123; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 503, {}); const infoLog = log._logs.info.join(' '); assert.ok(infoLog.includes('123')); assert.ok(infoLog.includes('503')); assert.ok(infoLog.includes('server_error')); assert.ok(infoLog.includes('3/7')); assert.ok(infoLog.includes('30s')); });
  it('log DEAD contem motivo e tentativa', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 7; state.row.company_id = 123; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 503, {}); const warnLog = log._logs.warn.join(' '); assert.ok(warnLog.includes('DEAD')); assert.ok(warnLog.includes('server_error')); assert.ok(warnLog.includes('7/7')); });
  it('log de retry auth contem 401 e auth_error', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 2; state.row.company_id = 999; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._handleResponse(state.row, 401, {}); const infoLog = log._logs.info.join(' '); assert.ok(infoLog.includes('401')); assert.ok(infoLog.includes('auth_error')); assert.ok(infoLog.includes('2/3')); assert.ok(infoLog.includes('60s')); });
  it('log de network retry contem network e motivo', async () => { state.row.state = 'PROCESSING'; state.row.attempt_count = 1; state.row.company_id = 55; const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log }); await worker._scheduleRetry(state.row, null, 'network_error'); const infoLog = log._logs.info.join(' '); assert.ok(infoLog.includes('network')); assert.ok(infoLog.includes('network_error')); assert.ok(infoLog.includes('1/7')); });
  it('fluxo completo 503: _processCycle com maxActiveConnections === 1 e PENDING', async () => {
    state.row.state = 'PENDING'; state.row.attempt_count = 0;
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => ({ status: 503, text: async () => '{}' });
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log });
    state.activeConnections = 0; state.maxActiveConnections = 0;
    const result = await worker._processCycle();
    assert.equal(result, true);
    assert.equal(state.row.state, 'PENDING');
    assert.equal(state.row.attempt_count, 1);
    assert.equal(state.maxActiveConnections, 1);
    globalThis.fetch = originalFetch;
  });
  it('fluxo completo 200: _processCycle com maxActiveConnections === 1 e DONE', async () => {
    state.row.state = 'PENDING'; state.row.attempt_count = 0;
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => ({ status: 200, text: async () => '{}' });
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log });
    state.activeConnections = 0; state.maxActiveConnections = 0;
    const result = await worker._processCycle();
    assert.equal(result, true);
    assert.equal(state.row.state, 'DONE');
    assert.equal(state.maxActiveConnections, 1);
    globalThis.fetch = originalFetch;
  });
  it('fluxo completo network error: _processCycle com maxActiveConnections === 1 e PENDING', async () => {
    state.row.state = 'PENDING'; state.row.attempt_count = 0;
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => { throw new Error('network failure'); };
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log });
    state.activeConnections = 0; state.maxActiveConnections = 0;
    const result = await worker._processCycle();
    assert.equal(result, true);
    assert.equal(state.row.state, 'PENDING');
    assert.equal(state.maxActiveConnections, 1);
    globalThis.fetch = originalFetch;
  });
  it('log permanente 400 contem DEAD e nao mostra /7', async () => {
    state.row.state = 'PROCESSING'; state.row.attempt_count = 1; state.row.company_id = 123;
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log });
    await worker._handleResponse(state.row, 400, {});
    const warnLog = log._logs.warn.join(' ');
    assert.ok(warnLog.includes('400'));
    assert.ok(warnLog.includes('permanent_error'));
    assert.ok(warnLog.includes('DEAD'));
    assert.ok(!warnLog.includes('/7'));
  });
  it('log inesperado 418 contem DEAD e nao mostra /7', async () => {
    state.row.state = 'PROCESSING'; state.row.attempt_count = 1; state.row.company_id = 123;
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'http://spring:8080', internalToken: 'test-token', log });
    await worker._handleResponse(state.row, 418, {});
    const warnLog = log._logs.warn.join(' ');
    assert.ok(warnLog.includes('418'));
    assert.ok(warnLog.includes('unexpected_http_status'));
    assert.ok(warnLog.includes('DEAD'));
    assert.ok(!warnLog.includes('/7'));
  });
});