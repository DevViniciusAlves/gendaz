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

const { DisconnectReason } = require('@whiskeysockets/baileys');
const { normalizeCompanyId } = require('./companyId');

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
  constructor({ authStore, createSocket, baseDelayMs = 2000, maxDelayMs = 60000, maxAttempts = 10, log = console } = {}) {
    if (!authStore || !createSocket) {
      throw new Error('SessionManager requer authStore e createSocket');
    }
    this.authStore = authStore;
    this.createSocket = createSocket;
    this.baseDelayMs = baseDelayMs;
    this.maxDelayMs = maxDelayMs;
    this.maxAttempts = maxAttempts;
    this.log = log;
    this.sessions = new Map(); // companyId -> record
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
      };
      this.sessions.set(companyId, record);
    }
    return record;
  }

  // ---- API publica (companyId ja validado ou validado aqui) ----

  async connect(rawCompanyId) {
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
    const gen = (record.generation += 1);
    this.clearTimer(record);
    await this.closeSocketQuietly(record.sock);
    record.sock = null;
    record.qr = null;
    record.qrUpdatedAt = null;
    record.state = STATES.CONNECTING;

    let auth;
    try {
      auth = await this.authStore.load(record.companyId);
    } catch (err) {
      if (gen !== record.generation) {
        return record;
      }
      this.log.error(`[whatsapp-service] falha ao carregar auth empresa=${record.companyId}: ${err.message}`);
      record.state = STATES.DISCONNECTED;
      return record;
    }

    let sock;
    try {
      sock = this.createSocket({ authState: auth.state });
    } catch (err) {
      if (gen !== record.generation) {
        return record;
      }
      this.log.error(`[whatsapp-service] falha ao criar socket empresa=${record.companyId}: ${err.message}`);
      record.sock = null;
      // Reaproveita o retry controlado (backoff + limite, sem loop):
      // a sessao nao fica presa em CONNECTING e pode se recuperar sozinha
      // ou via novo connect() manual.
      this.scheduleRetry(record, gen, null);
      return record;
    }
    record.sock = sock;

    // Toda atualizacao de credencial (inclui Signal Keys) persiste na hora.
    sock.ev.on('creds.update', () => {
      auth.saveCreds().catch((err) => {
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

    return record;
  }

  async handleConnectionUpdate(record, gen, update) {
    const { connection, qr, lastDisconnect } = update;

    if (qr) {
      // Mantem apenas o QR atualmente valido em memoria; nunca loga o conteudo.
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
      await this.closeSocketQuietly(oldSock);
      record.state = code === DisconnectReason.loggedOut ? STATES.LOGGED_OUT : STATES.DISCONNECTED;
      record.reconnectAttempts = 0;
      this.clearTimer(record);
      if (code === DisconnectReason.loggedOut) {
        // Sessao revogada no provider: o auth antigo nao serve mais e
        // impediria um QR novo. Descarta via abstracao (somente neste caso,
        // nunca em erros temporarios ou desconhecidos).
        try {
          await this.authStore.clear(record.companyId);
        } catch (err) {
          this.log.error(`[whatsapp-service] falha ao limpar auth revogado empresa=${record.companyId}: ${err.message}`);
        }
      }
      this.log.warn(`[whatsapp-service] empresa=${record.companyId} queda definitiva codigo=${code}; sem reconexao automatica`);
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
    if (record.reconnectAttempts >= this.maxAttempts) {
      record.state = STATES.DISCONNECTED;
      this.log.warn(
        `[whatsapp-service] empresa=${record.companyId} esgotou ${this.maxAttempts} tentativas; aguardando connect manual`
      );
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
      if (gen !== record.generation) {
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
    const records = [...this.sessions.values()];
    this.sessions.clear();
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
