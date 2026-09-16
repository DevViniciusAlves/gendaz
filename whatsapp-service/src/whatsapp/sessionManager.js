'use strict';

// Gerenciador de sessoes WhatsApp: 1 socket por empresa (companyId -> sessao).
//
// Estados: NOT_CONNECTED (inicial, antes de qualquer connect) -> CONNECTING ->
// CONNECTED; em falha temporaria -> RECONNECTING (com backoff e limite);
// em queda definitiva -> DISCONNECTED; em desvinculacao -> LOGGED_OUT.
//
// Garantias:
// - connect() repetido ou concorrente para empresa CONNECTING/CONNECTED/
//   RECONNECTING reutiliza a sessao (single-flight via promise); nunca cria
//   dois sockets simultaneos para a mesma empresa.
// - cada empresa tem auth state e diretorio de credenciais proprios.
// - reconexao conservadora: 1 timer por empresa, backoff exponencial com
//   teto, numero maximo de tentativas; socket anterior encerrado antes de
//   criar o novo; eventos de geracao antiga ignorados.
// - loggedOut (401): NAO reconecta, fecha o socket e descarta o auth
//   revogado via AuthStateStore para permitir um QR novo no proximo connect.
//   Outras quedas definitivas (403, 440) NAO reconectam, mas preservam o auth.
// - falha na criacao do socket nao prende a sessao em CONNECTING: usa o
//   retry controlado (ou DISCONNECTED ao esgotar), permitindo nova tentativa.
// - shutdownAll() encerra sockets/timers sem logout e sem apagar auth.
// - creds.update persiste imediatamente via authStore (inclui Signal Keys,
//   que fazem parte do state carregado).
// - writes de auth sao serializados por empresa para evitar race conditions.
// - recovery cooldown apos esgotar tentativas rapidas.
// - ensureConnected() para recuperacao demand-driven (status/send).

const { DisconnectReason } = require('@whiskeysockets/baileys');
const { normalizeCompanyId } = require('./companyId');
const { extractDeliveredIds } = require('./deliveryTracker');

const STATES = {
  NOT_CONNECTED: 'NOT_CONNECTED',
  CONNECTING: 'CONNECTING',
  CONNECTED: 'CONNECTED',
  RECONNECTING: 'RECONNECTING',
  DISCONNECTED: 'DISCONNECTED',
  LOGGED_OUT: 'LOGGED_OUT',
};

// Quedas que NAO devem gerar reconexao automatica.
const NO_RETRY_CODES = new Set([
  DisconnectReason.loggedOut, // 401: desvinculado, precisa de novo QR manual
  DisconnectReason.forbidden, // 403: banido/removido pelo servidor
  DisconnectReason.connectionReplaced, // 440: outro socket assumiu; reconectar brigaria com ele
]);

function disconnectCode(error) {
  if (!error) {
    return null;
  }
  const code = error && error.output && error.output.statusCode;
  return typeof code === 'number' ? code : null;
}

class SessionManager {
  constructor({
    authStore,
    createSocket,
    onDelivery,
    baseDelayMs = 2000,
    maxDelayMs = 60000,
    maxAttempts = 10,
    recoveryCooldownMs = 60000,
    log = console
  } = {}) {
    if (!authStore || !createSocket) {
      throw new Error('SessionManager requer authStore e createSocket');
    }
    this.authStore = authStore;
    this.createSocket = createSocket;
    this.onDelivery = typeof onDelivery === 'function' ? onDelivery : null;
    this.baseDelayMs = baseDelayMs;
    this.maxDelayMs = maxDelayMs;
    this.maxAttempts = maxAttempts;
    this.recoveryCooldownMs = recoveryCooldownMs;
    this.log = log;
    this.sessions = new Map(); // companyId -> record
    this._writeQueues = new Map(); // companyId -> Promise chain
    this._shuttingDown = false;
  }

  getRecord(companyId) {
    let record = this.sessions.get(companyId);
    if (!record) {
      record = {
        companyId,
        state: STATES.NOT_CONNECTED,
        sock: null,
        qr: null,
        qrUpdatedAt: null,
        connectedAt: null,
        lastDisconnectCode: null,
        reconnectAttempts: 0,
        reconnectTimer: null,
        generation: 0,
        connectingPromise: null,
        lastRecoveryAttempt: 0,
        writePromise: Promise.resolve(),
      };
      this.sessions.set(companyId, record);
    }
    return record;
  }

