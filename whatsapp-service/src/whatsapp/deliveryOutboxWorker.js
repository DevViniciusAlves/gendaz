'use strict';

// Worker que processa delivery outbox periodicamente:
// SELECT PENDING/PROCESSING da BD -> HTTP POST ao Spring -> atualiza estado

const TRANSIENT_DELAYS = {
  1: 5,
  2: 15,
  3: 30,
  4: 60,
  5: 120,
  6: 300,
};

const AUTH_DELAYS = {
  1: 15,
  2: 60,
};

const MAX_TRANSIENT_ATTEMPTS = 7;
const MAX_AUTH_ATTEMPTS = 3;
const MAX_AUTH_RECOVERY_CYCLES = 1;
const MAX_TRANSIENT_RECOVERY_CYCLES = 2;

// Recovery duravel de callbacks DEAD por erro recuperavel: esses registros
// representam prova de entrega (DELIVERY_ACK/READ/PLAYED ja persistida) cujo
// POST ao Spring falhou de forma transitoria. Sem recovery, a notificacao
// Spring ficaria em AGUARDANDO_ENTREGA com reserva ocupada para sempre.
// Motivos permanentes (permanent_error, unexpected_http_status) continuam
// DEAD e nunca sao reprocessados.
// Razões de recovery separadas por grupo de política (única fonte de verdade).
// VER: _recoverDeadCycle() e o SQL abaixo — não duplicar estes motivos em query.
const AUTH_RECOVERABLE_REASONS = ['auth_error', 'forbidden_error'];
const TRANSITENT_RECOVERABLE_REASONS = [
  'network_error',
  'server_error',
  'rate_limit',
  'not_configured',
];
// Lista consolidada — é a fonte única para o worker e para validações.
// Inclui auth + transient. Não inclui permanent_error nem unexpected_http_status.
const RECOVERABLE_DEAD_REASONS = [
  ...AUTH_RECOVERABLE_REASONS,
  ...TRANSITENT_RECOVERABLE_REASONS,
];

function intEnv(name, def) {
  const raw = process.env[name];
  if (raw == null || String(raw).trim() === '') {
    return def;
  }
  const parsed = Number.parseInt(String(raw).trim(), 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : def;
}

class DeliveryOutboxWorker {
  constructor({ pool, backendUrl, internalToken, log = console,
    recoveryIntervalMs, recoveryBatch, recoveryMinAgeMinutes } = {}) {
    if (!pool) {
      throw new Error('DeliveryOutboxWorker requires a pg Pool');
    }
    this.pool = pool;
    this.backendUrl = backendUrl;
    this.internalToken = internalToken;
    this.log = log;
    this._running = false;
    this._loopPromise = null;
    this._pollingIntervalMs = 5000;
    this._recoveryIntervalMs = recoveryIntervalMs != null
      ? recoveryIntervalMs
      : intEnv('DELIVERY_DEAD_RECOVERY_INTERVAL_MS', 5 * 60 * 1000);
    this._recoveryBatch = recoveryBatch != null
      ? recoveryBatch
      : intEnv('DELIVERY_DEAD_RECOVERY_BATCH', 20);
    this._recoveryMinAgeMinutes = recoveryMinAgeMinutes != null
      ? recoveryMinAgeMinutes
      : intEnv('DELIVERY_DEAD_RECOVERY_MIN_AGE_MINUTES', 15);
    this._lastRecoveryAt = 0;
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
        await this._maybeRecoverDead();
        const processed = await this._processCycle();
        if (processed) {
          continue;
        }
      } catch (err) {
        this.log.error('[delivery-worker] erro no cycle:', err.message);
      }
      if (this._running) {
        await new Promise(resolve => setTimeout(resolve, this._pollingIntervalMs));
      }
    }
  }

  async _maybeRecoverDead() {
    if (Date.now() - this._lastRecoveryAt < this._recoveryIntervalMs) {
      return 0;
    }
    this._lastRecoveryAt = Date.now();
    try {
      const recovered = await this._recoverDeadCycle();
      if (recovered > 0) {
        this.log.info(`[delivery-worker] dead recovery: ${recovered} callback(s) recuperaveis voltaram para PENDING`);
      }
      return recovered;
    } catch (err) {
      this.log.error('[delivery-worker] erro no dead recovery:', err.message);
      return 0;
    }
  }

