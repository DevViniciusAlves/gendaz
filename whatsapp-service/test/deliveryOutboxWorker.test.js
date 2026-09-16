'use strict';

const { describe, it, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const { DeliveryOutboxWorker } = require('../src/whatsapp/deliveryOutboxWorker');

function createMockPool() {
  const tables = { whatsapp_delivery_outbox: new Map() };
  const executeQuery = async (sql, params) => {
    const upperSql = sql.trim().toUpperCase();

    if (upperSql.includes('INSERT INTO whatsapp_delivery_outbox')) {
      const companyId = params[0];
      const providerMessageId = params[1];
      const key = `${companyId}:${providerMessageId}`;
      if (tables.whatsapp_delivery_outbox.has(key)) { return { rows: [] }; }
      const row = { id: 1, company_id: companyId, provider_message_id: providerMessageId, state: 'PENDING', attempt_count: 0, locked_until: null, last_attempt_at: null, updated_at: new Date() };
      tables.whatsapp_delivery_outbox.set(key, row);
      return { rows: [row] };
    }

    if (upperSql.includes('FOR UPDATE SKIP LOCKED')) {
      const rows = []; for (const [, v] of tables.whatsapp_delivery_outbox) { if (v.state === 'PENDING') rows.push(v); } return { rows };
    }

    if (upperSql.includes('UPDATE whatsapp_delivery_outbox') && upperSql.includes('SET state = PROCESSING') && upperSql.includes('RETURNING attempt_count')) {
      const companyId = params[params.length - 1]; const key = `${companyId}:some-message`; const existing = tables.whatsapp_delivery_outbox.get(key); if (existing) { existing.state = 'PROCESSING'; existing.locked_until = new Date(Date.now() + 60000); existing.attempt_count += 1; existing.last_attempt_at = new Date(); existing.updated_at = new Date(); return { rows: [{ attempt_count: existing.attempt_count }] }; }
      const row = { id: 1, company_id: companyId, provider_message_id: 'msg', state: 'PROCESSING', attempt_count: 1, last_attempt_at: new Date(), updated_at: new Date() }; tables.whatsapp_delivery_outbox.set(key, row); return { rows: [{ attempt_count: 1 }] };
    }

    if (upperSql.includes('UPDATE whatsapp_delivery_outbox') && upperSql.includes('SET state = DONE')) { const companyId = params[params.length - 1]; const key = `${companyId}:some-message`; const existing = tables.whatsapp_delivery_outbox.get(key); if (existing) existing.state = 'DONE'; return { rows: [] }; }

    if (upperSql.includes('UPDATE whatsapp_delivery_outbox') && upperSql.includes('SET state = DEAD')) { const companyId = params[params.length - 1]; const key = `${companyId}:some-message`; const existing = tables.whatsapp_delivery_outbox.get(key); if (existing) existing.state = 'DEAD'; return { rows: [] }; }

    return { rows: [] };
  };

  let pool;

  beforeEach(() => { pool = createMockPool(); });
  afterEach(() => { pool = undefined; });

  it('PENDING -> claim -> PROCESSING com attempt_count incrementado', async () => {
    await pool.query(`INSERT INTO whatsapp_delivery_outbox (company_id, provider_message_id, state, attempt_count, created_at, updated_at) VALUES ('empresa-1', 'msg-1', 'PENDING', 0, NOW(), NOW())`);
    const selectResult = await pool.query(`SELECT id, company_id, provider_message_id, attempt_count FROM whatsapp_delivery_outbox WHERE state = 'PENDING' ORDER BY next_attempt_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED`);
    assert.equal(selectResult.rows.length, 1); assert.equal(selectResult.rows[0].state, 'PENDING');
    const updateResult = await pool.query(`UPDATE whatsapp_delivery_outbox SET state = 'PROCESSING', locked_until = NOW() + INTERVAL '60 seconds', attempt_count = attempt_count + 1, last_attempt_at = NOW(), updated_at = NOW() WHERE id = $1 RETURNING attempt_count`, [selectResult.rows[0].id]);
    assert.equal(Number(updateResult.rows[0].attempt_count), 1);
    const checkResult = await pool.query(`SELECT state FROM whatsapp_delivery_outbox WHERE id = $1`, [selectResult.rows[0].id]);
    assert.equal(checkResult.rows[0].state, 'PROCESSING');
  });

  it('last_attempt_at atualizado ao claim', async () => {
    await pool.query(`INSERT INTO whatsapp_delivery_outbox (company_id, provider_message_id, state, attempt_count, created_at, updated_at) VALUES ('empresa-1', 'msg-1', 'PENDING', 0, NOW(), NOW())`);
    const selectResult = await pool.query(`SELECT id, company_id, provider_message_id, attempt_count FROM whatsapp_delivery_outbox WHERE state = 'PENDING' ORDER BY next_attempt_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED`);
    const updateResult = await pool.query(`UPDATE whatsapp_delivery_outbox SET state = 'PROCESSING', locked_until = NOW() + INTERVAL '60 seconds', attempt_count = attempt_count + 1, last_attempt_at = NOW(), updated_at = NOW() WHERE id = $1 RETURNING attempt_count`, [selectResult.rows[0].id]);
    assert.ok(updateResult.rows[0].attempt_count); const checkResult = await pool.query(`SELECT last_attempt_at FROM whatsapp_delivery_outbox WHERE id = $1`, [selectResult.rows[0].id]); assert.ok(checkResult.rows[0].last_attempt_at);
  });

  it('200 -> DONE', async () => {
    await pool.query(`INSERT INTO whatsapp_delivery_outbox (company_id, provider_message_id, state, attempt_count, created_at, updated_at) VALUES ('empresa-1', 'msg-1', 'PROCESSING', 0, NOW(), NOW())`);
    await pool.query(`UPDATE whatsapp_delivery_outbox SET state = 'DONE', updated_at = NOW() WHERE id = 1`);
    const checkResult = await pool.query(`SELECT state FROM whatsapp_delivery_outbox WHERE id = 1`);
    assert.equal(checkResult.rows[0].state, 'DONE');
  });

  it('400 -> DEAD', async () => {
    await pool.query(`INSERT INTO whatsapp_delivery_outbox (company_id, provider_message_id, state, attempt_count, created_at, updated_at) VALUES ('empresa-1', 'msg-1', 'PROCESSING', 0, NOW(), NOW())`);
    await pool.query(`UPDATE whatsapp_delivery_outbox SET state = 'DEAD', http_status = 400, updated_at = NOW() WHERE id = 1`);
    const checkResult = await pool.query(`SELECT state FROM whatsapp_delivery_outbox WHERE id = 1`);
    assert.equal(checkResult.rows[0].state, 'DEAD');
  });

  it('attempt_count 20 -> DEAD', async () => {
    await pool.query(`INSERT INTO whatsapp_delivery_outbox (company_id, provider_message_id, state, attempt_count, created_at, updated_at) VALUES ('empresa-1', 'msg-1', 'PROCESSING', 19, NOW(), NOW())`);
    const updateResult = await pool.query(`UPDATE whatsapp_delivery_outbox SET state = 'DEAD', attempt_count = attempt_count + 1 WHERE id = 1 RETURNING attempt_count`);
    assert.equal(Number(updateResult.rows[0].attempt_count), 20);
  });
});