  // Serializa writes de auth por empresa para evitar race conditions
  _enqueueWrite(companyId, writeFn) {
    const record = this.getRecord(companyId);
    const nextPromise = record.writePromise.then(() => writeFn()).catch((err) => {
      this.log.error(`[whatsapp-service] write falhou empresa=${companyId}: ${err.message}`);
    });
    record.writePromise = nextPromise;
    return nextPromise;
  }

  // Aguarda writes pendentes de uma empresa
  async _flushWrites(companyId) {
    const record = this.sessions.get(companyId);
    if (record && record.writePromise) {
      await record.writePromise;
    }
  }

  // Aguarda todos os writes pendentes
  async _flushAllWrites() {
    await Promise.all(
      [...this.sessions.values()].map(r => r.writePromise)
    );
  }

  // ---- API publica ----

  // Restore no boot: reconecta cada empresa com auth persistido, sem gerar
  // QR novo para credencial valida (connect reutiliza). Falha em UMA empresa
  // nao derruba as outras nem o processo. Idempotente: connect() reutiliza
  // sessao ja ativa/em andamento (single-flight).
  async initialize() {
    this.log.info('[whatsapp-service] inicializando sessoes persistidas...');
    let companies = [];
    try {
      companies = await this.authStore.listCompanies();
    } catch (err) {
      this.log.error(
        `[whatsapp-service] falha ao listar sessoes persistidas: erroTipo=${err && err.name ? err.name : 'Error'}`
      );
      return;
    }
    for (const companyId of companies) {
      this.log.info(`[whatsapp-service] restaurando sessao empresa=${companyId}`);
      try {
        await this.connect(companyId);
      } catch (err) {
        this.log.error(
          `[whatsapp-service] falha ao restaurar sessao empresa=${companyId}: erroTipo=${err && err.name ? err.name : 'Error'}`
        );
      }
    }
  }

  async connect(rawCompanyId) {
    if (this._shuttingDown) {
      const record = this.getRecord(rawCompanyId);
      return record;
    }
    const companyId = normalizeCompanyId(rawCompanyId);
    if (!companyId) {
      throw Object.assign(new Error('invalid_company_id'), { code: 'invalid_company_id' });
    }
    const record = this.getRecord(companyId);
    if (
      record.state === STATES.CONNECTED ||
      record.state === STATES.CONNECTING ||
      record.state === STATES.RECONNECTING
    ) {
      return record;
    }
    if (!record.connectingPromise) {
      record.connectingPromise = this.establish(record).finally(() => {
        record.connectingPromise = null;
      });
    }
    return record.connectingPromise;
  }

  // Recuperacao demand-driven: chamada por status/send quando a sessao esta
  // desconectada mas tem auth persistido valido.
  // Nao gera QR para empresa sem sessao registrada.
  async ensureConnected(companyId, source) {
    const normalized = normalizeCompanyId(companyId);
    if (!normalized) {
      throw Object.assign(new Error('invalid_company_id'), { code: 'invalid_company_id' });
    }
    const record = this.getRecord(normalized);

    // Estados que ja estao conectando/conectados: no-op
    if (
      record.state === STATES.CONNECTED ||
      record.state === STATES.CONNECTING ||
      record.state === STATES.RECONNECTING
    ) {
      return record;
    }

    // LOGGED_OUT: nunca auto-conectar
    if (record.state === STATES.LOGGED_OUT) {
      return record;
    }

    // Verifica se tem auth persistido E registrado
    let hasAuth = false;
    try {
      hasAuth = await this.authStore.hasRegisteredSession(normalized);
    } catch (err) {
      this.log.error(`[whatsapp-service] falha ao verificar auth persistido empresa=${normalized}: ${err.message}`);
      return record;
    }

    if (!hasAuth) {
      // Sem auth persistido: nao gerar QR automaticamente
      this.log.info(`[whatsapp-service] empresa=${normalized} sem auth persistido; ${source} nao inicia connect`);
      return record;
    }

    // Cooldown de recuperacao
    const now = Date.now();
    if (now - record.lastRecoveryAttempt < this.recoveryCooldownMs) {
      this.log.info(`[whatsapp-service] empresa=${normalized} recovery cooldown ativo; ${source} aguarda`);
      return record;
    }
    record.lastRecoveryAttempt = now;

    this.log.info(`[whatsapp-service] empresa=${normalized} recovery source=${source}`);
    return this.connect(normalized);
  }

