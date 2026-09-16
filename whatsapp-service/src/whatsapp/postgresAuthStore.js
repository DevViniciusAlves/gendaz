'use strict';

const { Pool } = require('pg');
const { createCipheriv, createDecipheriv, randomBytes, createHmac, timingSafeEqual } = require('crypto');
const { BufferJSON } = require('@whiskeysockets/baileys').Utils || require('@whiskeysockets/baileys/lib/Utils/generics.js');
const { initAuthCreds } = require('@whiskeysockets/baileys').Utils || require('@whiskeysockets/baileys/lib/Utils/auth-utils.js');
const { WAProto } = require('@whiskeysockets/baileys');
const proto = WAProto;

const ALGORITHM = 'aes-256-gcm';
const IV_LENGTH = 12;
const TAG_LENGTH = 16;
const KEY_LENGTH = 32;
const PAYLOAD_VERSION = 1;
const HMAC_ALGORITHM = 'sha256';

function deriveKeys(masterKey) {
  const encKey = createHmac(HMAC_ALGORITHM, masterKey).update('enc').digest().subarray(0, KEY_LENGTH);
  const indexKey = createHmac(HMAC_ALGORITHM, masterKey).update('idx').digest().subarray(0, KEY_LENGTH);
  return { encKey, indexKey };
}

function encryptPayload(encKey, plaintext) {
  const iv = randomBytes(IV_LENGTH);
  const cipher = createCipheriv(ALGORITHM, encKey, iv);
  const ciphertext = Buffer.concat([cipher.update(plaintext), cipher.final()]);
  const tag = cipher.getAuthTag();
  const envelope = Buffer.alloc(1 + IV_LENGTH + TAG_LENGTH + ciphertext.length);
  envelope[0] = PAYLOAD_VERSION;
  iv.copy(envelope, 1);
  tag.copy(envelope, 1 + IV_LENGTH);
  ciphertext.copy(envelope, 1 + IV_LENGTH + TAG_LENGTH);
  return envelope;
}

function decryptPayload(encKey, envelope) {
  if (!envelope || envelope.length < 1 + IV_LENGTH + TAG_LENGTH) {
    throw new Error('invalid_ciphertext');
  }
  const version = envelope[0];
  if (version !== PAYLOAD_VERSION) {
    throw new Error('unsupported_payload_version');
  }
  const iv = envelope.subarray(1, 1 + IV_LENGTH);
  const tag = envelope.subarray(1 + IV_LENGTH, 1 + IV_LENGTH + TAG_LENGTH);
  const ciphertext = envelope.subarray(1 + IV_LENGTH + TAG_LENGTH);
  const decipher = createDecipheriv(ALGORITHM, encKey, iv);
  decipher.setAuthTag(tag);
  const plaintext = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
  return plaintext;
}

function computeKeyHash(indexKey, keyType, keyId) {
  const data = `${keyType}:${keyId}`;
  return createHmac(HMAC_ALGORITHM, indexKey).update(data).digest('hex');
}

function serializeForStorage(obj) {
  return JSON.stringify(obj, BufferJSON.replacer);
}

function parseFromStorage(text) {
  return JSON.parse(text, BufferJSON.reviver);
}

function encodeEncryptedPayload(buffer) {
  if (!Buffer.isBuffer(buffer)) {
    throw new Error('encodeEncryptedPayload requires a Buffer');
  }
  return buffer.toString('base64');
}

function decodeEncryptedPayload(base64String) {
  if (typeof base64String === 'string') {
    return Buffer.from(base64String, 'base64');
  }
  if (Buffer.isBuffer(base64String)) {
    return base64String;
  }
  throw new Error('decodeEncryptedPayload requires a string or Buffer');
}

class PostgresAuthStateStore {
  constructor({ pool, encryptionKey, log = console } = {}) {
    if (!pool) {
      throw new Error('PostgresAuthStateStore requires a pg Pool');
    }
    if (!encryptionKey || !(encryptionKey instanceof Buffer) || encryptionKey.length !== KEY_LENGTH) {
      throw new Error('PostgresAuthStateStore requires a 32-byte Buffer encryptionKey');
    }
    this.pool = pool;
    this.encryptionKey = encryptionKey;
    this.log = log;
    const { encKey, indexKey } = deriveKeys(encryptionKey);
    this.encKey = encKey;
    this.indexKey = indexKey;
    this._writeQueue = new Map();
    this._closed = false;
  }

  async load(companyId) {
    const client = await this.pool.connect();
    try {
      const result = await client.query(
        'SELECT payload, registered FROM whatsapp_auth_sessions WHERE company_id = $1',
        [companyId]
      );
      if (result.rows.length === 0) {
        const creds = initAuthCreds();
        return this._buildAuthState(creds, false, companyId);
      }
      const row = result.rows[0];
      const envelope = decodeEncryptedPayload(row.payload);
              const plaintext = decryptPayload(this.encKey, envelope);
      const creds = parseFromStorage(plaintext.toString('utf8'));
      return this._buildAuthState(creds, row.registered, companyId);
    } finally {
      client.release();
    }
  }

