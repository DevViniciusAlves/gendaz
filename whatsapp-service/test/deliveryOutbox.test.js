'use strict';

const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');

function normalizeSql(sql) {
  return sql
    .replace(/\s+/g, ' ')
    .trim()
    .toUpperCase();
}

function createMockPool() {
  const insertedRows = new Map();

  const client = {
    query: async (sql, params) => {
      const normalizedSql = normalizeSql(sql);

      if (
        normalizedSql.includes('INSERT INTO WHATSAPP_DELIVERY_OUTBOX') &&
        normalizedSql.includes('ON CONFLICT (COMPANY_ID, PROVIDER_MESSAGE_ID) DO NOTHING') &&
        normalizedSql.includes('RETURNING ID, STATE')
      ) {
        const companyId = params[0];
        const providerMessageId = params[1];
        const key = `${companyId}:${providerMessageId}`;

        if (insertedRows.has(key)) {
          return { rows: [] };
        }

        insertedRows.set(key, true);
        return {
          rows: [{ id: 1, state: 'PENDING' }]
        };
      }

      return { rows: [] };
    },
    release: () => {},
  };

  const pool = {
    connect: async () => client,
    end: async () => {},
  };

  return pool;
}

function createFailingPool() {
  return {
    connect: async () => ({
      query: async () => { throw new Error('db error'); },
      release: () => {},
    }),
    end: async () => {},
  };
}

describe('DeliveryOutbox', () => {
  let pool;

  beforeEach(() => {
    pool = createMockPool();
  });

  it('registro novo: isNew=true', async () => {
    const { DeliveryOutbox } = require('../src/whatsapp/deliveryOutbox');
    const outbox = new DeliveryOutbox({ pool, log: { log: () => {}, warn: () => {}, error: () => {}, info: () => {} } });

    const result = await outbox.recordDelivery('empresa-1', 'msg-1');

    assert.equal(result.isNew, true);
    assert.equal(result.id, 1);
  });

  it('registro duplicado: isNew=false', async () => {
    const { DeliveryOutbox } = require('../src/whatsapp/deliveryOutbox');
    const outbox = new DeliveryOutbox({ pool, log: { log: () => {}, warn: () => {}, error: () => {}, info: () => {} } });

    const first = await outbox.recordDelivery('empresa-1', 'msg-1');
    assert.equal(first.isNew, true);

    const second = await outbox.recordDelivery('empresa-1', 'msg-1');
    assert.equal(second.isNew, false);
    assert.equal(second.id, null);
  });

  it('erro PostgreSQL: Promise rejeita', async () => {
    const { DeliveryOutbox } = require('../src/whatsapp/deliveryOutbox');

    const failingPool = createFailingPool();
    const outbox = new DeliveryOutbox({ pool: failingPool, log: { log: () => {}, warn: () => {}, error: () => {}, info: () => {} } });

    await assert.rejects(
      () => outbox.recordDelivery('empresa-1', 'msg-1'),
      /db error/
    );
  });

  it('DeliveryReporter poderá executar fallback quando outbox falha', async () => {
    const { DeliveryReporter } = require('../src/whatsapp/deliveryReporter');

    const outbox = {
      recordDelivery: async () => { throw new Error('db-down'); }
    };

    let fetchCalls = 0;
    const fetchFn = async () => {
      fetchCalls += 1;
      return { ok: true, status: 200 };
    };

    const reporter = new DeliveryReporter({
      backendUrl: 'http://spring:8080',
      internalToken: 'tok',
      outbox,
      fetchFn,
      log: { log: () => {}, warn: () => {}, error: () => {}, info: () => {} },
    });

    const result = await reporter.report('empresa-1', 'WAMID-1');

    assert.equal(fetchCalls, 1);
    assert.equal(result.ok, true);
  });
});