  status(rawCompanyId) {
    const companyId = normalizeCompanyId(rawCompanyId);
    if (!companyId) {
      throw Object.assign(new Error('invalid_company_id'), { code: 'invalid_company_id' });
    }
    return this.publicStatus(this.getRecord(companyId));
  }

  getQr(rawCompanyId) {
    const companyId = normalizeCompanyId(rawCompanyId);
    if (!companyId) {
      throw Object.assign(new Error('invalid_company_id'), { code: 'invalid_company_id' });
    }
    const record = this.getRecord(companyId);
    if (!record.qr) {
      return null;
    }
    return { qr: record.qr, updatedAt: record.qrUpdatedAt };
  }

  // Envio de texto: exige sessao CONNECTED com socket ativo.
  async sendText(rawCompanyId, recipient, text) {
    const companyId = normalizeCompanyId(rawCompanyId);
    if (!companyId) {
      throw Object.assign(new Error('invalid_company_id'), { code: 'invalid_company_id' });
    }
    const record = this.sessions.get(companyId);
    if (!record || record.state !== STATES.CONNECTED || !record.sock) {
      const err = new Error('session_not_connected');
      err.code = 'session_not_connected';
      err.state = record ? record.state : STATES.NOT_CONNECTED;
      throw err;
    }
    const jid = await this.resolveRecipientJid(record.sock, recipient);
    const message = await record.sock.sendMessage(jid, { text });
    const messageId = message && message.key && message.key.id;
    if (typeof messageId !== 'string' || messageId.trim() === '') {
      throw Object.assign(new Error('provider_missing_message_id'), {
        code: 'provider_missing_message_id',
      });
    }
    return messageId;
  }

  async resolveRecipientJid(sock, recipient) {
    if (!sock || typeof sock.onWhatsApp !== 'function') {
      throw Object.assign(new Error('provider_send_failed'), {
        code: 'provider_send_failed',
      });
    }
    const results = await sock.onWhatsApp(recipient);
    const list = Array.isArray(results) ? results : [];
    const valid = list.find(
      (entry) => entry && entry.exists === true
        && typeof entry.jid === 'string' && entry.jid.trim() !== ''
    );
    if (!valid) {
      throw Object.assign(new Error('recipient_not_on_whatsapp'), {
        code: 'recipient_not_on_whatsapp',
      });
    }
    return valid.jid;
  }

  async logout(rawCompanyId) {
    const companyId = normalizeCompanyId(rawCompanyId);
    if (!companyId) {
      throw Object.assign(new Error('invalid_company_id'), { code: 'invalid_company_id' });
    }
    const record = this.getRecord(companyId);
    record.generation += 1; // invalida eventos do socket antigo
    this.clearTimer(record);
    const sock = record.sock;
    record.sock = null;
    record.qr = null;
    record.qrUpdatedAt = null;
    if (sock) {
      try {
        await sock.logout();
      } catch (err) {
        this.log.warn(`[whatsapp-service] logout com falha socket empresa=${companyId}: ${err.message}`);
      }
      try {
        await sock.end(undefined);
      } catch (_) {
        // melhor esforco
      }
    }
    // Aguarda writes pendentes antes de limpar auth
    await this._flushWrites(companyId);
    // Desvinculacao intencional: remove credenciais persistidas.
    await this.authStore.clear(companyId);
    record.state = STATES.LOGGED_OUT;
    record.reconnectAttempts = 0;
    return record;
  }

  // ---- internals ----

  publicStatus(record) {
    return {
      companyId: record.companyId,
      state: record.state,
      hasQr: record.qr !== null,
      qrUpdatedAt: record.qrUpdatedAt,
      connectedAt: record.connectedAt,
      reconnectAttempts: record.reconnectAttempts,
      lastDisconnectCode: record.lastDisconnectCode,
    };
  }

  clearTimer(record) {
    if (record.reconnectTimer) {
      clearTimeout(record.reconnectTimer);
      record.reconnectTimer = null;
    }
  }

