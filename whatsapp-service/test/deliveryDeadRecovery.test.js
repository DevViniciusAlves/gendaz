'use strict';

// Recovery duravel de DEAD recuperavel -> PENDING -> DONE.
// Cobre: 401/403 no limite rapido, recovery posterior, 200 apos recovery,
// 400/404 permanentes, network/5xx/429 recuperaveis, idempotencia e restart.

const { describe, it, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const { DeliveryOutboxWorker, RECOVERABLE_DEAD_REASONS } = require('../src/whatsapp/deliveryOutboxWorker');
const { DeliveryOutbox } = require('../src/whatsapp/deliveryOutbox');

function minutesAgo(min) {
  return new Date(Date.now() - min * 60 * 1000);
}

function createMockPool(initialRows) {
  const state = { rows: initialRows.map((r, i) => ({ id: i + 1, recovery_count: 0, ...r })), seq: initialRows.length };
  const pool = {
    connect: async () => {
      const client = {
        query: async (sql, params = []) => {
          const n = sql.replace(/\s+/g, ' ').trim().toUpperCase();
          if (n === 'BEGIN' || n === 'COMMIT' || n === 'ROLLBACK') {
            return { rows: [] };
          }
          // INSERT outbox (recordDelivery)
          if (n.startsWith('INSERT INTO WHATSAPP_DELIVERY_OUTBOX')) {
            const [companyId, messageId] = params;
            const exists = state.rows.find(r => String(r.company_id) === String(companyId) && r.provider_message_id === messageId);
            if (exists) {
              return { rows: [] };
            }
            state.seq += 1;
            state.rows.push({
              id: state.seq, company_id: companyId, provider_message_id: messageId,
              state: 'PENDING', attempt_count: 0, recovery_count: 0, next_attempt_at: new Date(),
              locked_until: null, http_status: null, http_error_message: null,
              updated_at: new Date(),
            });
            return { rows: [{ id: state.seq, state: 'PENDING' }] };
          }
          // SELECT recovery: DEAD + motivos + idade minima + recovery_count
          if (n.includes("WHERE STATE = 'DEAD'")) {
            const maxAuthCycles = params[0];
            const maxTransientCycles = params[1];
            const minAgeMinutes = Number(params[2]);
            const limit = Number(params[3]);
            const cutoff = Date.now() - minAgeMinutes * 60 * 1000;
            const picked = state.rows
              .filter(r => {
                if (r.state !== 'DEAD') return false;
                if (r.updated_at.getTime() > cutoff) return false;
                if (r.http_error_message === 'auth_error' && r.recovery_count < maxAuthCycles) return true;
                if (['network_error', 'server_error', 'rate_limit', 'not_configured'].includes(r.http_error_message) && r.recovery_count < maxTransientCycles) return true;
                return false;
              })
              .sort((a, b) => a.updated_at - b.updated_at)
              .slice(0, limit);
            return { rows: picked.map(r => ({ id: r.id })) };
          }
          // SELECT claim principal PENDING/PROCESSING
          if (n.includes('FROM WHATSAPP_DELIVERY_OUTBOX') && n.includes('FOR UPDATE SKIP LOCKED')) {
            const now = Date.now();
            const row = state.rows
              .filter(r => (r.state === 'PENDING' && r.next_attempt_at.getTime() <= now)
                || (r.state === 'PROCESSING' && r.locked_until && r.locked_until.getTime() <= now))
              .sort((a, b) => a.next_attempt_at - b.next_attempt_at)[0];
            return { rows: row ? [{ id: row.id, company_id: row.company_id, provider_message_id: row.provider_message_id, attempt_count: row.attempt_count, recovery_count: row.recovery_count }] : [] };
          }
          if (n.includes("SET STATE = 'PROCESSING'")) {
            const row = state.rows.find(r => r.id === params[0]);
            row.state = 'PROCESSING';
            row.attempt_count += 1;
            row.last_attempt_at = new Date();
            row.locked_until = new Date(Date.now() + 60000);
            return { rows: [{ attempt_count: row.attempt_count }] };
          }
          if (n.includes("SET STATE = 'DONE'")) {
            state.rows.find(r => r.id === params[0]).state = 'DONE';
            return { rows: [] };
          }
          if (n.includes("SET STATE = 'DEAD'")) {
            const row = state.rows.find(r => r.id === params[params.length - 1]);
            row.state = 'DEAD';
            row.http_status = params[0];
            row.http_error_message = params[1];
            row.updated_at = new Date();
            return { rows: [] };
          }
          // UPDATE recovery: volta para PENDING com attempt zerado e incrementa recovery_count
          if (n.includes("SET STATE = 'PENDING', ATTEMPT_COUNT = 0")) {
            for (const id of params[0]) {
              const row = state.rows.find(r => r.id === id);
              row.state = 'PENDING';
              row.attempt_count = 0;
              row.recovery_count = (row.recovery_count || 0) + 1;
              row.next_attempt_at = new Date();
              row.locked_until = null;
              row.http_status = null;
              row.updated_at = new Date();
            }
            return { rowCount: params[0].length, rows: [] };
          }
          // UPDATE retry rapido: volta para PENDING com proxima tentativa
          if (n.includes("SET STATE = 'PENDING'")) {
            const row = state.rows.find(r => r.id === params[params.length - 1]);
            row.state = 'PENDING';
            row.next_attempt_at = params[0];
            row.http_status = params[1];
            row.http_error_message = params[2];
            row.updated_at = new Date();
            return { rows: [] };
          }
          throw new Error('query nao mockada: ' + sql);
        },
        release: () => {},
      };
      return client;
    },
    end: async () => {},
  };
  return { pool, state };
}

function logger() {
  return { log: () => {}, warn: () => {}, error: () => {}, info: () => {} };
}

function fetchWith(status) {
  globalThis.fetch = async () => ({ status, text: async () => '{}' });
}

describe('dead recovery', () => {
  afterEach(() => { globalThis.fetch = undefined; });

  it('exporta motivos recuperaveis sem incluir erro permanente', () => {
    for (const r of ['auth_error', 'network_error', 'server_error', 'rate_limit']) {
      assert.ok(RECOVERABLE_DEAD_REASONS.includes(r), r);
    }
    assert.ok(!RECOVERABLE_DEAD_REASONS.includes('permanent_error'));
    assert.ok(!RECOVERABLE_DEAD_REASONS.includes('unexpected_http_status'));
  });

  it('401 chega ao limite rapido, vira DEAD e nao perde o evento', async () => {
    const { pool, state } = createMockPool([{
      company_id: 7, provider_message_id: 'WAMID-A', state: 'PENDING',
      attempt_count: 0, next_attempt_at: minutesAgo(1), locked_until: null,
      http_status: null, http_error_message: null, updated_at: minutesAgo(60),
    }]);
    fetchWith(401);
    const worker = new DeliveryOutboxWorker({
      pool, backendUrl: 'http://spring:8080', internalToken: 't', log: logger(),
      recoveryIntervalMs: 0, recoveryBatch: 20, recoveryMinAgeMinutes: 15,
    });
    // 3 tentativas auth (15s, 60s, 300s): forca vencimento entre ciclos.
    for (let i = 0; i < 3; i++) {
      state.rows[0].state = 'PENDING';
      state.rows[0].next_attempt_at = minutesAgo(1);
      await worker._processCycle();
    }
    assert.equal(state.rows[0].state, 'DEAD');
    assert.equal(state.rows[0].http_error_message, 'auth_error');
  });

  it('DEAD auth_error antigo volta para PENDING e 200 leva a DONE', async () => {
    const { pool, state } = createMockPool([{
      company_id: 7, provider_message_id: 'WAMID-A', state: 'DEAD',
      attempt_count: 3, next_attempt_at: minutesAgo(60), locked_until: null,
      http_status: 401, http_error_message: 'auth_error', updated_at: minutesAgo(60),
    }]);
    const worker = new DeliveryOutboxWorker({
      pool, backendUrl: 'http://spring:8080', internalToken: 't', log: logger(),
      recoveryIntervalMs: 0, recoveryBatch: 20, recoveryMinAgeMinutes: 15,
    });
    const recovered = await worker._recoverDeadCycle();
    assert.equal(recovered, 1);
    assert.equal(state.rows[0].state, 'PENDING');
    assert.equal(state.rows[0].attempt_count, 0);

    fetchWith(200);
    await worker._processCycle();
    assert.equal(state.rows[0].state, 'DONE');
  });

  it('DEAD permanente (400) nunca e recuperado', async () => {
    const { pool, state } = createMockPool([{
      company_id: 7, provider_message_id: 'WAMID-P', state: 'DEAD',
      attempt_count: 1, next_attempt_at: minutesAgo(60), locked_until: null,
      http_status: 400, http_error_message: 'permanent_error', updated_at: minutesAgo(120),
    }]);
    const worker = new DeliveryOutboxWorker({
      pool, backendUrl: 'http://spring:8080', internalToken: 't', log: logger(),
      recoveryIntervalMs: 0, recoveryBatch: 20, recoveryMinAgeMinutes: 15,
    });
    assert.equal(await worker._recoverDeadCycle(), 0);
    assert.equal(state.rows[0].state, 'DEAD');
  });

  it('DEAD recuperavel recente respeita idade minima (sem hot loop)', async () => {
    const { pool, state } = createMockPool([{
      company_id: 7, provider_message_id: 'WAMID-R', state: 'DEAD',
      attempt_count: 7, next_attempt_at: new Date(), locked_until: null,
      http_status: 500, http_error_message: 'server_error', updated_at: new Date(),
    }]);
    const worker = new DeliveryOutboxWorker({
      pool, backendUrl: 'http://spring:8080', internalToken: 't', log: logger(),
      recoveryIntervalMs: 0, recoveryBatch: 20, recoveryMinAgeMinutes: 15,
    });
    assert.equal(await worker._recoverDeadCycle(), 0);
    assert.equal(state.rows[0].state, 'DEAD');
  });

  it('recovery respeita lote maximo por ciclo', async () => {
    const rows = [1, 2, 3].map(i => ({
      company_id: 7, provider_message_id: 'WAMID-' + i, state: 'DEAD',
      attempt_count: 7, next_attempt_at: minutesAgo(60), locked_until: null,
      http_status: 0, http_error_message: 'network_error', updated_at: minutesAgo(60),
    }));
    const { pool, state } = createMockPool(rows);
    const worker = new DeliveryOutboxWorker({
      pool, backendUrl: 'http://spring:8080', internalToken: 't', log: logger(),
      recoveryIntervalMs: 0, recoveryBatch: 2, recoveryMinAgeMinutes: 15,
    });
    assert.equal(await worker._recoverDeadCycle(), 2);
    assert.equal(state.rows.filter(r => r.state === 'PENDING').length, 2);
    assert.equal(state.rows.filter(r => r.state === 'DEAD').length, 1);
  });

  it('network_error, server_error e rate_limit sao recuperaveis', async () => {
    const rows = ['network_error', 'server_error', 'rate_limit'].map((reason, i) => ({
      company_id: 7, provider_message_id: 'WAMID-T' + i, state: 'DEAD',
      attempt_count: 7, next_attempt_at: minutesAgo(60), locked_until: null,
      http_status: 0, http_error_message: reason, updated_at: minutesAgo(60),
    }));
    const { pool, state } = createMockPool(rows);
    const worker = new DeliveryOutboxWorker({
      pool, backendUrl: 'http://spring:8080', internalToken: 't', log: logger(),
      recoveryIntervalMs: 0, recoveryBatch: 20, recoveryMinAgeMinutes: 15,
    });
    assert.equal(await worker._recoverDeadCycle(), 3);
    assert.ok(state.rows.every(r => r.state === 'PENDING'));
  });

  it('outbox continua idempotente apos restart (ON CONFLICT)', async () => {
    const { pool, state } = createMockPool([]);
    const outbox = new DeliveryOutbox({ pool, log: logger() });
    const first = await outbox.recordDelivery(9, 'WAMID-X');
    assert.equal(first.isNew, true);
    // Simula restart: nova instancia, mesma base.
    const outbox2 = new DeliveryOutbox({ pool, log: logger() });
    const second = await outbox2.recordDelivery(9, 'WAMID-X');
    assert.equal(second.isNew, false);
    assert.equal(state.rows.length, 1);
  });

  it('auth DEAD pode recuperar apenas ate o limite global (1 ciclo)', async () => {
    const { pool, state } = createMockPool([{
      company_id: 7, provider_message_id: 'WAMID-AUTH-LIMIT', state: 'DEAD',
      attempt_count: 3, recovery_count: 1, next_attempt_at: minutesAgo(60), locked_until: null,
      http_status: 401, http_error_message: 'auth_error', updated_at: minutesAgo(60),
    }]);
    const worker = new DeliveryOutboxWorker({
      pool, backendUrl: 'http://spring:8080', internalToken: 't', log: logger(),
      recoveryIntervalMs: 0, recoveryBatch: 20, recoveryMinAgeMinutes: 15,
    });
    // recovery_count = 1, should not be recovered as limit is 1 (MAX_AUTH_RECOVERY_CYCLES = 1)
    const recovered = await worker._recoverDeadCycle();
    assert.equal(recovered, 0);
    assert.equal(state.rows[0].state, 'DEAD');
  });

  it('transitório esgota limite apos 2 recoveries', async () => {
    const { pool, state } = createMockPool([{
      company_id: 7, provider_message_id: 'WAMID-TRANS-LIMIT', state: 'DEAD',
      attempt_count: 7, recovery_count: 2, next_attempt_at: minutesAgo(60), locked_until: null,
      http_status: 500, http_error_message: 'server_error', updated_at: minutesAgo(60),
    }]);
    const worker = new DeliveryOutboxWorker({
      pool, backendUrl: 'http://spring:8080', internalToken: 't', log: logger(),
      recoveryIntervalMs: 0, recoveryBatch: 20, recoveryMinAgeMinutes: 15,
    });
    // recovery_count = 2, should not be recovered as limit is 2 (MAX_TRANSIENT_RECOVERY_CYCLES = 2)
    const recovered = await worker._recoverDeadCycle();
    assert.equal(recovered, 0);
    assert.equal(state.rows[0].state, 'DEAD');
  });

  it('transitório pode recuperar se recovery_count < 2 e incrementa', async () => {
    const { pool, state } = createMockPool([{
      company_id: 7, provider_message_id: 'WAMID-TRANS-OK', state: 'DEAD',
      attempt_count: 7, recovery_count: 1, next_attempt_at: minutesAgo(60), locked_until: null,
      http_status: 500, http_error_message: 'server_error', updated_at: minutesAgo(60),
    }]);
    const worker = new DeliveryOutboxWorker({
      pool, backendUrl: 'http://spring:8080', internalToken: 't', log: logger(),
      recoveryIntervalMs: 0, recoveryBatch: 20, recoveryMinAgeMinutes: 15,
    });
    // recovery_count = 1, should be recovered (limit is 2) and incremented to 2
    const recovered = await worker._recoverDeadCycle();
    assert.equal(recovered, 1);
    assert.equal(state.rows[0].state, 'PENDING');
    assert.equal(state.rows[0].recovery_count, 2);
  });
});
