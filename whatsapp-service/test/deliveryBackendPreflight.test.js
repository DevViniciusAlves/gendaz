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

describe('DeliveryOutboxWorker - Backend Preflight Tests', () => {
  const originalFetch = globalThis.fetch;
  afterEach(() => { globalThis.fetch = originalFetch; });

  it('200 -> ok=true', async () => {
    const worker = new DeliveryOutboxWorker({ pool: { connect: async () => ({}) }, backendUrl: 'https://backend.test', internalToken: 'token' });
    globalThis.fetch = async () => createResponse(200);
    const result = await worker._checkBackendAccess();
    assert.equal(result.ok, true);
    assert.equal(result.status, 200);
  });

  it('401 -> auth_error', async () => {
    const worker = new DeliveryOutboxWorker({ pool: { connect: async () => ({}) }, backendUrl: 'https://backend.test', internalToken: 'token' });
    globalThis.fetch = async () => createResponse(401);
    const result = await worker._checkBackendAccess();
    assert.equal(result.ok, false);
    assert.equal(result.reason, 'auth_error');
  });

  it('403 -> forbidden_error with diagnostic', async () => {
    const worker = new DeliveryOutboxWorker({ pool: { connect: async () => ({}) }, backendUrl: 'https://backend.test', internalToken: 'token' });
    globalThis.fetch = async () => createResponse(403, { 'server': 'cloudflare', 'cf-ray': 'abc123' });
    const result = await worker._checkBackendAccess();
    assert.equal(result.ok, false);
    assert.equal(result.status, 403);
    assert.equal(result.diagnostic.server, 'cloudflare');
    assert.equal(result.diagnostic.cfRay, 'abc123');
    assert.equal(result.diagnostic.internalHandler, '-');
  });

  it('401 -> captures internalHandler marker', async () => {
    const worker = new DeliveryOutboxWorker({ pool: { connect: async () => ({}) }, backendUrl: 'https://backend.test', internalToken: 'token' });
    globalThis.fetch = async () => createResponse(401, { 'x-gendaz-internal-handler': 'whatsapp-delivery-controller' });
    const result = await worker._checkBackendAccess();
    assert.equal(result.diagnostic.internalHandler, 'whatsapp-delivery-controller');
  });

  it('429 -> rate_limit', async () => {
    const worker = new DeliveryOutboxWorker({ pool: { connect: async () => ({}) }, backendUrl: 'https://backend.test', internalToken: 'token' });
    globalThis.fetch = async () => createResponse(429);
    const result = await worker._checkBackendAccess();
    assert.equal(result.ok, false);
    assert.equal(result.reason, 'rate_limit');
  });

  it('500 -> server_error', async () => {
    const worker = new DeliveryOutboxWorker({ pool: { connect: async () => ({}) }, backendUrl: 'https://backend.test', internalToken: 'token' });
    globalThis.fetch = async () => createResponse(500);
    const result = await worker._checkBackendAccess();
    assert.equal(result.ok, false);
    assert.equal(result.reason, 'server_error');
  });

  it('Network error -> network_error', async () => {
    const worker = new DeliveryOutboxWorker({ pool: { connect: async () => ({}) }, backendUrl: 'https://backend.test', internalToken: 'token' });
    globalThis.fetch = async () => { throw new Error('network down'); };
    const result = await worker._checkBackendAccess();
    assert.equal(result.ok, false);
    assert.equal(result.reason, 'network_error');
  });
});
