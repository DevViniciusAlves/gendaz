'use strict';
const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { DeliveryOutboxWorker, REQUIRED_OUTBOX_COLUMNS } = require('../src/whatsapp/deliveryOutboxWorker');

describe('DeliveryOutboxWorker - Schema Guard Tests', () => {
  it('schema guard retorna false quando recovery_count está faltando', async () => {
    const pool = {
      query: async (sql) => {
        if (sql.includes('information_schema.columns')) {
          return { rows: [] };
        }
        return { rows: [] };
      }
    };
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'https://backend.test', internalToken: 'token' });
    const ready = await worker._ensureOutboxSchemaReady();
    assert.equal(ready, false);
    assert.equal(worker._schemaReady, false);
  });

  it('cache de schema guard evita consultas repetidas dentro da janela', async () => {
    let queriesCount = 0;
    const pool = {
      query: async (sql, params) => {
        if (sql.includes('information_schema.columns')) {
          queriesCount += 1;
          return { rows: REQUIRED_OUTBOX_COLUMNS.map(col => ({ column_name: col })) };
        }
        return { rows: [] };
      }
    };
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'https://backend.test', internalToken: 'token' });
    
    // Força estado inicial de não ready
    worker._schemaReady = false;
    
    // Faz a primeira chamada
    await worker._ensureOutboxSchemaReady();
    
    // Verifica se ficou ready
    assert.equal(worker._schemaReady, true);
    
    // Agora deveria estar cacheado, consulta não deve ser feita novamente
    await worker._ensureOutboxSchemaReady();

    assert.equal(queriesCount, 1);
  });

  it('detecta erro postgres 42703 como mismatch de schema', () => {
    const worker = new DeliveryOutboxWorker({ pool: {}, backendUrl: 'https://backend.test', internalToken: 'token' });
    assert.equal(worker._isSchemaMismatchError({ code: '42703' }), true);
  });

  it('detecta erro postgres 42P01 como mismatch de schema', () => {
    const worker = new DeliveryOutboxWorker({ pool: {}, backendUrl: 'https://backend.test', internalToken: 'token' });
    assert.equal(worker._isSchemaMismatchError({ code: '42P01' }), true);
  });

  it('não classifica erro postgres não estrutural (23505) como mismatch de schema', () => {
    const worker = new DeliveryOutboxWorker({ pool: {}, backendUrl: 'https://backend.test', internalToken: 'token' });
    assert.equal(worker._isSchemaMismatchError({ code: '23505' }), false);
  });
});
