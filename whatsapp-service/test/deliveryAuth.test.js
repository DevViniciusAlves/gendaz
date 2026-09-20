'use strict';
const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { DeliveryOutboxWorker } = require('../src/whatsapp/deliveryOutboxWorker');

const { createDeliveryOutboxMockPool } = require('./helpers/deliveryOutboxMockPool');

function createMockPool() {
  return createDeliveryOutboxMockPool();
}
function createLogger() { return { log: () => {}, warn: () => {}, error: () => {}, info: () => {} }; }

describe('DeliveryOutboxWorker - Auth Tests', () => {
  let pool, state, log;
  beforeEach(() => { const mock = createMockPool(); pool = mock.pool; state = mock.state; log = createLogger(); });

  it('401 deve ser auth_error', async () => {
    state.row.state = 'PROCESSING';
    state.row.attempt_count = 1;
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'https://backend.test', internalToken: 'test-token', log });
    await worker._handleResponse(state.row, 401, {});
    assert.equal(state.row.http_error_message, 'auth_error');
  });

  it('403 deve ser forbidden_error', async () => {
    state.row.state = 'PROCESSING';
    state.row.attempt_count = 1;
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'https://backend.test', internalToken: 'test-token', log });
    await worker._handleResponse(state.row, 403, {});
    assert.equal(state.row.http_error_message, 'forbidden_error');
  });

  it('Auth limits: attempt 1,2 PENDING, 3 DEAD', async () => {
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'https://backend.test', internalToken: 'test-token', log });
    
    // Attempt 1 -> PENDING
    state.row.state = 'PROCESSING'; state.row.attempt_count = 1;
    await worker._handleResponse(state.row, 401, {});
    assert.equal(state.row.state, 'PENDING');
    
    // Attempt 2 -> PENDING
    state.row.state = 'PROCESSING'; state.row.attempt_count = 2;
    await worker._handleResponse(state.row, 401, {});
    assert.equal(state.row.state, 'PENDING');
    
    // Attempt 3 -> DEAD
    state.row.state = 'PROCESSING'; state.row.attempt_count = 3;
    await worker._handleResponse(state.row, 401, {});
    assert.equal(state.row.state, 'DEAD');
  });
});
