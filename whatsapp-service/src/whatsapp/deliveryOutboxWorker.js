'use strict';

// Worker que processa delivery outbox periodicamente:
// SELECT PENDING/PROCESSING da BD -> HTTP POST ao Spring -> atualiza estado

class DeliveryOutboxWorker {
  constructor({ pool, backendUrl, internalToken, log = console } = {}) {
    if (!pool) {
      throw new Error('DeliveryOutboxWorker requires a pg Pool');
    }
    this.pool = pool;
    this.backendUrl = backendUrl;
    this.internalToken = internalToken;
    this.log = log;
    this._running = false;
    this._loopPromise = null;
    this._pollingIntervalMs = 5000; // Poll a cada 5 segundos
  }

  start() {
    if (this._running) {
      return;
    }
    this._running = true;
    this.log.log('[delivery-worker] iniciando worker de outbox');
    this._loopPromise = this._runLoop();
  }

  async stop() {
    if (!this._running) {
      return;
    }
    this._running = false;
    this.log.log('[delivery-worker] parando worker');
    if (this._loopPromise) {
      try {
        await this._loopPromise;
      } catch (err) {
        this.log.error('[delivery-worker] erro ao aguardar loop:', err.message);
      }
    }
  }

  async _runLoop() {
    while (this._running) {
      try {
        await this._processCycle();
      } catch (err) {
        this.log.error('[delivery-worker] erro no cycle:', err.message);
      }
      // Aguardar proximo ciclo
      if (this._running) {
        await new Promise(resolve => setTimeout(resolve, this._pollingIntervalMs));
      }
    }
  }

  async _processCycle() {
    const client = await this.pool.connect();
    try {
      // Atomic: SELECT + UPDATE PROCESSING na mesma transacao
      await client.query('BEGIN');

      const selectResult = await client.query(
        `SELECT id, company_id, provider_message_id, attempt_count
         FROM whatsapp_delivery_outbox
         WHERE (state = 'PENDING' AND next_attempt_at <= NOW())
            OR (state = 'PROCESSING' AND locked_until <= NOW())
         ORDER BY next_attempt_at ASC
         LIMIT 1
         FOR UPDATE SKIP LOCKED`
      );

      if (selectResult.rows.length === 0) {
        await client.query('COMMIT');
        return;
      }

      const row = selectResult.rows[0];
      
      // Marcar como PROCESSING com lease
      const updateResult = await client.query(
        `UPDATE whatsapp_delivery_outbox
         SET state = 'PROCESSING', locked_until = NOW() + INTERVAL '60 seconds', attempt_count = attempt_count + 1, last_attempt_at = NOW(), updated_at = NOW()
         WHERE id = $1
         RETURNING attempt_count`,
        [row.id]
      );

      row.attempt_count = Number(updateResult.rows[0].attempt_count);

      await client.query('COMMIT');

      // Processar FORA da transacao
      await this._postToSpring(row);

    } catch (err) {
      try {
        await client.query('ROLLBACK');
      } catch {}
      throw err;
    } finally {
      client.release();
    }
  }

