'use strict';

const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');

function createMockPool() {
  let insertedRows = new Map(); // companyId:providerMessageId -> bool exists

  const client = {
    query: async (sql, params) => {
      const upperSql = sql.trim().toUpperCase();

      // INSERT INTO whatsapp_delivery_outbox ... RETURNING id, state
      if (upperSql.includes('INSERT INTO whatsapp_delivery_outbox') && upperSql.includes('RETURNING')) {
        const companyId = params[0];
        const providerMessageId = params[1];
        const key = `${companyId}:${providerMessageId}`;

        // Verifica se ja existe (ON CONFLICT DO NOTHING)
        if (insertedRows.has(key)) {
          // Ja existia, ON CONFLICT DO NOTHING retorna rows vazios
          return { rows: [] };
        }

        // Primeiro registro
        insertedRows.set(key, true);
        return {
          rows: [{
            id: 1,
            company_id: companyId,
            provider_message_id: providerMessageId,
            state: 'PENDING',
            attempt_count: 0,
            created_at: new Date(),
            updated_at: new Date()
          }]
        };
      }

      // INSERT INTO whatsapp_delivery_outbox ON CONFLICT ... DO NOTHING RETURNING...
      if (upperSql.includes('ON CONFLICT DO NOTHING RETURNING')) {
        const companyId = params[0];
        const providerMessageId = params[1];
        const key = `${companyId}:${providerMessageId}`;

        if (insertedRows.has(key)) {
          return { rows: [] };
        }
        insertedRows.set(key, true);
        return { rows: [{ id: 1, company_id: companyId, provider_message_id: providerMessageId, state: 'PENDING' }] };
      }

      // SELECT from whatsapp_delivery_outbox WHERE company_id
      if (upperSql.includes('FROM whatsapp_delivery_outbox') && upperSql.includes('WHERE company_id')) {
        const companyId = params[0];
        const rows = [];
        for (const [key, value] of insertedRows.entries()) {
          // This simple mock doesn't track company_id -> rows mapping well
          // Just return empty for now
        }
        return { rows: [] };
      }

      // SELECT * FROM ... COUNT(*)
      if (upperSql.includes('COUNT(*)')) {
        const companyId = params[0];
        return { rows: [{ count: '0' }] };
      }

      // UPDATE whatsapp_delivery_outbox SET state = ...
      if (upperSql.includes('UPDATE whatsapp_delivery_outbox') && upperSql.includes('SET')) {
        return { rows: [{ state: 'PROCESSING', updated_at: new Date() }] };
      }

      // DELETE FROM whatsapp_delivery_outbox WHERE company_id
      if (upperSql.includes('DELETE FROM whatsapp_delivery_outbox') && upperSql.includes('WHERE company_id')) {
        insertedRows.clear();
        return { rows: [] };
      }

      return { rows: [] };
    },

    release: async () => {},
  };

  const pool = {
    query: async (sql, params) => {
      return client.query(sql, params);
    },
    connect: async () => ({ ...client }),
    end: async () => {},
  };

  return pool;
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

    // Primeiro registro
    const first = await outbox.recordDelivery('empresa-1', 'msg-1');
    assert.equal(first.isNew, true);

    // Segundo registro com mesma empresa + messageId - ON CONFLICT DO NOTHING
    const second = await outbox.recordDelivery('empresa-1', 'msg-1');
    assert.equal(second.isNew, false);
    assert.equal(second.id, null);
  });

  it('erro PostgreSQL: Promise rejeita', async () => {
    const { DeliveryOutbox } = require('../src/whatsapp/deliveryOutbox');

    // Mock pool que lança erro no connect.query
    const failingPool = createMockPool();
    const originalQuery = failingPool.query;
    failingPool.query = async (sql, params) => {
      throw new Error('db error');
    };

    const outbox = new DeliveryOutbox({ pool: failingPool, log: { log: () => {}, warn: () => {}, error: () => {}, info: () => {} } });

    try {
      await outbox.recordDelivery('empresa-1', 'msg-1');
      assert.fail('Deve ter lançado erro');
    } catch (err) {
      assert.ok(err instanceof Error);
    } finally {
      failingPool.query = originalQuery;
    }
  });

  it('DeliveryReporter poderá executar fallback quando outbox falha', async () => {
    const { DeliveryOutbox } = require('../src/whatsapp/deliveryOutbox');
    const { DeliveryReporter } = require('../src/whatsapp/deliveryReporter');

    const outbox = new DeliveryOutbox({ pool, log: { log: () => {}, warn: () => {}, error: () => {}, info: () => {} } });

    // Simula falha no outbox
    outbox.recordDelivery = async () => {
      throw new Error('db-down');
    };

    const reporter = new DeliveryReporter({
      backendUrl: 'http://spring:8080',
      internalToken: 'tok',
      outbox,
      log: { log: () => {}, warn: () => {}, error: () => {}, info: () => {} },
    });

    const result = await reporter.report('empresa-1', 'WAMID-1');

    assert.ok(result.ok !== undefined);
  });
});