  async closeSocketQuietly(sock) {
    if (!sock) {
      return;
    }
    try {
      await sock.end(undefined);
    } catch (_) {
      // melhor esforco: socket pode ja estar morto
    }
  }

  async establish(record) {
    if (this._shuttingDown) {
      return record;
    }
    const gen = (record.generation += 1);
    this.clearTimer(record);
    await this.closeSocketQuietly(record.sock);

    // O shutdown pode ter começado durante o await acima.
    if (this._shuttingDown || gen !== record.generation) {
      return record;
    }

    record.sock = null;
    record.qr = null;
    record.qrUpdatedAt = null;
    record.state = STATES.CONNECTING;

    let auth;
    try {
      auth = await this.authStore.load(record.companyId);
    } catch (err) {
      if (this._shuttingDown || gen !== record.generation) {
        return record;
      }
      this.log.error(`[whatsapp-service] falha ao carregar auth empresa=${record.companyId}: ${err.message}`);
      record.state = STATES.DISCONNECTED;
      return record;
    }

    if (this._shuttingDown || gen !== record.generation) {
      return record;
    }

    let sock;
    try {
      sock = this.createSocket({ authState: auth.state });
    } catch (err) {
      if (this._shuttingDown || gen !== record.generation) {
        return record;
      }
      this.log.error(`[whatsapp-service] falha ao criar socket empresa=${record.companyId}: ${err.message}`);
      record.sock = null;
      this.scheduleRetry(record, gen, null);
      return record;
    }
    record.sock = sock;

    // Toda atualizacao de credencial (inclui Signal Keys) persiste na hora.
    // Protegido por generation para ignorar eventos de sockets antigos.
    sock.ev.on('creds.update', () => {
      if (gen !== record.generation) {
        return; // evento de socket antigo; ignora
      }
      this._enqueueWrite(record.companyId, () => auth.saveCreds()).catch((err) => {
        this.log.error(`[whatsapp-service] falha ao salvar credenciais empresa=${record.companyId}: ${err.message}`);
      });
    });

    sock.ev.on('connection.update', (update) => {
      if (gen !== record.generation) {
        return; // evento de socket antigo; ignora
      }
      this.handleConnectionUpdate(record, gen, update).catch((err) => {
        this.log.error(`[whatsapp-service] erro ao tratar connection.update empresa=${record.companyId}: ${err.message}`);
      });
    });

    // Prova de entrega: messages.update com DELIVERY_ACK (ou READ/PLAYED
    // posteriores, caso o intermediario nao tenha sido observado).
    // SERVER_ACK/PENDING nunca comprovam. Evento de geracao antiga e
    // ignorado. Nunca usa messages.upsert como confirmacao. Callback
    // fire-and-forget: falha nao derruba o socket.
    sock.ev.on('messages.update', (updates) => {
      if (gen !== record.generation) {
        return; // evento de socket antigo; ignora
      }
      if (!this.onDelivery) {
        return;
      }
      let delivered;
      try {
        delivered = extractDeliveredIds(updates);
      } catch (err) {
        this.log.error(`[whatsapp-service] erro ao classificar messages.update empresa=${record.companyId}: ${err.message}`);
        return;
      }
      for (const messageId of delivered) {
        Promise.resolve()
          .then(() => this.onDelivery({ companyId: record.companyId, messageId }))
          .catch((err) => {
            const seguro = err instanceof Error ? err.message : String(err);
            this.log.warn(`[whatsapp-service] falha no callback de entrega empresa=${record.companyId} messageIdPresent=true: ${seguro}`);
          });
      }
    });

    return record;
  }

