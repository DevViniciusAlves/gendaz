'use strict';

// Persistencia duravel de eventos de entrega (DELIVERY_ACK, READ, PLAYED)
// em PostgreSQL. Fonte de verdade: BD > memoria.
// Workers processam essa tabela e chamam Spring.

class DeliveryOutbox {
  constructor({ pool, log = console } = {}) {
    if (!pool) {
      throw new Error('DeliveryOutbox requires a pg Pool');
    }
    this.pool = pool;
    this.log = log;
  }

  async recordDelivery(companyId, providerMessageId) {
    if (!companyId || !providerMessageId) {
      throw new Error('companyId e providerMessageId sao obrigatorios');
    }

    const client = await this.pool.connect();
    try {
      const result = await client.query(
        `INSERT INTO whatsapp_delivery_outbox (company_id, provider_message_id, state, created_at, updated_at)
         VALUES ($1, $2, 'PENDING', NOW(), NOW())
         ON CONFLICT (company_id, provider_message_id) DO NOTHING
         RETURNING id, state`,
        [companyId, providerMessageId]
      );

      if (result.rows.length === 0) {
        // Ja existia, retorna que foi silenciado (idempotente)
        return { isNew: false, id: null };
      }

      return { isNew: true, id: result.rows[0].id };
    } finally {
      client.release();
    }
  }

  async getPendingCount(companyId) {
    const client = await this.pool.connect();
    try {
      const result = await client.query(
        `SELECT COUNT(*) as count FROM whatsapp_delivery_outbox
         WHERE company_id = $1 AND state IN ('PENDING', 'PROCESSING')`,
        [companyId]
      );
      return parseInt(result.rows[0].count, 10) || 0;
    } finally {
      client.release();
    }
  }

  async close() {
    if (this.pool) {
      await this.pool.end();
    }
  }
}

module.exports = { DeliveryOutbox };