  _buildAuthState(creds, registered, companyId) {
    const store = this;
    return {
      state: {
        creds,
        keys: {
          async get(type, ids) {
            const data = {};
            if (!ids || ids.length === 0) return data;
            const client = await store.pool.connect();
            try {
              const keyHashes = ids.map(id => computeKeyHash(store.indexKey, type, id));
              const placeholders = keyHashes.map((_, i) => `$${i + 3}`).join(',');
              const result = await client.query(
                `SELECT key_hash, payload FROM whatsapp_auth_keys WHERE company_id = $1 AND key_type = $2 AND key_hash IN (${placeholders})`,
                [companyId, type, ...keyHashes]
              );
              for (const row of result.rows) {
                const idx = keyHashes.indexOf(row.key_hash);
                if (idx >= 0) {
                  const id = ids[idx];
                  const envelope = decodeEncryptedPayload(row.payload);
                  const plaintext = decryptPayload(store.encKey, envelope);
                  let value = parseFromStorage(plaintext.toString('utf8'));
                  if (type === 'app-state-sync-key' && value) {
                    value = proto.Message.AppStateSyncKeyData.fromObject(value);
                  }
                  data[id] = value;
                }
              }
            } finally {
              client.release();
            }
            return data;
          },
          async set(data) {
            if (!data) return;
            const client = await store.pool.connect();
            try {
              await client.query('BEGIN');
              for (const type in data) {
                for (const id in data[type]) {
                  const value = data[type][id];
                  const keyHash = computeKeyHash(store.indexKey, type, id);
                  if (value === null || value === undefined) {
                    await client.query(
                      'DELETE FROM whatsapp_auth_keys WHERE company_id = $1 AND key_type = $2 AND key_hash = $3',
                      [companyId, type, keyHash]
                    );
                  } else {
                    const serialized = serializeForStorage(value);
                    const encryptedEnvelope = encryptPayload(store.encKey, Buffer.from(serialized, 'utf8'));
                    const payload = encodeEncryptedPayload(encryptedEnvelope);
                    await client.query(
                      `INSERT INTO whatsapp_auth_keys (company_id, key_type, key_hash, payload, updated_at)
                       VALUES ($1, $2, $3, $4, NOW())
                       ON CONFLICT (company_id, key_type, key_hash) DO UPDATE SET payload = EXCLUDED.payload, updated_at = NOW()`,
                      [companyId, type, keyHash, payload]
                    );
                  }
                }
              }
              await client.query('COMMIT');
            } catch (err) {
              await client.query('ROLLBACK');
              throw err;
            } finally {
              client.release();
            }
          }
        }
      },
      saveCreds: async () => {
        const serialized = serializeForStorage(creds);

        const encryptedEnvelope = encryptPayload(
          store.encKey,
          Buffer.from(serialized, 'utf8')
        );

        const payload = encodeEncryptedPayload(encryptedEnvelope);
        const registeredFlag = Boolean(creds.registered);
        const client = await store.pool.connect();
        try {
          await client.query(
            `INSERT INTO whatsapp_auth_sessions (company_id, payload, registered, created_at, updated_at)
             VALUES ($1, $2, $3, NOW(), NOW())
             ON CONFLICT (company_id) DO UPDATE SET payload = EXCLUDED.payload, registered = EXCLUDED.registered, updated_at = NOW()`,
            [companyId, payload, registeredFlag]
          );
        } finally {
          client.release();
        }
      }
    };
  }

  async clear(companyId) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      await client.query('DELETE FROM whatsapp_auth_keys WHERE company_id = $1', [companyId]);
      await client.query('DELETE FROM whatsapp_auth_sessions WHERE company_id = $1', [companyId]);
      await client.query('COMMIT');
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  async listCompanies() {
    const result = await this.pool.query(
      'SELECT company_id FROM whatsapp_auth_sessions WHERE registered = true'
    );
    return result.rows.map(r => r.company_id);
  }

  async hasRegisteredSession(companyId) {
    const result = await this.pool.query(
      'SELECT 1 FROM whatsapp_auth_sessions WHERE company_id = $1 AND registered = true',
      [companyId]
    );
    return result.rows.length > 0;
  }

  async flush(companyId) {
    // No-op for postgres since writes are synchronous
    return Promise.resolve();
  }

  async flushAll() {
    // No-op for postgres since writes are synchronous
    return Promise.resolve();
  }

  async close() {
    if (this._closed) return;
    this._closed = true;
    await this.pool.end();
  }
}

module.exports = { PostgresAuthStateStore };