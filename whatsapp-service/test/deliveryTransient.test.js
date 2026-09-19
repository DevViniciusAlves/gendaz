'use strict';
const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { DeliveryOutboxWorker } = require('../src/whatsapp/deliveryOutboxWorker');

function createMockPool() {
  const state = {
    row: { id: 1, company_id: 1, provider_message_id: 'WAMID-1', state: 'PENDING', attempt_count: 0, recovery_count: 0, http_status: null, http_error_message: null },
    queries: [],
  };
  const pool = {
    connect: async () => ({
      query: async (sql, params) => { state.queries.push({ sql, params }); return { rows: [] }; },
      release: () => {},
    }),
    end: async () => {},
  };
  return { pool, state };
}
function createLogger() { return { log: () => {}, warn: () => {}, error: () => {}, info: () => {} }; }

describe('DeliveryOutboxWorker - Transient Tests', () => {
  let pool, state, log;
  beforeEach(() => { const mock = createMockPool(); pool = mock.pool; state = mock.state; log = createLogger(); });

  it('Transient errors: 7 attempts -> DEAD', async () => {
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'https://backend.test', internalToken: 'test-token', log });
    
    for (let i = 1; i <= 6; i++) {
        state.row.state = 'PROCESSING'; state.row.attempt_count = i;
        await worker._handleResponse(state.row, 503, {});
        assert.equal(state.row.state, 'PENDING', `Failed at attempt ${i}`);
    }
    
    state.row.state = 'PROCESSING'; state.row.attempt_count = 7;
    await worker._handleResponse(state.row, 503, {});
    assert.equal(state.row.state, 'DEAD');
  });
});
