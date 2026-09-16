'use strict';

// Callback interno Node -> Spring: informa entrega confirmada pelo Baileys.
//
// POST {backendUrl}/internal/whatsapp/delivery
//   Authorization: Bearer <WHATSAPP_INTERNAL_TOKEN>
//   body: { companyId, messageId, status: "DELIVERED" }
//
// Payload minimo, sem dados pessoais: nunca telefone, JID, texto, authState,
// QR, keys ou tokens. Fire-and-forget a partir do listener messages.update:
// falha no callback NUNCA derruba o socket (log + resolve, sem throw).
// Sem backendUrl/token configurados: loga aviso e ignora (fail-safe).
// Idempotencia duravel vive no backend (receipt persistido); aqui ha apenas
// dedup em memoria (TTL) para nao repetir callbacks no mesmo processo.

const DEFAULT_TTL_MS = 60 * 60 * 1000;
const DEFAULT_MAX_ENTRIES = 2000;

class DeliveryReporter {
  constructor({
    backendUrl = '',
    internalToken = '',
    outbox = null,
    fetchFn = fetch,
    ttlMs = DEFAULT_TTL_MS,
    maxEntries = DEFAULT_MAX_ENTRIES,
    log = console,
  } = {}) {
    this.backendUrl = (backendUrl || '').trim().replace(/\/+$/, '');
    this.internalToken = (internalToken || '').trim();
    this.outbox = outbox;
    this.fetchFn = fetchFn;
    this.ttlMs = ttlMs;
    this.maxEntries = maxEntries;
    this.log = log;
    this.reported = new Map(); // "companyId -> Map(messageId -> expiresAt)"
    this.reportedSize = 0;
  }

  configured() {
    return this.backendUrl !== '' && this.internalToken !== '';
  }

  alreadyReported(companyId, messageId) {
    const inner = this.reported.get(companyId);
    if (!inner) {
      return false;
    }
    const expiresAt = inner.get(messageId);
    if (expiresAt === undefined) {
      return false;
    }
    if (expiresAt <= Date.now()) {
      inner.delete(messageId);
      this.reportedSize -= 1;
      if (inner.size === 0) {
        this.reported.delete(companyId);
      }
      return false;
    }
    return true;
  }

  markReported(companyId, messageId) {
    const now = Date.now();
    for (const [cid, inner] of this.reported) {
      for (const [mid, exp] of inner) {
        if (exp <= now) {
          inner.delete(mid);
          this.reportedSize -= 1;
        }
      }
      if (inner.size === 0) {
        this.reported.delete(cid);
      }
    }
    while (this.reportedSize >= this.maxEntries) {
      let evicted = false;
      for (const [cid, inner] of this.reported) {
        const oldest = inner.keys().next();
        if (!oldest.done) {
          inner.delete(oldest.value);
          this.reportedSize -= 1;
          evicted = true;
          if (inner.size === 0) {
            this.reported.delete(cid);
          }
          break;
        }
        this.reported.delete(cid);
      }
      if (!evicted) {
        break;
      }
    }
    let inner = this.reported.get(companyId);
    if (!inner) {
      inner = new Map();
      this.reported.set(companyId, inner);
    }
    if (!inner.has(messageId)) {
      this.reportedSize += 1;
    }
    inner.set(messageId, now + this.ttlMs);
  }

  // Nunca rejeita: erro de rede/config e logado e resolvido como { ok: false }.
  async report(companyId, messageId) {
    if (typeof companyId !== 'string' || companyId.trim() === ''
      || typeof messageId !== 'string' || messageId.trim() === '') {
      return { ok: false, reason: 'invalid_args' };
    }

    if (this.alreadyReported(companyId, messageId)) {
      this.log.log(
        `[whatsapp-service] delivery company=${companyId} messageIdPresent=true duplicate=true ignorado`
      );
      return { ok: true, deduplicated: true };
    }

    if (this.outbox) {
      try {
        const persisted = await this.outbox.recordDelivery(companyId, messageId);

        this.markReported(companyId, messageId);

        return {
          ok: true,
          deduplicated: persisted && persisted.isNew === false,
          queued: true,
        };
      } catch (err) {
        this.log.warn(
          `[whatsapp-service] falha ao persistir delivery outbox company=${companyId} messageIdPresent=true erro=${err && err.message ? err.message : 'error'}`
        );
        // NÃO marcar reported.
        // A gravação durável falhou.
        // Continua abaixo para fallback HTTP se backend/token estiverem configurados.
      }
    }

    // Fallback: nenhum Outbox funcional ou falha no Outbox.
    if (!this.configured()) {
      this.log.warn(
        '[whatsapp-service] delivery callback ignorado: backend/token nao configurado'
      );
      return { ok: false, reason: 'not_configured' };
    }

    const url = `${this.backendUrl}/internal/whatsapp/delivery`;
    try {
      const res = await this.fetchFn(url, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${this.internalToken}`,
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: JSON.stringify({ companyId, messageId, status: 'DELIVERED' }),
      });
      if (!res || !res.ok) {
        const status = res && typeof res.status === 'number' ? res.status : 'unknown';
        this.log.warn(
          `[whatsapp-service] delivery callback falhou company=${companyId} messageIdPresent=true httpStatus=${status}`
        );
        return { ok: false, reason: 'http_error' };
      }
      this.markReported(companyId, messageId);
      this.log.log(
        `[whatsapp-service] delivery company=${companyId} messageIdPresent=true duplicate=false ok`
      );
      return { ok: true, deduplicated: false };
    } catch (err) {
      const code = (err && err.code) || (err && err.message) || 'error';
      this.log.warn(
        `[whatsapp-service] delivery callback erro company=${companyId} messageIdPresent=true erro=${code}`
      );
      return { ok: false, reason: 'fetch_error' };
    }
  }
}

module.exports = { DeliveryReporter, DEFAULT_TTL_MS, DEFAULT_MAX_ENTRIES };