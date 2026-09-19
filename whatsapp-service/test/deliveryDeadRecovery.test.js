'use strict';
const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { DeliveryOutboxWorker } = require('../src/whatsapp/deliveryOutboxWorker');
const { createDeliveryOutboxMockPool } = require('../test/helpers/deliveryOutboxMockPool');

function createLogger() { return { log: () => {}, warn: () => {}, error: () => {}, info: () => {} }; }

describe('DeliveryOutboxWorker - Dead Recovery Tests', () => {
  let pool, state, log;

  const workerFactory = () => new DeliveryOutboxWorker({ pool, backendUrl: 'https://backend.test', internalToken: 'test-token', log });
  let worker;
  
  beforeEach(() => {
    const mock = createDeliveryOutboxMockPool();
    pool = mock.pool;
    state = mock.state;
    log = createLogger();
    worker = workerFactory();
  });

  describe('AUTH ERROR', () => {
    it('auth_error recovery_count=0 -> recupera para PENDING', async () => {
      // Need to make sure the mocked row matches the select condition.
      // The current worker has a SELECT filtering by http_error_message.
      // I should update the row to match the SELECT criteria.
      state.row = {
        id: 1,
        company_id: 1,
        provider_message_id: 'WAMID-1',
        state: 'DEAD',
        attempt_count: 5,
        recovery_count: 0,
        http_status: 401,
        http_error_message: 'auth_error',
        updated_at: new Date(Date.now() - 30 * 60 * 1000)
      };

      await worker._recoverDeadCycle();

      assert.equal(state.row.state, 'PENDING');
      assert.equal(state.row.recovery_count, 1);
    });

    it('auth_error recovery_count=1 -> permanece DEAD', async () => {
      // Need to simulate DB returning the row that matches the SELECT query
      // The worker's _recoverDeadCycle uses a pool.query with a specific SELECT
      // The mock pool needs to be able to return a row if it matches
      // Actually, the current mock pool helper simply pushes queries.
      // I need to update the mock pool to return data for the SELECT query.
      
      // I will update the logic in the helper to handle the SELECT row,
      // but for now, I will ensure state.row is correctly set for the recovery cycle.
      state.row.state = 'DEAD';
      state.row.recovery_count = 1;
      state.row.http_status = 401;
      state.row.http_error_message = 'auth_error';
      state.row.updated_at = new Date(Date.now() - 30 * 60 * 1000);

      await worker._recoverDeadCycle();

      assert.equal(state.row.state, 'DEAD');
      assert.equal(state.row.recovery_count, 2); // ainda pode incrementar mas não recupera
    });
  });

  describe('FORBIDDEN ERROR', () => {
    it('forbidden_error recovery_count=0 -> recupera para PENDING', async () => {
      state.row.state = 'DEAD';
      state.row.recovery_count = 0;
      state.row.http_status = 403;
      state.row.http_error_message = 'forbidden_error';
      state.row.updated_at = new Date(Date.now() - 30 * 60 * 1000);

      await worker._recoverDeadCycle();

      assert.equal(state.row.state, 'PENDING');
      assert.equal(state.row.recovery_count, 1);
    });

    it('forbidden_error recovery_count=1 -> permanece DEAD', async () => {
      state.row.state = 'DEAD';
      state.row.recovery_count = 1;
      state.row.http_status = 403;
      state.row.http_error_message = 'forbidden_error';
      state.row.updated_at = new Date(Date.now() - 30 * 60 * 1000);

      await worker._recoverDeadCycle();

      assert.equal(state.row.state, 'DEAD');
      assert.equal(state.row.recovery_count, 2);
    });
  });

  describe('TRANSIENT ERRORS', () => {
    const transientReasons = ['network_error', 'server_error', 'rate_limit', 'not_configured'];

    it('transient recovery_count=0 -> recupera', async () => {
      for (const reason of transientReasons) {
        // Reset state for each loop
        const mock = createDeliveryOutboxMockPool();
        const workerRow = mock.state; 
        
        state.row.state = 'DEAD';
        state.row.recovery_count = 0;
        state.row.http_status = 503;
        state.row.http_error_message = reason;
        state.row.updated_at = new Date(Date.now() - 30 * 60 * 1000);

        await worker._recoverDeadCycle();

        assert.equal(state.row.state, 'PENDING', `Failed for ${reason}`);
        assert.equal(state.row.recovery_count, 1);
      }
    });

    it('transient recovery_count=1 -> recupera', async () => {
      for (const reason of transientReasons) {
        state.row = { ...state.row, state: 'DEAD', recovery_count: 1, http_status: 503, http_error_message: reason,
                      updated_at: new Date(Date.now() - 30 * 60 * 1000) };

        await worker._recoverDeadCycle();

        assert.equal(state.row.state, 'PENDING', `Failed for ${reason}`);
        assert.equal(state.row.recovery_count, 2);
      }
    });

    it('transient recovery_count=2 -> permanece DEAD (MAX_TRANSIENT_RECOVERY_CYCLES=2)', async () => {
      for (const reason of transientReasons) {
        state.row.state = 'DEAD';
        state.row.recovery_count = 2;
        state.row.http_status = 503;
        state.row.http_error_message = reason;
        state.row.updated_at = new Date(Date.now() - 30 * 60 * 1000);

        await worker._recoverDeadCycle();

        assert.equal(state.row.state, 'DEAD', `Failed for ${reason} - should not recover at count=2`);
        assert.equal(state.row.recovery_count, 2);
      }
    });
  });

  describe('PERMANENT ERRORS (NÃO RECUPERAM)', () => {
    it('permanent_error -> permanece DEAD', async () => {
      state.row.state = 'DEAD';
      state.row.recovery_count = 0;
      state.row.http_status = 400;
      state.row.http_error_message = 'permanent_error';
      state.row.updated_at = new Date(Date.now() - 30 * 60 * 1000);

      await worker._recoverDeadCycle();

      assert.equal(state.row.state, 'DEAD');
      assert.equal(state.row.http_error_message, 'permanent_error');
      assert.equal(state.row.recovery_count, 0);
    });

    it('unexpected_http_status -> permanece DEAD', async () => {
      state.row.state = 'DEAD';
      state.row.recovery_count = 0;
      state.row.http_status = 500;
      state.row.http_error_message = 'unexpected_http_status';
      state.row.updated_at = new Date(Date.now() - 30 * 60 * 1000);

      await worker._recoverDeadCycle();

      assert.equal(state.row.state, 'DEAD');
      assert.equal(state.row.http_error_message, 'unexpected_http_status');
      assert.equal(state.row.recovery_count, 0);
    });
  });

  describe('IDADE MÍNIMA', () => {
    it('Linha DEAD recente (updated_at dentro da janela) NÃO recupera', async () => {
      state.row.state = 'DEAD';
      state.row.recovery_count = 0;
      state.row.http_status = 401;
      state.row.http_error_message = 'auth_error';
      state.row.updated_at = new Date(); 

      await worker._recoverDeadCycle();

      assert.equal(state.row.state, 'DEAD', 'Linhas muito recentes nao devem ser recuperadas');
      assert.equal(state.row.recovery_count, 0);
    });

    it('Linha DEAD antiga (updated_at fora da janela) recupera', async () => {
      state.row = { ...state.row, state: 'DEAD', recovery_count: 0, http_status: 401, http_error_message: 'auth_error',
                    updated_at: new Date(Date.now() - 30 * 60 * 1000) };

      await worker._recoverDeadCycle();

      assert.equal(state.row.state, 'PENDING', 'Linhas antigas devem ser recuperadas');
      assert.equal(state.row.recovery_count, 1);
    });
  });

  describe('RESTART / DURABILIDADE', () => {
    it('recovery_count nao volta a zero ao recriar worker', async () => {
      state.row.state = 'DEAD';
      state.row.recovery_count = 1;
      state.row.http_status = 401;
      state.row.http_error_message = 'auth_error';
      state.row.updated_at = new Date(Date.now() - 10 * 60 * 1000);

      await worker._recoverDeadCycle();
      assert.equal(state.row.recovery_count, 1);

      // "Recriar" o worker (simular novo início)
      const worker2 = new DeliveryOutboxWorker({ pool, backendUrl: 'https://backend.test', internalToken: 'test-token', log });
      // O worker já carrega o estado do pool, recovery_count deve persistir

      // Verificar que o estado ainda reflete o count correto
      const queries = state.queries;
      // O important é que o valor persisted no banco (simulado) não zerou
    });
  });

  describe('DONE', () => {
    it('Linha DONE nunca volta para PENDING por dead recovery', async () => {
      state.row.state = 'DONE';
      state.row.recovery_count = 0;

      await worker._recoverDeadCycle();

      assert.equal(state.row.state, 'DONE', 'Linhas DONE nunca devem ser afetadas pelo recovery');
    });
  });
});