'use strict';
const { describe, it, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const { DeliveryOutboxWorker } = require('../src/whatsapp/deliveryOutboxWorker');

function createResponse(status, headers = {}) {
  return {
    status,
    headers: {
      get(name) {
        const key = String(name).toLowerCase();
        return headers[key] ?? null;
      },
    },
  };
}

describe('DeliveryOutboxWorker - Circuit Breaker Tests', () => {
  const originalFetch = globalThis.fetch;
  afterEach(() => { globalThis.fetch = originalFetch; });

  it('estado inicial do circuit breaker', () => {
    const worker = new DeliveryOutboxWorker({ pool: { connect: async () => ({ query: async () => ({ rows: [] }), release: () => {} }) }, backendUrl: 'https://backend.test', internalToken: 'token' });
    assert.equal(worker._backendCircuitState, 'OPEN');
    assert.equal(worker._backendNextProbeAt, 0);
  });

  it('circuit fecha após preflight 200', async () => {
    const worker = new DeliveryOutboxWorker({ pool: { connect: async () => ({ query: async () => ({ rows: [] }), release: () => {} }) }, backendUrl: 'https://backend.test', internalToken: 'token' });
    globalThis.fetch = async () => createResponse(200);

    const ok = await worker._ensureBackendAvailable();
    assert.equal(ok, true);
    assert.equal(worker._backendCircuitState, 'CLOSED');
    assert.equal(worker._backendFailureCount, 0);
  });

  it('single-flight evita múltiplas requisições simultâneas', async () => {
    const worker = new DeliveryOutboxWorker({ pool: { connect: async () => ({ query: async () => ({ rows: [] }), release: () => {} }) }, backendUrl: 'https://backend.test', internalToken: 'token' });
    let calls = 0;
    worker._checkBackendAccess = async () => {
      calls += 1;
      await new Promise(resolve => setTimeout(resolve, 15));
      return { ok: true, status: 200, reason: null };
    };

    const results = await Promise.all([
      worker._ensureBackendAvailable(),
      worker._ensureBackendAvailable(),
      worker._ensureBackendAvailable(),
    ]);

    assert.deepEqual(results, [true, true, true]);
    assert.equal(calls, 1);
  });

  it('401 abre o circuit e define failure reason como auth_error', async () => {
    const worker = new DeliveryOutboxWorker({ pool: { connect: async () => ({ query: async () => ({ rows: [] }), release: () => {} }) }, backendUrl: 'https://backend.test', internalToken: 'token' });
    const row = { id: 1 };
    globalThis.fetch = async () => createResponse(401);

    await worker._handleResponse(row, 401, {});
    assert.equal(worker._backendCircuitState, 'OPEN');
    assert.equal(worker._backendLastFailureReason, 'auth_error');
  });

  it('403 abre o circuit e define failure reason como forbidden_error', async () => {
    const worker = new DeliveryOutboxWorker({ pool: { connect: async () => ({ query: async () => ({ rows: [] }), release: () => {} }) }, backendUrl: 'https://backend.test', internalToken: 'token' });
    const row = { id: 1 };
    globalThis.fetch = async () => createResponse(403);

    await worker._handleResponse(row, 403, {});
    assert.equal(worker._backendCircuitState, 'OPEN');
    assert.equal(worker._backendLastFailureReason, 'forbidden_error');
  });
});
