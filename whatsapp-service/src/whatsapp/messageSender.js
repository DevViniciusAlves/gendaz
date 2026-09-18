'use strict';

// Envio serializado por empresa com idempotencia em memoria.
//
// - Uma promise chain por companyId: mensagens da mesma empresa sao enviadas
//   uma por vez, na ordem de chegada; empresas diferentes tem filas
//   independentes. Uma falha nunca quebra a fila das proximas.
// - Deduplicacao por (companyId, requestId) em mapas aninhados: a chave nunca
//   e uma concatenacao de strings, entao pares como ("ab", "c") e ("a", "bc")
//   jamais colidem. Requisicoes iguais simultaneas executam um unico send;
//   ambas recebem o mesmo resultado.
// - Cache pequeno de resultados com TTL e teto de tamanho: repeticao imediata
//   nao reenvia.
//
// LIMITACAO DOCUMENTADA: o cache e em memoria e perdido em restart. A fonte
// de verdade duravel da idempotencia e o banco do Spring; este cache protege
// apenas chamadas repetidas/concorrentes dentro do mesmo processo.
// Nunca loga destinatario, texto, JID, token, QR ou auth state.

const DEFAULT_TTL_MS = 10 * 60 * 1000;
const DEFAULT_MAX_ENTRIES = 500;

class MessageSender {
  constructor({ sendFn, ttlMs = DEFAULT_TTL_MS, maxEntries = DEFAULT_MAX_ENTRIES, log = console } = {}) {
    if (typeof sendFn !== 'function') {
      throw new Error('MessageSender requer sendFn');
    }
    this.sendFn = sendFn;
    this.ttlMs = ttlMs;
    this.maxEntries = maxEntries;
    this.log = log;
    this.chains = new Map(); // companyId -> promise (nunca rejeita)
    this.inflight = new Map(); // companyId -> Map(requestId -> promise em curso)
    this.cache = new Map(); // companyId -> Map(requestId -> { messageId, expiresAt })
    this.cacheSize = 0;
  }

  send({ companyId, recipient, text, requestId }) {
    const hit = this.getCache(companyId, requestId);
    if (hit) {
      if (hit.expiresAt > Date.now()) {
        this.log.log(`[whatsapp-service] send company=${companyId} request=${requestId} cache_hit`);
        return Promise.resolve({ messageId: hit.messageId, deduplicated: true });
      }
      this.deleteCache(companyId, requestId);
    }
    const ongoing = this.getInflight(companyId, requestId);
    if (ongoing) {
      return ongoing.then((result) => ({ ...result, deduplicated: true }));
    }
    const previous = this.chains.get(companyId) || Promise.resolve();
    const task = previous.then(() => this.execute({ companyId, recipient, text, requestId }));
    this.setInflight(companyId, requestId, task);
    // A cauda da fila nunca rejeita: falha de uma mensagem nao quebra as proximas.
    const settled = task.then(
      () => this.releaseChain(companyId, settled),
      () => this.releaseChain(companyId, settled)
    );
    this.chains.set(companyId, settled);
    return task;
  }

  async execute({ companyId, recipient, text, requestId }) {
    try {
      const messageId = await this.sendFn({ companyId, recipient, text });
      this.storeResult(companyId, requestId, messageId);
      // Log operacional seguro: nunca numero completo, texto, JID, token ou
      // auth state. Recipient mascarado (ultimos 4 digitos) e apenas a
      // presenca do messageId (o valor real viaja so na resposta HTTP).
      const masked = `***${String(recipient || '').replace(/\D/g, '').slice(-4)}`;
      const messageIdPresent = typeof messageId === 'string' && messageId.trim() !== '';
      this.log.log(`[whatsapp-service] send company=${companyId} request=${requestId} recipient=${masked} resolved=true messageIdPresent=${messageIdPresent} ok`);
      return { messageId, deduplicated: false };
    } catch (err) {
      const code = (err && err.code) || (err && err.constructor && err.constructor.name) || 'error';
      this.log.warn(`[whatsapp-service] send company=${companyId} request=${requestId} erro=${code}`);
      throw err;
    } finally {
      this.deleteInflight(companyId, requestId);
    }
  }

  getInflight(companyId, requestId) {
    const inner = this.inflight.get(companyId);
    return inner ? inner.get(requestId) : undefined;
  }

  setInflight(companyId, requestId, task) {
    let inner = this.inflight.get(companyId);
    if (!inner) {
      inner = new Map();
      this.inflight.set(companyId, inner);
    }
    inner.set(requestId, task);
  }

  deleteInflight(companyId, requestId) {
    const inner = this.inflight.get(companyId);
    if (!inner) {
      return;
    }
    inner.delete(requestId);
    if (inner.size === 0) {
      this.inflight.delete(companyId);
    }
  }

  getCache(companyId, requestId) {
    const inner = this.cache.get(companyId);
    return inner ? inner.get(requestId) : undefined;
  }

  deleteCache(companyId, requestId) {
    const inner = this.cache.get(companyId);
    if (!inner || !inner.has(requestId)) {
      return;
    }
    inner.delete(requestId);
    this.cacheSize -= 1;
    if (inner.size === 0) {
      this.cache.delete(companyId);
    }
  }

  storeResult(companyId, requestId, messageId) {
    const now = Date.now();
    for (const [cid, inner] of this.cache) {
      for (const [rid, entry] of inner) {
        if (entry.expiresAt <= now) {
          inner.delete(rid);
          this.cacheSize -= 1;
        }
      }
      if (inner.size === 0) {
        this.cache.delete(cid);
      }
    }
    while (this.cacheSize >= this.maxEntries) {
      let evicted = false;
      for (const [cid, inner] of this.cache) {
        const oldest = inner.keys().next();
        if (!oldest.done) {
          inner.delete(oldest.value);
          this.cacheSize -= 1;
          evicted = true;
          if (inner.size === 0) {
            this.cache.delete(cid);
          }
          break;
        }
        this.cache.delete(cid);
      }
      if (!evicted) {
        break;
      }
    }
    let inner = this.cache.get(companyId);
    if (!inner) {
      inner = new Map();
      this.cache.set(companyId, inner);
    }
    if (!inner.has(requestId)) {
      this.cacheSize += 1;
    }
    inner.set(requestId, { messageId, expiresAt: now + this.ttlMs });
  }

  releaseChain(companyId, settled) {
    if (this.chains.get(companyId) === settled) {
      this.chains.delete(companyId);
    }
  }
}

module.exports = { MessageSender, DEFAULT_TTL_MS, DEFAULT_MAX_ENTRIES };
