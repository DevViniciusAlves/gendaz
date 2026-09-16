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
      last_attempt_at: null,
      locked_until: null
    },
    queries: []
  };

  const client = {
    query: async (sql, params) => {
      state.queries.push(sql);
      const normalizedSql = sql
        .replace(/\s+/g, ' ')
        .trim()
        .toUpperCase();

      if (normalizedSql === 'BEGIN' || normalizedSql === 'COMMIT' || normalizedSql === 'ROLLBACK') {
        return { rows: [] };
      }

      if (normalizedSql.includes('FROM WHATSAPP_DELIVERY_OUTBOX') && normalizedSql.includes('FOR UPDATE SKIP LOCKED')) {
        if (state.row.state === 'PENDING') {
          return { rows: [{ id: state.row.id, company_id: state.row.company_id, provider_message_id: state.row.provider_message_id, attempt_count: state.row.attempt_count }] };
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
        state.row.state = 'DEAD';
        return { rows: [] };
      }

      if (normalizedSql.includes("SET STATE = 'PENDING'")) {
        state.row.state = 'PENDING';
        return { rows: [] };
      }

      return { rows: [] };
    },
    release: () => {},
  };

  const pool = {
    connect: async () => client,
    end: async () => {},
  };

  return { pool, state };
}

describe('DeliveryOutboxWorker', () => {
  let pool;
  let state;
  let log;

  beforeEach(() => {
    const mock = createMockPool();
    pool = mock.pool;
    state = mock.state;
    log = { log: () => {}, warn: () => {}, error: () => {}, info: () => {} };
  });

  afterEach(() => {
    if (globalThis.fetch && globalThis.fetch !== undefined) {
      // restore handled per-test
    }
  });

  it('claim: PENDING -> PROCESSING com attempt_count incrementado', async () => {
    state.row.state = 'PENDING';
    state.row.attempt_count = 0;

    const worker = new DeliveryOutboxWorker({
      pool,
      backendUrl: 'http://spring:8080',
      internalToken: 'test-token',
      log
    });

    worker._postToSpring = async () => {};

    await worker._processCycle();

    assert.equal(state.row.state, 'PROCESSING');
    assert.equal(state.row.attempt_count, 1);
    assert.ok(state.row.last_attempt_at);
  });

  it('200 -> DONE', async () => {
    state.row.state = 'PROCESSING';
    state.row.attempt_count = 1;

    const worker = new DeliveryOutboxWorker({
      pool,
      backendUrl: 'http://spring:8080',
      internalToken: 'test-token',
      log
    });

    await worker._handleResponse(state.row, 200, {});
    assert.equal(state.row.state, 'DONE');
  });

  it('202 -> DONE', async () => {
    state.row.state = 'PROCESSING';
    state.row.attempt_count = 1;

    const worker = new DeliveryOutboxWorker({
      pool,
      backendUrl: 'http://spring:8080',
      internalToken: 'test-token',
      log
    });

    await worker._handleResponse(state.row, 202, {});
    assert.equal(state.row.state, 'DONE');
  });

  it('400 -> DEAD', async () => {
    state.row.state = 'PROCESSING';
    state.row.attempt_count = 1;

    const worker = new DeliveryOutboxWorker({
      pool,
      backendUrl: 'http://spring:8080',
      internalToken: 'test-token',
      log
    });

    await worker._handleResponse(state.row, 400, {});
    assert.equal(state.row.state, 'DEAD');
  });

  it('401 -> PENDING', async () => {
    state.row.state = 'PROCESSING';
    state.row.attempt_count = 1;

    const worker = new DeliveryOutboxWorker({
      pool,
      backendUrl: 'http://spring:8080',
      internalToken: 'test-token',
      log
    });

    await worker._handleResponse(state.row, 401, {});
    assert.equal(state.row.state, 'PENDING');
  });

  it('403 -> PENDING', async () => {
    state.row.state = 'PROCESSING';
    state.row.attempt_count = 1;

    const worker = new DeliveryOutboxWorker({
      pool,
      backendUrl: 'http://spring:8080',
      internalToken: 'test-token',
      log
    });

    await worker._handleResponse(state.row, 403, {});
    assert.equal(state.row.state, 'PENDING');
  });

  it('429 -> PENDING', async () => {
    state.row.state = 'PROCESSING';
    state.row.attempt_count = 1;

    const worker = new DeliveryOutboxWorker({
      pool,
      backendUrl: 'http://spring:8080',
      internalToken: 'test-token',
      log
    });

    await worker._handleResponse(state.row, 429, {});
    assert.equal(state.row.state, 'PENDING');
  });

  it('503 -> PENDING', async () => {
    state.row.state = 'PROCESSING';
    state.row.attempt_count = 1;

    const worker = new DeliveryOutboxWorker({
      pool,
      backendUrl: 'http://spring:8080',
      internalToken: 'test-token',
      log
    });

    await worker._handleResponse(state.row, 503, {});
    assert.equal(state.row.state, 'PENDING');
  });

  it('network error -> PENDING via _handleRetry', async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => { throw new Error('network failure'); };

    const worker = new DeliveryOutboxWorker({
      pool,
      backendUrl: 'http://spring:8080',
      internalToken: 'test-token',
      log
    });

    state.row.state = 'PROCESSING';
    state.row.attempt_count = 1;

    await worker._postToSpring(state.row);
    assert.equal(state.row.state, 'PENDING');

    globalThis.fetch = originalFetch;
  });

  it('HTTP real: POST /internal/whatsapp/delivery -> DONE', async () => {
    globalThis.fetch = async (url, options) => ({
      status: 200,
      text: async () => JSON.stringify({ status: 'delivered' })
    });

    const worker = new DeliveryOutboxWorker({
      pool,
      backendUrl: 'http://spring:8080',
      internalToken: 'test-token',
      log
    });

    state.row.state = 'PROCESSING';
    state.row.attempt_count = 1;

    await worker._postToSpring(state.row);
    assert.equal(state.row.state, 'DONE');

    globalThis.fetch = undefined;
  });

  it('backendUrl ausente -> PENDING', async () => {
    const worker = new DeliveryOutboxWorker({
      pool,
      backendUrl: '',
      internalToken: 'test-token',
      log
    });

    state.row.state = 'PROCESSING';
    state.row.attempt_count = 1;

    await worker._postToSpring(state.row);
    assert.equal(state.row.state, 'PENDING');
  });

  it('internalToken ausente -> PENDING', async () => {
    const worker = new DeliveryOutboxWorker({
      pool,
      backendUrl: 'http://spring:8080',
      internalToken: '',
      log
    });

    state.row.state = 'PROCESSING';
    state.row.attempt_count = 1;

    await worker._postToSpring(state.row);
    assert.equal(state.row.state, 'PENDING');
  });

  it('attempt_count 20 -> DEAD', async () => {
    state.row.state = 'PROCESSING';
    state.row.attempt_count = 20;

    const worker = new DeliveryOutboxWorker({
      pool,
      backendUrl: 'http://spring:8080',
      internalToken: 'test-token',
      log
    });

    await worker._handleRetry(state.row, 'network_error');
    assert.equal(state.row.state, 'DEAD');
  });
});
