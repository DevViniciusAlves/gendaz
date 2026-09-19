'use strict';
const { describe, it, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const { DeliveryOutboxWorker } = require('../src/whatsapp/deliveryOutboxWorker');

describe('DeliveryOutboxWorker - Order Tests', () => {
  it('executa etapas na ordem correta: schema, backend, recovery, process via _runLoop', async () => {
    const order = [];
    let iteration = 0;

    const pool = {
      connect: async () => ({
        query: async () => ({ rows: [] }),
        release: () => {},
      }),
    };

    const worker = new DeliveryOutboxWorker({
      pool,
      backendUrl: 'https://backend.test',
      internalToken: 'token',
      log: { log: () => {}, warn: () => {}, error: () => {}, info: () => {} },
    });

    const originalSchemaReady = worker._ensureOutboxSchemaReady.bind(worker);
    const originalBackendAvailable = worker._ensureBackendAvailable.bind(worker);
    const originalRecoverDead = worker._maybeRecoverDead.bind(worker);
    const originalProcessCycle = worker._processCycle.bind(worker);

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
      iteration += 1;
      if (iteration >= 1) {
        worker.stop();
      }
      return false;
    };

    await worker.start();
    await new Promise(resolve => setTimeout(resolve, 100));

    assert.deepEqual(order, ['schema', 'backend', 'recovery', 'process']);
  });
});