'use strict';

const { describe, it, before, after, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { Buffer } = require('buffer');
const { PostgresAuthStateStore } = require('../src/whatsapp/postgresAuthStore');
const { initAuthCreds, BufferJSON } = require('@whiskeysockets/baileys');
const { proto } = require('@whiskeysockets/baileys').WAProto;

// Mock pool for unit tests
function createMockPool() {
  const tables = {
    whatsapp_auth_sessions: new Map(),
    whatsapp_auth_keys: new Map(),
  };
  const executeQuery = async (sql, params) => {
    let inTransaction = false;
    const txData = { sessions: new Map(), keys: new Map() };
    // We need to handle transactions specially - for simplicity, we'll execute directly
    // In a real pool, BEGIN/COMMIT/ROLLBACK would manage transaction state
    
    if (sql.trim().toUpperCase() === 'BEGIN') {
      return { rows: [] };
    }
    if (sql.trim().toUpperCase() === 'COMMIT') {
      // Apply transaction data
      for (const [key, value] of txData.sessions) {
        tables.whatsapp_auth_sessions.set(key, value);
      }
      for (const [key, value] of txData.keys) {
        tables.whatsapp_auth_keys.set(key, value);
      }
      return { rows: [] };
    }
    if (sql.trim().toUpperCase() === 'ROLLBACK') {
      return { rows: [] };
    }

    // SELECT from whatsapp_auth_sessions
    if (sql.includes('FROM whatsapp_auth_sessions')) {
      if (sql.includes('WHERE company_id =')) {
        const companyId = params[0];
        const row = tables.whatsapp_auth_sessions.get(companyId);
        return { rows: row ? [row] : [] };
      }
      // listCompanies
      const rows = [];
      for (const [k, v] of tables.whatsapp_auth_sessions) {
        if (v.registered) rows.push({ company_id: k });
      }
      return { rows };
    }

    // SELECT from whatsapp_auth_keys
    if (sql.includes('FROM whatsapp_auth_keys')) {
      if (sql.includes('WHERE company_id =')) {
        const companyId = params[0];
        const keyType = params[1];
        const keyHashes = params.slice(2);
        const rows = [];
        for (const hash of keyHashes) {
          const key = `${companyId}:${keyType}:${hash}`;
          const row = tables.whatsapp_auth_keys.get(key);
          if (row) rows.push(row);
        }
        return { rows };
      }
    }

    // INSERT/UPDATE whatsapp_auth_sessions
    if (sql.includes('INTO whatsapp_auth_sessions')) {
      const companyId = params[0];
      const row = { company_id: companyId, payload: params[1], registered: params[2], created_at: new Date(), updated_at: new Date() };
      tables.whatsapp_auth_sessions.set(companyId, row);
      return { rows: [] };
    }

    // INSERT/UPDATE whatsapp_auth_keys
    if (sql.includes('INTO whatsapp_auth_keys')) {
      const companyId = params[0];
      const keyType = params[1];
      const keyHash = params[2];
      const payload = params[3];
      const key = `${companyId}:${keyType}:${keyHash}`;
      const row = { company_id: companyId, key_type: keyType, key_hash: keyHash, payload, updated_at: new Date() };
      tables.whatsapp_auth_keys.set(key, row);
      return { rows: [] };
    }

    // DELETE whatsapp_auth_keys
    if (sql.includes('DELETE FROM whatsapp_auth_keys')) {
      const companyId = params[0];
      for (const key of tables.whatsapp_auth_keys.keys()) {
        if (key.startsWith(`${companyId}:`)) tables.whatsapp_auth_keys.delete(key);
      }
      return { rows: [] };
    }

    // DELETE whatsapp_auth_sessions
    if (sql.includes('DELETE FROM whatsapp_auth_sessions')) {
      const companyId = params[0];
      tables.whatsapp_auth_sessions.delete(companyId);
      return { rows: [] };
    }

    return { rows: [] };
  };

  return {
    tables,
    query: executeQuery,
    connect: async () => ({
      query: executeQuery,
      release: () => {},
    }),
    end: async () => {},
  };
}

function createEncryptionKey() {
  return Buffer.from('0'.repeat(64), 'hex'); // 32 bytes of zeros for testing
}

describe('PostgresAuthStateStore', () => {
  let pool;
  let store;
  let encryptionKey;

  beforeEach(() => {
    pool = createMockPool();
    encryptionKey = createEncryptionKey();
    store = new PostgresAuthStateStore({ pool, encryptionKey, log: { log: () => {}, warn: () => {}, error: () => {} } });
  });

  it('nova empresa sem auth -> initAuthCreds -> registered=false', async () => {
    const result = await store.load('empresa-nova');
    assert.ok(result.state.creds);
    assert.equal(result.state.creds.registered, false);
    assert.equal(typeof result.saveCreds, 'function');
  });

  it('saveCreds -> persiste criptografado', async () => {
    const result = await store.load('empresa-1');
    result.state.creds.registered = true;
    result.state.creds.me = { id: '5511999999999@lid', name: 'Test' };
    await result.saveCreds();

    const loaded = await store.load('empresa-1');
    assert.equal(loaded.state.creds.registered, true);
    assert.equal(loaded.state.creds.me.id, '5511999999999@lid');
  });

  it('load posterior -> restaura creds corretamente', async () => {
    const result1 = await store.load('empresa-2');
    result1.state.creds.registered = true;
    result1.state.creds.me = { id: '5511999999999@lid', name: 'Test' };
    const testBuffer = Buffer.from('test-binary-data');
    result1.state.creds.someBuffer = testBuffer;
    await result1.saveCreds();

    const result2 = await store.load('empresa-2');
    assert.equal(result2.state.creds.registered, true);
    assert.equal(result2.state.creds.me.id, '5511999999999@lid');
    assert.ok(Buffer.isBuffer(result2.state.creds.someBuffer));
    assert.ok(result2.state.creds.someBuffer.equals(testBuffer));
  });

  it('Buffer/Uint8Array -> round-trip correto via BufferJSON', async () => {
    const result = await store.load('empresa-3');
    result.state.creds.testBuffer = Buffer.from([1, 2, 3, 4, 5]);
    result.state.creds.testUint8Array = new Uint8Array([6, 7, 8]);
    await result.saveCreds();

    const loaded = await store.load('empresa-3');
    assert.ok(Buffer.isBuffer(loaded.state.creds.testBuffer));
    assert.ok(loaded.state.creds.testBuffer.equals(Buffer.from([1, 2, 3, 4, 5])));
    assert.ok(loaded.state.creds.testUint8Array instanceof Uint8Array);
    assert.deepEqual(Array.from(loaded.state.creds.testUint8Array), [6, 7, 8]);
  });

  it('Signal keys: set -> get', async () => {
    const result = await store.load('empresa-4');
    result.state.creds.registered = true;
    await result.saveCreds();

    const testKey = { keyData: Buffer.from('signal-key-data') };
    await result.state.keys.set({
      'sender-key': { 'session-1': testKey },
    });

    const got = await result.state.keys.get('sender-key', ['session-1']);
    assert.ok(got['session-1']);
    assert.ok(got['session-1'].keyData.equals(Buffer.from('signal-key-data')));
  });

  it('Signal key null -> delete', async () => {
    const result = await store.load('empresa-5');
    result.state.creds.registered = true;
    await result.saveCreds();

    await result.state.keys.set({
      'sender-key': { 'session-1': { data: 'test' } },
    });
    let got = await result.state.keys.get('sender-key', ['session-1']);
    assert.ok(got['session-1']);

    await result.state.keys.set({
      'sender-key': { 'session-1': null },
    });
    got = await result.state.keys.get('sender-key', ['session-1']);
    assert.equal(got['session-1'], undefined);
  });

  it('app-state-sync-key -> reconstrucao correta', async () => {
    const result = await store.load('empresa-6');
    result.state.creds.registered = true;
    await result.saveCreds();

    const syncKey = proto.Message.AppStateSyncKeyData.fromObject({
      keyId: 'test-key-id',
      keyData: Buffer.from('sync-key-data'),
    });
    await result.state.keys.set({
      'app-state-sync-key': { 'sync-1': syncKey },
    });

    const got = await result.state.keys.get('app-state-sync-key', ['sync-1']);
    assert.ok(got['sync-1']);
    assert.equal(got['sync-1'].keyId, 'test-key-id');
    assert.ok(got['sync-1'].keyData.equals(Buffer.from('sync-key-data')));
  });

  it('empresa A -> nunca le keys da empresa B', async () => {
    const resultA = await store.load('empresa-A');
    resultA.state.creds.registered = true;
    await resultA.saveCreds();
    await resultA.state.keys.set({ 'sender-key': { 's1': { data: 'A' } } });

    const resultB = await store.load('empresa-B');
    resultB.state.creds.registered = true;
    await resultB.saveCreds();
    await resultB.state.keys.set({ 'sender-key': { 's1': { data: 'B' } } });

    const gotA = await resultA.state.keys.get('sender-key', ['s1']);
    const gotB = await resultB.state.keys.get('sender-key', ['s1']);
    assert.equal(gotA['s1'].data, 'A');
    assert.equal(gotB['s1'].data, 'B');
  });

  it('clear empresa A -> remove creds e keys de A -> B permanece intacta', async () => {
    const resultA = await store.load('empresa-C');
    resultA.state.creds.registered = true;
    await resultA.saveCreds();
    await resultA.state.keys.set({ 'sender-key': { 's1': { data: 'C' } } });

    const resultB = await store.load('empresa-D');
    resultB.state.creds.registered = true;
    await resultB.saveCreds();
    await resultB.state.keys.set({ 'sender-key': { 's1': { data: 'D' } } });

    await store.clear('empresa-C');

    const loadedA = await store.load('empresa-C');
    assert.equal(loadedA.state.creds.registered, false);

    const loadedB = await store.load('empresa-D');
    assert.equal(loadedB.state.creds.registered, true);
    const gotB = await loadedB.state.keys.get('sender-key', ['s1']);
    assert.equal(gotB['s1'].data, 'D');
  });

  it('listCompanies -> somente sessoes registradas', async () => {
    await store.load('empresa-sem-reg');
    // nao salva, registered=false

    const r1 = await store.load('empresa-com-reg-1');
    r1.state.creds.registered = true;
    await r1.saveCreds();

    const r2 = await store.load('empresa-com-reg-2');
    r2.state.creds.registered = true;
    await r2.saveCreds();

    const companies = await store.listCompanies();
    assert.equal(companies.length, 2);
    assert.ok(companies.includes('empresa-com-reg-1'));
    assert.ok(companies.includes('empresa-com-reg-2'));
    assert.ok(!companies.includes('empresa-sem-reg'));
  });

  it('hasRegisteredSession -> false/true corretos', async () => {
    assert.equal(await store.hasRegisteredSession('empresa-x'), false);

    const r = await store.load('empresa-y');
    r.state.creds.registered = true;
    await r.saveCreds();

    assert.equal(await store.hasRegisteredSession('empresa-y'), true);
  });

  it('payload armazenado -> nao contem segredo original em plaintext', async () => {
    const result = await store.load('empresa-segredo');
    result.state.creds.registered = true;
    result.state.creds.secret = 'super-secret-value';
    await result.saveCreds();

    // Verifica no mock pool diretamente
    const sessionRow = pool.tables.whatsapp_auth_sessions.get('empresa-segredo');
    assert.ok(sessionRow);
    const payloadText = sessionRow.payload.toString('utf8');
    assert.ok(!payloadText.includes('super-secret-value'));
    assert.ok(!payloadText.includes('secret'));
  });

  it('key index -> nao grava JID/telefone bruto se usado hash/HMAC', async () => {
    const result = await store.load('empresa-hash');
    result.state.creds.registered = true;
    await result.saveCreds();

    const jid = '5511999999999@s.whatsapp.net';
    await result.state.keys.set({
      'sender-key': { [jid]: { data: 'test' } },
    });

    const keysRow = pool.tables.whatsapp_auth_keys;
    for (const [key, row] of keysRow) {
      if (key.startsWith('empresa-hash:')) {
        // key_hash deve ser HMAC, nao o JID
        assert.ok(!row.key_hash.includes('@'));
        assert.ok(!row.key_hash.includes('5511999999999'));
        assert.equal(row.key_hash.length, 64); // SHA256 hex
      }
    }
  });

  it('encryption key errada -> erro seguro -> nunca retorna auth corrompido silenciosamente', async () => {
    const result = await store.load('empresa-key-test');
    result.state.creds.registered = true;
    result.state.creds.test = 'value';
    await result.saveCreds();

    // Cria nova store com chave diferente
    const wrongKey = Buffer.from('1'.repeat(64), 'hex');
    const store2 = new PostgresAuthStateStore({ pool, encryptionKey: wrongKey, log: { log: () => {}, warn: () => {}, error: () => {} } });

    await assert.rejects(
      store2.load('empresa-key-test'),
      /invalid_ciphertext|unsupported_payload_version|Authentication failed/
    );
  });
});