// Reprocessamento posterior controlado: em lote pequeno, com idade minima
  // (evita hot loop com o retry rapido) e reset de attempt_count, para que o
  // registro passe novamente pelo mesmo worker ate o callback confirmar.
  // Cobre tambem registros DEAD antigos ja existentes antes do deploy.
  // SQL usa as razoes centralizadas definidas em AUTH_RECOVERABLE_REASONS
  // e TRANSITENT_RECOVERABLE_REASONS -- nao duplicate os motivos aqui.
  async _recoverDeadCycle() {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const selectResult = await client.query(
        `SELECT id FROM whatsapp_delivery_outbox
         WHERE state = 'DEAD'
           AND (
             (http_error_message = ANY($1::text[]) AND recovery_count < $2)
             OR
             (http_error_message = ANY($3::text[]) AND recovery_count < $4)
           )
           AND updated_at <= NOW() - ($5 || ' minutes')::INTERVAL
         ORDER BY updated_at ASC
         LIMIT $6
         FOR UPDATE SKIP LOCKED`,
        [AUTH_RECOVERABLE_REASONS, MAX_AUTH_RECOVERY_CYCLES, TRANSITENT_RECOVERABLE_REASONS, MAX_TRANSIENT_RECOVERY_CYCLES, String(this._recoveryMinAgeMinutes), this._recoveryBatch]
      );
      const ids = selectResult.rows.map(r => r.id);
      if (ids.length === 0) {
        await client.query('COMMIT');
        return 0;
      }
      await client.query(
        `UPDATE whatsapp_delivery_outbox
         SET state = 'PENDING', attempt_count = 0, recovery_count = recovery_count + 1, next_attempt_at = NOW(),
             locked_until = NULL, http_status = NULL, updated_at = NOW()
         WHERE id = ANY($1)`,
        [ids]
      );
      await client.query('COMMIT');
      return ids.length;
    } catch (err) {
      try {
        await client.query('ROLLBACK');
      } catch {}
      throw err;
    } finally {
      client.release();
    }
  }

  async _processCycle() {
    const client = await this.pool.connect();
    let row = null;
    try {
      await client.query('BEGIN');
      const selectResult = await client.query(
        `SELECT id, company_id, provider_message_id, attempt_count, recovery_count
         FROM whatsapp_delivery_outbox
         WHERE (state = 'PENDING' AND next_attempt_at <= NOW())
            OR (state = 'PROCESSING' AND locked_until <= NOW())
         ORDER BY next_attempt_at ASC
         LIMIT 1
         FOR UPDATE SKIP LOCKED`
      );
      if (selectResult.rows.length === 0) {
        await client.query('COMMIT');
        return false;
      }
      row = selectResult.rows[0];
      const updateResult = await client.query(
        `UPDATE whatsapp_delivery_outbox
         SET state = 'PROCESSING', locked_until = NOW() + INTERVAL '60 seconds', attempt_count = attempt_count + 1, last_attempt_at = NOW(), updated_at = NOW()
         WHERE id = $1
         RETURNING attempt_count`,
        [row.id]
      );
      row.attempt_count = Number(updateResult.rows[0].attempt_count);
      await client.query('COMMIT');
    } catch (err) {
      try {
        await client.query('ROLLBACK');
      } catch {}
      throw err;
    } finally {
      client.release();
    }

    await this._postToSpring(row);
    return true;
  }

  async _postToSpring(row) {
    if (!this.backendUrl || !this.internalToken) {
      this.log.warn('[delivery-worker] configuracao incompleta, reagendando callback');
      await this._handleRetry(row, 'not_configured');
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
      this.log.error('[delivery-worker] erro de rede:', err.message);
      await this._handleRetry(row, 'network_error');
    }
  }

  async _handleResponse(row, status, body) {
    if (status === 200) {
      const client = await this.pool.connect();
      try {
        await client.query(`UPDATE whatsapp_delivery_outbox SET state = 'DONE', updated_at = NOW() WHERE id = $1`, [row.id]);
        this.log.info(`[delivery-worker] empresa=${row.company_id} messageId=${row.provider_message_id} => DONE`);
      } finally {
        client.release();
      }
      return;
    }
    if (status === 202) {
      const client = await this.pool.connect();
      try {
        await client.query(`UPDATE whatsapp_delivery_outbox SET state = 'DONE', updated_at = NOW() WHERE id = $1`, [row.id]);
        this.log.info(`[delivery-worker] empresa=${row.company_id} messageId=${row.provider_message_id} => DONE (202 persisted)`);
      } finally {
        client.release();
      }
      return;
    }
    if (status === 400 || status === 404 || status === 405 || status === 422) {
      const client = await this.pool.connect();
      try {
        await client.query(`UPDATE whatsapp_delivery_outbox SET state = 'DEAD', http_status = $1, http_error_message = $2, updated_at = NOW() WHERE id = $3`, [status, 'permanent_error', row.id]);
        this.log.warn(`[delivery-worker] empresa=${row.company_id} status=${status} motivo=permanent_error => DEAD`);
      } finally {
        client.release();
      }
      return;
    }
    if (status === 401) {
      await this._scheduleRetry(row, status, 'auth_error');
      return;
    }
    if (status === 403) {
      await this._scheduleRetry(row, status, 'forbidden_error');
      return;
    }
    if (status === 429) {
      await this._scheduleRetry(row, status, 'rate_limit');
      return;
    }
    if (status >= 500) {
      await this._scheduleRetry(row, status, 'server_error');
      return;
    }
    const client = await this.pool.connect();
    try {
      await client.query(`UPDATE whatsapp_delivery_outbox SET state = 'DEAD', http_status = $1, http_error_message = $2, updated_at = NOW() WHERE id = $3`, [status, 'unexpected_http_status', row.id]);
      this.log.warn(`[delivery-worker] empresa=${row.company_id} status=${status} motivo=unexpected_http_status => DEAD`);
    } finally {
      client.release();
    }
  }

  async _handleRetry(row, reason) {
    await this._scheduleRetry(row, null, reason);
  }

  async _scheduleRetry(row, httpStatus, errorReason) {
    const isAuth = errorReason === 'auth_error' || errorReason === 'forbidden_error';
    const maxAttempts = isAuth ? MAX_AUTH_ATTEMPTS : MAX_TRANSIENT_ATTEMPTS;
    const delays = isAuth ? AUTH_DELAYS : TRANSIENT_DELAYS;
    const client = await this.pool.connect();
    try {
      if (row.attempt_count >= maxAttempts) {
        await client.query(`UPDATE whatsapp_delivery_outbox SET state = 'DEAD', http_status = $1, http_error_message = $2, updated_at = NOW() WHERE id = $3`, [httpStatus || 0, errorReason, row.id]);
        const statusLabel = httpStatus != null ? httpStatus : 'network';
        this.log.warn(`[delivery-worker] empresa=${row.company_id} status=${statusLabel} motivo=${errorReason} => DEAD tentativa=${row.attempt_count}/${maxAttempts} recovery=${row.recovery_count}`);
      } else {
        const delaySec = delays[row.attempt_count];
        const safeDelaySec = delaySec != null ? delaySec : 300;
        const nextAttempt = new Date(Date.now() + safeDelaySec * 1000);
        await client.query(`UPDATE whatsapp_delivery_outbox SET state = 'PENDING', next_attempt_at = $1, http_status = $2, http_error_message = $3, updated_at = NOW() WHERE id = $4`, [nextAttempt, httpStatus || 0, errorReason, row.id]);
        const statusLabel = httpStatus != null ? httpStatus : 'network';
        this.log.info(`[delivery-worker] empresa=${row.company_id} status=${statusLabel} motivo=${errorReason} tentativa=${row.attempt_count}/${maxAttempts} recovery=${row.recovery_count} proxima=${safeDelaySec}s`);
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

module.exports = { DeliveryOutboxWorker, createWorker, RECOVERABLE_DEAD_REASONS };