  async handleConnectionUpdate(record, gen, update) {
    const { connection, qr, lastDisconnect } = update;

    if (qr) {
      record.qr = qr;
      record.qrUpdatedAt = new Date().toISOString();
    }

    if (connection === 'open') {
      record.state = STATES.CONNECTED;
      record.qr = null;
      record.qrUpdatedAt = null;
      record.connectedAt = new Date().toISOString();
      record.reconnectAttempts = 0;
      record.lastDisconnectCode = null;
      this.clearTimer(record);
      this.log.log(`[whatsapp-service] empresa=${record.companyId} CONNECTED`);
      return;
    }

    if (connection === 'connecting') {
      record.state = STATES.CONNECTING;
      return;
    }

    if (connection !== 'close') {
      return;
    }

    const code = disconnectCode(lastDisconnect && lastDisconnect.error);
    record.lastDisconnectCode = code;
    record.qr = null;
    record.qrUpdatedAt = null;
    const oldSock = record.sock;
    record.sock = null;

    if (code !== null && NO_RETRY_CODES.has(code)) {
      // Queda definitiva: invalida imediatamente eventos posteriores deste
      // socket (o listener confere generation e passa a ignora-los).
      record.generation += 1;
      await this.closeSocketQuietly(oldSock);
      record.state = code === DisconnectReason.loggedOut ? STATES.LOGGED_OUT : STATES.DISCONNECTED;
      record.reconnectAttempts = 0;
      this.clearTimer(record);
      if (code === DisconnectReason.loggedOut) {
        // Sessao revogada no provider: o auth antigo nao serve mais e
        // impediria um QR novo. Descarta via abstracao (somente neste caso,
        // nunca em erros temporarios ou desconhecidos).
        // Aguarda writes pendentes ANTES do clear para evitar race.
        await this._flushWrites(record.companyId);
        try {
          await this.authStore.clear(record.companyId);
        } catch (err) {
          this.log.error(`[whatsapp-service] falha ao limpar auth revogado empresa=${record.companyId}: ${err.message}`);
        }
        this.log.warn(`[whatsapp-service] empresa=${record.companyId} logged_out auth_cleared=true`);
      } else {
        this.log.warn(`[whatsapp-service] empresa=${record.companyId} queda definitiva codigo=${code}; sem reconexao automatica`);
      }
      return;
    }

    // Falha temporaria (ou codigo desconhecido): reconexao controlada.
    await this.closeSocketQuietly(oldSock);
    this.scheduleRetry(record, gen, code);
  }

  // Retry controlado compartilhado: backoff exponencial com teto e numero
  // maximo de tentativas. Um unico timer por empresa; ao esgotar, a sessao
  // fica DISCONNECTED aguardando connect manual. Nunca gera loop.
  scheduleRetry(record, gen, code) {
    if (this._shuttingDown || gen !== record.generation) {
      return;
    }
    if (record.reconnectAttempts >= this.maxAttempts) {
      record.state = STATES.DISCONNECTED;
      this.log.warn(
        `[whatsapp-service] empresa=${record.companyId} esgotou ${this.maxAttempts} tentativas; recovery cooldown=${this.recoveryCooldownMs}ms`
      );
      record.lastRecoveryAttempt = Date.now();
      return;
    }
    record.state = STATES.RECONNECTING;
    record.reconnectAttempts += 1;
    const delay = Math.min(this.baseDelayMs * 2 ** (record.reconnectAttempts - 1), this.maxDelayMs);
    this.log.warn(
      `[whatsapp-service] empresa=${record.companyId} tentativa ${record.reconnectAttempts}/${this.maxAttempts} em ${delay}ms (codigo=${code})`
    );
    this.clearTimer(record);
    record.reconnectTimer = setTimeout(() => {
      record.reconnectTimer = null;
      if (this._shuttingDown || gen !== record.generation) {
        return;
      }
      this.establish(record).catch((err) => {
        this.log.error(`[whatsapp-service] falha na reconexao empresa=${record.companyId}: ${err.message}`);
      });
    }, delay);
    if (record.reconnectTimer.unref) {
      record.reconnectTimer.unref();
    }
  }

  // Shutdown gracioso: cancela timers, invalida eventos antigos e encerra
  // todos os sockets com end(). NAO faz logout() e NAO apaga auth state,
  // para que as sessoes possam ser restauradas no proximo boot.
  async shutdownAll() {
    this._shuttingDown = true;
    const records = [...this.sessions.values()];
    this.sessions.clear();
    // Aguarda writes pendentes antes de encerrar
    await this._flushAllWrites();
    await Promise.all(
      records.map(async (record) => {
        record.generation += 1;
        this.clearTimer(record);
        const sock = record.sock;
        record.sock = null;
        record.qr = null;
        record.qrUpdatedAt = null;
        await this.closeSocketQuietly(sock);
      })
    );
  }
}

module.exports = { SessionManager, STATES, NO_RETRY_CODES };