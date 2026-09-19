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
      query: async (sql, params) => { state.queries.push({ sql, params }); 
        if(sql.includes('SELECT id FROM whatsapp_delivery_outbox WHERE state = \'DEAD\'')) return { rows: [] };
        return { rows: [] }; 
      },
      release: () => {},
    }),
    end: async () => {},
  };
  return { pool, state };
}
function createLogger() { return { log: () => {}, warn: () => {}, error: () => {}, info: () => {} }; }

describe('DeliveryOutboxWorker - Permanent/Done Tests', () => {
  let pool, state, log;
  beforeEach(() => { const mock = createMockPool(); pool = mock.pool; state = mock.state; log = createLogger(); });

  it('Permanent errors 400, 404, 405, 422 -> DEAD', async () => {
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'https://backend.test', internalToken: 'test-token', log });
    const errors = [400, 404, 405, 422];
    for (const status of errors) {
        state.row.state = 'PROCESSING';
        await worker._handleResponse(state.row, status, {});
        assert.equal(state.row.state, 'DEAD', `Status ${status} should be DEAD`);
    }
  });

  it('200/202 -> DONE, then recovery -> remains DONE', async () => {
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'https://backend.test', internalToken: 'test-token', log });
    
    // Test DONE
    state.row.state = 'PROCESSING';
    await worker._handleResponse(state.row, 200, {});
    assert.equal(state.row.state, 'DONE');
    
    // Run recovery
    await worker._recoverDeadCycle();
    assert.equal(state.row.state, 'DONE', 'Should remain DONE after recovery cycle');
  });
});
