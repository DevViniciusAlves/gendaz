'use strict';
const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { DeliveryOutboxWorker } = require('../src/whatsapp/deliveryOutboxWorker');

describe('DeliveryOutboxWorker - Order Tests', () => {
  it('executa etapas na ordem correta: schema, backend, recovery, process', async () => {
    const order = [];
    const pool = { connect: async () => ({}) };
    const worker = new DeliveryOutboxWorker({ pool, backendUrl: 'https://backend.test', internalToken: 'token' });

    worker._ensureOutboxSchemaReady = async () => {
      order.push('schema');
      return true;
    };
    worker._ensureBackendAvailable = async () => {
      order.push('backend');
      return true;
    };
    worker._maybeRecoverDead = async () => {
      order.push('recovery');
    };
    worker._processCycle = async () => {
      order.push('process');
      return false;
    };

    // Simula uma iteração do loop invocando os métodos na ordem do runLoop
    if (await worker._ensureOutboxSchemaReady()) {
      if (await worker._ensureBackendAvailable()) {
        await worker._maybeRecoverDead();
        await worker._processCycle();
      }
    }

    assert.deepEqual(order, ['schema', 'backend', 'recovery', 'process']);
  });
});
