'use strict';
const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { DeliveryOutboxWorker } = require('../src/whatsapp/deliveryOutboxWorker');

describe('DeliveryOutboxWorker - Backend Preflight Tests', () => {
  it('should return correct results based on backend response status', async () => {
    const worker = new DeliveryOutboxWorker({ pool: { connect: async () => ({}) }, backendUrl: 'https://backend.test', internalToken: 'token' });
    
    const scenarios = [
      { status: 200, expectedOk: true, expectedReason: null },
      { status: 401, expectedOk: false, expectedReason: 'auth_error' },
      { status: 403, expectedOk: false, expectedReason: 'forbidden_error' },
      { status: 429, expectedOk: false, expectedReason: 'rate_limit' },
      { status: 500, expectedOk: false, expectedReason: 'server_error' },
    ];

    for (const s of scenarios) {
      globalThis.fetch = async () => ({
        status: s.status,
        headers: new Map(),
        text: async () => ''
  it('should capture diagnostic for 403', async () => {
    const worker = new DeliveryOutboxWorker({ pool: { connect: async () => ({}) }, backendUrl: 'https://backend.test', internalToken: 'token' });
    
    globalThis.fetch = async () => ({
      status: 403,
      headers: new Map([['server', 'cloudflare'], ['cf-ray', 'abc123']]),
      text: async () => ''
    });

    const result = await worker._checkBackendAccess();
    assert.equal(result.status, 403);
    assert.equal(result.diagnostic.server, 'cloudflare');
    assert.equal(result.diagnostic.cfRay, 'abc123');
    assert.equal(result.diagnostic.internalHandler, '-');
  });
});
      const result = await worker._checkBackendAccess();
      assert.equal(result.ok, s.expectedOk);
      assert.equal(result.reason, s.expectedReason);
    }
  });
});