  async _postToSpring(row) {
    if (!this.backendUrl || !this.internalToken) {
      this.log.warn(
        '[delivery-worker] configuracao incompleta, reagendando callback'
      );

      await this._handleRetry(
        row,
        'not_configured'
      );

      return;
    }

    const url = `${this.backendUrl}/internal/whatsapp/delivery`;
    const payload = JSON.stringify({
      companyId: String(row.company_id),
      messageId: row.provider_message_id,
      status: 'DELIVERED'
    });

    const headers = {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${this.internalToken || ''}`,
      'Content-Length': Buffer.byteLength(payload)
    };

    try {
      const response = await globalThis.fetch(url, {
        method: 'POST',
        headers,
        body: payload,
        signal: AbortSignal.timeout(30000)
      });

      const responseText = await response.text();
      let responseBody;
      try {
        responseBody = JSON.parse(responseText);
      } catch {
        responseBody = { raw: responseText };
      }

      await this._handleResponse(row, response.status, responseBody);

    } catch (err) {
      // Network/timeout error
      this.log.error('[delivery-worker] erro de rede:', err.message);
      await this._handleRetry(row, 'network_error');
    }
  }

  async _handleResponse(row, status, body) {
    const client = await this.pool.connect();
    try {
      if (status === 200) {
        // Sucesso: DONE
        await client.query(
          `UPDATE whatsapp_delivery_outbox SET state = 'DONE', updated_at = NOW() WHERE id = $1`,
          [row.id]
        );
        this.log.info(`[delivery-worker] empresa=${row.company_id} messageId=${row.provider_message_id} => DONE`);

      } else if (status === 202) {
        // Pending but persisted no Spring: DONE
        await client.query(
          `UPDATE whatsapp_delivery_outbox SET state = 'DONE', updated_at = NOW() WHERE id = $1`,
          [row.id]
        );
        this.log.info(`[delivery-worker] empresa=${row.company_id} messageId=${row.provider_message_id} => DONE (202 persisted)`);

      } else if (status === 400) {
        // Bad request: DEAD (nao retry)
        await client.query(
          `UPDATE whatsapp_delivery_outbox SET state = 'DEAD', http_status = $1, updated_at = NOW() WHERE id = $2`,
          [status, row.id]
        );
        this.log.warn(`[delivery-worker] empresa=${row.company_id} => DEAD (400 bad request)`);

      } else if (status === 401 || status === 403) {
        // Unauthorized/Forbidden: retry com backoff
        await this._scheduleRetry(row, status, 'auth_error');

      } else if (status === 429) {
        // Rate limit: retry
        await this._scheduleRetry(row, status, 'rate_limit');

      } else if (status >= 500) {
        // Server error: retry
        await this._scheduleRetry(row, status, 'server_error');

      } else {
        // 2xx desconhecido: retry
        await this._scheduleRetry(row, status, 'unknown_2xx');
      }

    } finally {
      client.release();
    }
  }

  async _handleRetry(row, reason) {
    await this._scheduleRetry(row, null, reason);
  }

  async _scheduleRetry(row, httpStatus, errorReason) {
    const client = await this.pool.connect();
    try {
      const maxAttempts = 20;
      const nextAttempt = new Date(Date.now() + (row.attempt_count < 5 ? 5000 : 60000));

      if (row.attempt_count >= maxAttempts) {
        // Esgotou tentativas
        await client.query(
          `UPDATE whatsapp_delivery_outbox SET state = 'DEAD', http_status = $1, http_error_message = $2, updated_at = NOW() WHERE id = $3`,
          [httpStatus || 0, errorReason, row.id]
        );
        this.log.warn(`[delivery-worker] empresa=${row.company_id} => DEAD (maxAttempts=${maxAttempts})`);
      } else {
        // Schedule proxima tentativa
        await client.query(
          `UPDATE whatsapp_delivery_outbox SET state = 'PENDING', next_attempt_at = $1, http_status = $2, http_error_message = $3, updated_at = NOW() WHERE id = $4`,
          [nextAttempt, httpStatus || 0, errorReason, row.id]
        );
        this.log.info(
      `[delivery-worker] empresa=${row.company_id} => retry agendado attempts=${row.attempt_count}/${maxAttempts}`
    );
      }

    } finally {
      client.release();
    }
  }

  async close() {
    await this.stop();
    if (this.pool) {
      await this.pool.end();
    }
  }
}

function createWorker(pool) {
  if (!pool) {
    return null;
  }
  const backendUrl = process.env.GENDAZ_BACKEND_URL ? process.env.GENDAZ_BACKEND_URL.trim() : '';
  const internalToken = process.env.WHATSAPP_INTERNAL_TOKEN ? process.env.WHATSAPP_INTERNAL_TOKEN.trim() : '';

  if (!backendUrl || !internalToken) {
    return null;
  }

  return new DeliveryOutboxWorker({
    pool,
    backendUrl,
    internalToken,
    log: console
  });
}

module.exports = { DeliveryOutboxWorker, createWorker };