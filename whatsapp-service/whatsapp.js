const axios = require('axios');
const fs = require('fs');
const path = require('path');
const {
  DisconnectReason,
  fetchLatestBaileysVersion,
  Browsers,
  makeWASocket,
  makeCacheableSignalKeyStore,
  initAuthCreds,
  BufferJSON,
  proto,
} = require('@whiskeysockets/baileys');
const { processarMensagem, limparHistoricos, marcarPausaAtendimentoHumano, conversationState } = require('./ia');
const { montarMensagem } = require('./mensagens');

// ============================================
// MESSAGE QUEUE - Fila de envio com throttling
// Garante espaçamento entre mensagens por contato
// ============================================
const messageQueue = new Map();

const sendMessageWithThrottle = async (sock, jid, message) => {
  if (!sock) {
    throw new Error('Socket indisponivel para envio.');
  }

  if (!messageQueue.has(jid)) {
    messageQueue.set(jid, {
      lastSent: 0,
      queue: [],
      processing: false,
    });
  }

  const queue = messageQueue.get(jid);

  return new Promise((resolve, reject) => {
    queue.queue.push({ sock, jid, message, resolve, reject });
    void processMessageQueue(jid);
  });
};

async function processMessageQueue(jid) {
  const queue = messageQueue.get(jid);
  if (!queue || queue.processing) return;
  if (queue.queue.length === 0) return;

  queue.processing = true;

  try {
    while (queue.queue.length > 0) {
      const agora = Date.now();
      const tempoDesdeUltimoEnvio = agora - queue.lastSent;
      const throttleMs = 20000;

      if (queue.lastSent > 0 && tempoDesdeUltimoEnvio < throttleMs) {
        const esperarMs = throttleMs - tempoDesdeUltimoEnvio;
        console.log('[Bot-Service] Rate limit ativo. Proxima mensagem em', Math.ceil(esperarMs / 1000), 's');
        await new Promise((resolve) => setTimeout(resolve, esperarMs));
      }

      const item = queue.queue.shift();
      if (!item) continue;

      try {
        const result = await item.sock.sendMessage(item.jid, item.message);
        queue.lastSent = Date.now();
        console.log('[Bot-Service] Mensagem enviada para', item.jid);
        item.resolve(result);
      } catch (error) {
        console.error('[Bot-Service] Erro ao enviar:', error.message);
        item.reject(error);
      }
    }
  } finally {
    queue.processing = false;
    if (queue.queue.length === 0) {
      messageQueue.delete(jid);
    }
  }
}

const backendBaseUrl = (process.env.BACKEND_URL || process.env.BACKEND_JAVA_URL || 'http://localhost:8080').replace(/\/$/, '');
const backendToken = String(process.env.WHATSAPP_INTERNAL_TOKEN || process.env.BACKEND_INTERNAL_TOKEN || '').trim();
const backendHttp = axios.create({
  baseURL: backendBaseUrl,
  timeout: 15000,
  headers: backendToken ? { 'X-Internal-Token': backendToken } : undefined,
});
const responderGrupos = String(process.env.RESPONDER_GRUPOS || '').trim().toLowerCase() === 'true';
const sessions = new Map();
const pairingLocks = new Map();
const pairingTimeouts = new Map();
const lastStatusSentByEmpresa = new Map();
const replyQueues = new Map();
const recentMessageIds = new Map();
const botMessageIds = new Map();
const lidMappingCache = new Map();
const reconnectTimers = new Map();
const socketCreationLocks = new Map();
const PAIRING_TTL_MS = Number(process.env.WHATSAPP_PAIRING_TTL_MS || 60000);
const REPLY_DEBOUNCE_MS = Number(process.env.WHATSAPP_REPLY_DEBOUNCE_MS || 3000);
const RECENT_MESSAGE_TTL_MS = Number(process.env.WHATSAPP_RECENT_MESSAGE_TTL_MS || 120000);
const RECONNECT_BASE_DELAY_MS = Number(process.env.WHATSAPP_RECONNECT_BASE_DELAY_MS || 2000);
const RECONNECT_MAX_DELAY_MS = Number(process.env.WHATSAPP_RECONNECT_MAX_DELAY_MS || 60000);
const DURACAO_PAUSA_HUMANO_MS = Number(process.env.PAUSA_HUMANO_MINUTOS || 30) * 60 * 1000;
const FORCE_RESET_BAILEYS_SESSION = String(process.env.WHATSAPP_FORCE_RESET_BAILEYS_SESSION || '').trim().toLowerCase() === 'true';
const SESSION_ERROR_PATTERNS = [
  'bad mac',
  'no matching sessions found',
  'no session found to decrypt message',
  'failed to decrypt message',
  'failed to decrypt',
];
const PAIRING_FAILURE_PATTERNS = [
  'connection closed',
  'connection failure',
  'precondition required',
  'qr refs attempts ended',
];

function normalizarTelefone(phoneNumber) {
  const entrada = String(phoneNumber || '').trim();
  let digitos = entrada.replace(/\D/g, '');
  if (!digitos) {
    return '';
  }
  if (digitos.length === 13) {
    return digitos;
  }
  if (digitos.length === 10) {
    return `55${digitos}`;
  }
  if (digitos.length === 11) {
    return `55${digitos}`;
  }
  if (!digitos.startsWith('55')) {
    digitos = `55${digitos}`;
  }
  const nacional = digitos.slice(2);
  if (nacional.length < 10 || nacional.length > 11) {
    return digitos;
  }
  const ddd = nacional.slice(0, 2);
  let local = nacional.slice(2);
  if (local.length === 10 && local.startsWith('9')) {
    local = local.slice(1);
  }
  if (local.length !== 9) {
    return `55${ddd}${local}`;
  }
  const resultado = `55${ddd}${local}`;
  console.log('[normalize] entrada:', entrada, 'â†’ saída:', resultado, `(${resultado.length} dígitos)`);
  return resultado;
}

function normalizarTelefoneInternacional(remoteJidOuTelefone) {
  let digitos = String(remoteJidOuTelefone || '')
    .replace(/\D/g, '')
    .trim();

  if (!digitos) return null;

  // Se já tem 13 dígitos começando com 55, retorna direto
  if (digitos.startsWith('55') && digitos.length === 13) {
    return digitos;
  }

  // Se tem exatamente 11 dígitos (DDD + 9 dígitos do número), adiciona 55
  if (digitos.length === 11 && !digitos.startsWith('55')) {
    return `55${digitos}`;
  }

  // Se tem exatamente 10 dígitos (DDD + 8 dígitos), adiciona 55
  if (digitos.length === 10 && !digitos.startsWith('55')) {
    return `55${digitos}`;
  }

  // Se tem 13 dígitos MAS NÃO começa com 55, significa que já está
  // no formato internacional (tipo um ID de contato WhatsApp)
  // Nesse caso, retorna como está
  if (digitos.length === 13 && !digitos.startsWith('55')) {
    return digitos; // ou retorna '55' + digitos.slice(-11) se precisar forçar começa com 55
  }

  // Se tem MAIS de 13 dígitos, algo tá errado — retorna null ou loga aviso
  if (digitos.length > 13) {
    console.warn('[normalize] número com mais de 13 dígitos, possivelmente corrompido', { 
      digitos, 
      tamanho: digitos.length,
      primeiros13: digitos.slice(0, 13),
      ultimos13: digitos.slice(-13)
    });
    // Tentar pegar os 13 primeiros se começarem com 55
    if (digitos.slice(0, 13).startsWith('55')) {
      return digitos.slice(0, 13);
    }
    return null; // Número ambíguo, não processa
  }

  // Se chegou aqui com menos de 9 dígitos, é número inválido
  if (digitos.length < 9) {
    console.warn('[normalize] número muito curto', { digitos, tamanho: digitos.length });
    return null;
  }

  // Fallback pra outros casos não previstos
  console.warn('[normalize] número em formato desconhecido', { digitos, tamanho: digitos.length });
  return digitos;
}

function validarTelefonePareamento(phoneNumber) {
  const telefone = normalizarTelefone(phoneNumber);
  if (!telefone) {
    throw new Error('Telefone é obrigatório.');
  }
  if (telefone.length !== 13) {
    throw new Error('Informe o número com DDI, DDD e telefone. Exemplo: 5565992700672.');
  }
  if (telefone.startsWith('0')) {
    throw new Error('Informe o número com DDI, sem zero antes do código do país.');
  }
  return telefone;
}

function extrairNumeroCorreto(entrada) {
  if (!entrada) return null;

  const ehObjeto = typeof entrada === 'object' && !Array.isArray(entrada);
  const key = ehObjeto ? (entrada.key || entrada) : null;
  const remoteJid = String(
    (ehObjeto ? entrada.remoteJid : entrada)
    || key?.remoteJid
    || ''
  ).trim();

  const candidatos = [];
  if (key?.senderPn) candidatos.push({ valor: key.senderPn, origem: 'senderPn' });
  if (key?.participantPn) candidatos.push({ valor: key.participantPn, origem: 'participantPn' });

  const remoteJidAlt = String(key?.remoteJidAlt || entrada?.remoteJidAlt || '').trim();
  if (remoteJidAlt) candidatos.push({ valor: remoteJidAlt, origem: 'remoteJidAlt' });

  const senderLid = String(key?.senderLid || '').trim();
  if (senderLid && lidMappingCache.has(senderLid)) {
    candidatos.push({ valor: lidMappingCache.get(senderLid), origem: 'lidMappingCache.senderLid' });
  }

  const participantLid = String(key?.participantLid || '').trim();
  if (participantLid && lidMappingCache.has(participantLid)) {
    candidatos.push({ valor: lidMappingCache.get(participantLid), origem: 'lidMappingCache.participantLid' });
  }

  if (remoteJid && lidMappingCache.has(remoteJid)) {
    candidatos.push({ valor: lidMappingCache.get(remoteJid), origem: 'lidMappingCache.remoteJid' });
  }

  if (remoteJid && !remoteJid.includes('@lid')) {
    candidatos.push({ valor: remoteJid, origem: 'remoteJid' });
  }

  for (const candidato of candidatos) {
    const numero = normalizarTelefoneInternacional(candidato.valor);
    if (numero && numero.startsWith('55') && (numero.length === 12 || numero.length === 13)) {
      return numero;
    }
  }

  if (remoteJid.includes('@lid')) {
    console.warn('[numero-extracao] @lid sem mapeamento confiável', {
      remoteJid,
      senderPn: key?.senderPn || null,
      participantPn: key?.participantPn || null,
      senderLid: key?.senderLid || null,
      participantLid: key?.participantLid || null,
      cacheEncontrado: Boolean(lidMappingCache.get(remoteJid)),
    });
    return null;
  }

  const fallback = normalizarTelefoneInternacional(remoteJid);
  if (fallback && fallback.startsWith('55') && (fallback.length === 12 || fallback.length === 13)) {
    return fallback;
  }

  return null;
}

async function resolverNumeroMensagemBaileys({ message, remoteJid, sock }) {
  const numeroDireto = extrairNumeroCorreto({
    remoteJid,
    key: message?.key || {},
  });
  if (numeroDireto) {
    console.log('[numero-extracao] resolvido diretamente', {
      remoteJid,
      numeroFinal: numeroDireto,
      senderPn: message?.key?.senderPn || null,
      participantPn: message?.key?.participantPn || null,
    });
    return numeroDireto;
  }

  const key = message?.key || {};
  const candidatosLid = [
    String(key.senderLid || '').trim(),
    String(key.participantLid || '').trim(),
    String(remoteJid || '').trim(),
  ].filter((valor) => valor.includes('@lid'));

  for (const lid of candidatosLid) {
    try {
      const pn = await sock?.signalRepository?.lidMapping?.getPNForLID?.(lid);
      const numero = extrairNumeroCorreto(pn);
      if (numero) {
        lidMappingCache.set(lid, numero);
        console.log('[numero-extracao] resolvido via signalRepository', {
          lid,
          numero,
        });
        return numero;
      }
    } catch (error) {
      console.warn('[lid-mapping] falha ao resolver LID', {
        lid,
        detalhe: error.message,
      });
    }
  }

  return null;
}

function ehConversaPrivadaValida(remoteJid) {
  const jid = String(remoteJid || '').trim();
  if (!jid) return false;
  if (jid.endsWith('@g.us')) return false;
  if (jid === 'status@broadcast' || jid.endsWith('@broadcast')) return false;
  if (jid.endsWith('@newsletter')) return false;
  return true;
}

function extrairTextoMensagemBaileys(message) {
  const payload = message?.message || {};
  const wrappers = [
    payload,
    payload?.ephemeralMessage?.message,
    payload?.viewOnceMessage?.message,
    payload?.viewOnceMessageV2?.message,
    payload?.viewOnceMessageV2Extension?.message,
  ].filter(Boolean);

  for (const item of wrappers) {
    const texto = item?.conversation
      || item?.extendedTextMessage?.text
      || item?.imageMessage?.caption
      || item?.videoMessage?.caption
      || item?.buttonsResponseMessage?.selectedDisplayText
      || item?.templateButtonReplyMessage?.selectedDisplayText
      || item?.listResponseMessage?.singleSelectReply?.selectedRowId
      || item?.interactiveResponseMessage?.nativeFlowResponseMessage?.paramsJson
      || '';
    if (String(texto || '').trim()) {
      return String(texto).trim();
    }
  }

  return '';
}

function empresaKey(empresaId) {
  return String(empresaId || 'default');
}

function conversaKey(empresaId, remoteJid) {
  return `${empresaKey(empresaId)}:${String(remoteJid || '').trim()}`;
}

function isSessionErrorMessage(message) {
  const valor = String(message || '').toLowerCase();
  return SESSION_ERROR_PATTERNS.some((pattern) => valor.includes(pattern));
}

function isPairingFailureMessage(message) {
  const valor = String(message || '').toLowerCase();
  return PAIRING_FAILURE_PATTERNS.some((pattern) => valor.includes(pattern));
}

function statusLabel(status) {
  const normalized = String(status || '').toLowerCase();
  if (normalized === 'conectado' || normalized === 'connected') return 'WhatsApp conectado';
  if (normalized === 'connecting' || normalized === 'reconnecting') return 'Reconectando WhatsApp';
  if (normalized === 'generating_code' || normalized === 'gerando_codigo') return 'Gerando código';
  if (normalized === 'aguardando' || normalized === 'waiting_pairing') return 'Aguardando pareamento';
  if (normalized === 'pairing' || normalized === 'pairing_code') return 'Aguardando pareamento';
  if (normalized === 'pairing_failed') return 'Pareamento falhou';
  if (normalized === 'pairing_expired') return 'Código expirado';
  if (normalized === 'session_error') return 'Sessão inválida';
  if (normalized === 'disconnected') return 'Desconectado';
  return 'Desconectado';
}

function chaveSessao(empresaId) {
  return empresaKey(empresaId);
}

function obterSessaoAtiva(empresaId) {
  if (!empresaId) return null;
  return sessions.get(empresaKey(empresaId)) || null;
}

function extrairNumeroDeCreds(creds) {
  const candidatos = [
    creds?.me,
    creds?.registered && creds?.me,
    creds?.account?.jid,
    creds?.account?.id,
  ];
  for (const candidato of candidatos) {
    const valor = String(candidato || '').trim();
    if (!valor) continue;
    const numero = normalizarTelefone(valor.replace(/[:@].*$/, ''));
    if (numero) return numero;
  }
  return '';
}

function obterSessaoPorEmpresa(empresaId) {
  return sessions.get(empresaKey(empresaId)) || null;
}

function liberarLockPareamento(empresaId) {
  pairingLocks.delete(empresaKey(empresaId));
}

function obterLockPareamento(empresaId) {
  return pairingLocks.get(empresaKey(empresaId)) || null;
}

function adquirirLockPareamento(empresaId, phoneNumber) {
  const chave = empresaKey(empresaId);
  const lockAtual = pairingLocks.get(chave);
  if (lockAtual?.active) {
    return false;
  }
  pairingLocks.set(chave, {
    active: true,
    phoneNumber: normalizarTelefone(phoneNumber),
    startedAt: Date.now(),
  });
  return true;
}

function limparTimeoutPareamento(empresaId) {
  const chave = empresaKey(empresaId);
  const timer = pairingTimeouts.get(chave);
  if (timer) {
    clearTimeout(timer);
    pairingTimeouts.delete(chave);
  }
}

function agendarExpiracaoPareamento(empresaId, expiresAt) {
  limparTimeoutPareamento(empresaId);
  const chave = empresaKey(empresaId);
  const delay = Math.max(0, Number(new Date(expiresAt).getTime() - Date.now()));
  const timer = setTimeout(async () => {
    const session = obterSessaoPorEmpresa(empresaId);
    if (!session || session.status !== 'waiting_pairing') {
      return;
    }
    if (session.pairingExpiresAt && new Date(session.pairingExpiresAt).getTime() > Date.now()) {
      agendarExpiracaoPareamento(empresaId, session.pairingExpiresAt);
      return;
    }
    console.warn('[Bot-Service] pairing expirado:', {
      empresaId,
      phoneNumber: session.phoneNumber,
    });
    session.status = 'pairing_expired';
    session.lastError = 'Código de pareamento expirado.';
    session.pairingCode = null;
    session.pairingExpiresAt = null;
    session.disconnectedAt = new Date();
    session.manualDisconnect = true;
    try {
      session.sock?.ev?.removeAllListeners?.();
      session.sock?.end?.();
    } catch {
      // ignore
    }
    limparFilasRespostaEmpresa(empresaId);
    await removerSessaoPersistidaBackend(session.empresaId);
    await sincronizarWhatsappConectado(empresaId, false, 'pairing_expired');
    liberarLockPareamento(empresaId);
  }, delay);
  pairingTimeouts.set(chave, timer);
}

function limparFilaResposta(chave) {
  const item = replyQueues.get(chave);
  if (item?.timer) {
    clearTimeout(item.timer);
  }
  replyQueues.delete(chave);
}

function limparRecentesMensagemEmpresa(empresaId) {
  const prefixo = `${empresaKey(empresaId)}:`;
  for (const chave of Array.from(recentMessageIds.keys())) {
    if (chave.startsWith(prefixo)) {
      recentMessageIds.delete(chave);
    }
  }
}

function limparMensagensBotEmpresa(empresaId) {
  const prefixo = `${empresaKey(empresaId)}:`;
  for (const chave of Array.from(botMessageIds.keys())) {
    if (chave.startsWith(prefixo)) {
      botMessageIds.delete(chave);
    }
  }
}

function limparFilasRespostaEmpresa(empresaId) {
  const prefixo = `${empresaKey(empresaId)}:`;
  for (const chave of Array.from(replyQueues.keys())) {
    if (chave.startsWith(prefixo)) {
      limparFilaResposta(chave);
    }
  }
  limparRecentesMensagemEmpresa(empresaId);
}

function limparReconnectTimerEmpresa(empresaId) {
  const chave = empresaKey(empresaId);
  const timer = reconnectTimers.get(chave);
  if (timer) {
    clearTimeout(timer);
    reconnectTimers.delete(chave);
  }
}

async function criarSocketComLock(empresaId, phoneNumber, opcoes = {}) {
  const chave = empresaKey(empresaId);
  const lockAnterior = socketCreationLocks.get(chave);
  if (lockAnterior) {
    await lockAnterior.catch(() => null);
  }

  const lockPromise = (async () => {
    try {
      return await criarSocket(empresaId, phoneNumber, opcoes);
    } finally {
      if (socketCreationLocks.get(chave) === lockPromise) {
        socketCreationLocks.delete(chave);
      }
    }
  })();

  socketCreationLocks.set(chave, lockPromise);
  return lockPromise;
}

function agendarReconnectEmpresa(empresaId, current, reason, reasonMessage, delayMs = RECONNECT_BASE_DELAY_MS) {
  if (!current || current.manualDisconnect) return;
  const chave = empresaKey(empresaId);
  limparReconnectTimerEmpresa(empresaId);
  const delay = Math.max(RECONNECT_BASE_DELAY_MS, Math.min(Number(delayMs || RECONNECT_BASE_DELAY_MS), RECONNECT_MAX_DELAY_MS));
  current.status = 'reconnecting';
  current.reconnecting = true;
  current.lastError = reason ? `disconnect:${reason}` : 'disconnect';
  current.reconnectAttempts = Number(current.reconnectAttempts || 0) + 1;
  const tentativas = current.reconnectAttempts;
  console.log('[whatsapp] reconexão agendada', {
    empresaId,
    reason,
    reasonMessage,
    delayMs: delay,
    tentativas,
  });
  const timer = setTimeout(async () => {
    try {
      const sessionAtual = sessions.get(chave);
      if (!sessionAtual || sessionAtual.manualDisconnect) return;
      const authSnapshot = await carregarSessaoPersistidaBackend(empresaId);
      if (!authSnapshot) {
        console.warn('[whatsapp] sem auth persistida para reconectar', { empresaId, reason });
        return;
      }
      console.log('[whatsapp] tentando reconectar com auth persistida', {
        empresaId,
        reason,
        tentativas,
      });
      const novaSession = await criarSocketComLock(empresaId, sessionAtual.phoneNumber, {
        authSnapshot,
        statusInicial: 'connecting',
      });
      novaSession.reconnecting = true;
      novaSession.status = 'reconnecting';
      novaSession.phoneNumber = sessionAtual.phoneNumber;
      novaSession.reconnectAttempts = tentativas;
      sessions.set(chave, novaSession);
    } catch (error) {
      console.warn('[whatsapp] falha ao reconectar, reagendando', {
        empresaId,
        reason,
        detalhe: error.message,
      });
      const sessionAtual = sessions.get(chave);
      if (!sessionAtual || sessionAtual.manualDisconnect) return;
      const proximoDelay = Math.min(delay * 2, RECONNECT_MAX_DELAY_MS);
      agendarReconnectEmpresa(empresaId, sessionAtual, reason, reasonMessage, proximoDelay);
    }
  }, delay);
  reconnectTimers.set(chave, timer);
}

function chaveMensagemProcessada(empresaId, remoteJid, messageId) {
  return `${empresaKey(empresaId)}:${String(remoteJid || '').trim()}:${String(messageId || '').trim()}`;
}

function mensagemJaProcessada(empresaId, remoteJid, messageId) {
  const chave = chaveMensagemProcessada(empresaId, remoteJid, messageId);
  const expiraEm = recentMessageIds.get(chave);
  if (!expiraEm) return false;
  if (expiraEm <= Date.now()) {
    recentMessageIds.delete(chave);
    return false;
  }
  return true;
}

function registrarMensagemProcessada(empresaId, remoteJid, messageId) {
  const chave = chaveMensagemProcessada(empresaId, remoteJid, messageId);
  recentMessageIds.set(chave, Date.now() + RECENT_MESSAGE_TTL_MS);
  setTimeout(() => {
    const expiraEm = recentMessageIds.get(chave);
    if (!expiraEm) return;
    if (expiraEm <= Date.now()) {
      recentMessageIds.delete(chave);
    }
  }, RECENT_MESSAGE_TTL_MS + 250).unref?.();
}

function registrarMensagemBotEnviada(empresaId, remoteJid, messageId) {
  if (!messageId) return;
  const chave = conversaKey(empresaId, remoteJid) + `:${String(messageId).trim()}`;
  botMessageIds.set(chave, Date.now() + RECENT_MESSAGE_TTL_MS);
  setTimeout(() => {
    const expiraEm = botMessageIds.get(chave);
    if (!expiraEm) return;
    if (expiraEm <= Date.now()) {
      botMessageIds.delete(chave);
    }
  }, RECENT_MESSAGE_TTL_MS + 250).unref?.();
}

function mensagemBotConhecida(empresaId, remoteJid, messageId) {
  const chave = conversaKey(empresaId, remoteJid) + `:${String(messageId || '').trim()}`;
  const expiraEm = botMessageIds.get(chave);
  if (!expiraEm) return false;
  if (expiraEm <= Date.now()) {
    botMessageIds.delete(chave);
    return false;
  }
  return true;
}

function agendarProcessamentoResposta({
  empresaId,
  remoteJid,
  messageId,
  telefone,
  identificadorCliente,
  texto,
  sock,
}) {
  const chave = conversaKey(empresaId, remoteJid);
  if (messageId && mensagemJaProcessada(empresaId, remoteJid, messageId)) {
    console.log('[Bot-Service] mensagem ignorada por duplicidade:', {
      empresaId,
      remoteJid,
      messageId,
    });
    return;
  }

  const existente = replyQueues.get(chave);
  const versaoAtual = Number(existente?.versao || 0) + 1;
  if (existente?.timer) {
    clearTimeout(existente.timer);
    existente.textos.push(texto);
    existente.updatedAt = Date.now();
  } else {
    replyQueues.set(chave, {
      textos: [texto],
      updatedAt: Date.now(),
      timer: null,
      versao: versaoAtual,
    });
  }

  const timer = setTimeout(async () => {
    const fila = replyQueues.get(chave);
    if (!fila) return;
    if (Number(fila.versao || 0) !== versaoAtual) {
      console.log('[bot-debounce] resposta antiga descartada', {
        empresaId,
        remoteJid,
        motivo: 'versao_da_fila_mudou',
      });
      return;
    }
    replyQueues.delete(chave);
    const textoFinal = fila.textos
      .map((item) => String(item || '').trim())
      .filter(Boolean)
      .join(' ')
      .replace(/\s+/g, ' ')
      .trim();
    if (!textoFinal) {
      return;
    }

    try {
      const resposta = await processarMensagem({
        empresaId,
        remoteJid,
        phoneCliente: telefone,
        identificadorCliente,
        texto: textoFinal,
        backendUrl: backendBaseUrl,
        backendToken,
        clienteNome: '',
      });
      const filaAtual = replyQueues.get(chave);
      if (filaAtual && Number(filaAtual.versao || 0) !== versaoAtual) {
        console.log('[bot-debounce] resposta antiga descartada', {
          empresaId,
          remoteJid,
          motivo: 'processamento_sobreposto',
        });
        return;
      }
      if (resposta) {
        const enviado = await sendMessageWithThrottle(sock, remoteJid, { text: resposta });
        registrarMensagemBotEnviada(empresaId, remoteJid, enviado?.key?.id);
        if (messageId) {
          registrarMensagemProcessada(empresaId, remoteJid, messageId);
        }
      }
    } catch (error) {
      console.warn('[Bot-Service] erro ao processar mensagem com debounce:', {
        empresaId,
        remoteJid,
        detalhe: error.message,
      });
    }
  }, REPLY_DEBOUNCE_MS);

  replyQueues.set(chave, {
    ...(replyQueues.get(chave) || { textos: [] }),
    textos: existente ? existente.textos : [texto],
    updatedAt: Date.now(),
    timer,
    versao: versaoAtual,
  });
}

function ehSessaoPareamentoAtivo(session) {
  if (!session || !['generating_code', 'waiting_pairing'].includes(session.status)) return false;
  if (!session.pairingCode) return false;
  if (!session.pairingExpiresAt) return true;
  return new Date(session.pairingExpiresAt).getTime() > Date.now();
}

async function sincronizarWhatsappConectado(empresaId, conectado, detalhe = null) {
  if (!empresaId) return null;
  const rota = conectado ? 'conectar' : 'desconectar';
  console.log('[whatsapp-estado] sincronizando status no backend', {
    empresaId,
    conectado,
    rota,
    detalhe: detalhe || null,
  });
  try {
    const response = await backendHttp.post(`/api/internal/whatsapp/sessao/${empresaId}/${rota}`, {
      empresaId,
      conectado,
      detalhe: detalhe || null,
      timestamp: new Date().toISOString(),
    });
    return response.data || null;
  } catch (error) {
    console.warn('[Bot-Service] falha ao sincronizar status no backend:', error.message);
    return null;
  }
}

async function carregarSessaoPersistidaBackend(empresaId) {
  if (!empresaId) return null;
  try {
    const response = await backendHttp.get(`/api/internal/whatsapp/sessao/${empresaId}`);
    return response.data || null;
  } catch (error) {
    if (error.response?.status === 404) {
      return null;
    }
    throw error;
  }
}

async function listarSessoesPersistidasBackend() {
  try {
    const response = await backendHttp.get('/api/internal/whatsapp/sessoes');
    return Array.isArray(response.data) ? response.data : [];
  } catch (error) {
    if (error.response?.status === 404) {
      return [];
    }
    throw error;
  }
}

async function salvarSessaoPersistidaBackend(empresaId, session, status = null, lastError = null) {
  if (!empresaId || !session) return null;
  const payload = {
    credsJson: JSON.stringify(session.creds || {}, BufferJSON.replacer),
    keysJson: JSON.stringify(session.keysData || {}, BufferJSON.replacer),
    registered: Boolean(session.creds?.registered),
    phoneNumber: session.phoneNumber || extrairNumeroDeCreds(session.creds) || null,
    meId: session.creds?.me?.id || null,
    meLid: session.creds?.me?.lid || null,
    lastStatus: status || session.status || null,
    lastError: lastError || session.lastError || null,
  };
  const response = await backendHttp.put(`/api/internal/whatsapp/sessao/${empresaId}`, payload);
  return response.data || null;
}

async function removerSessaoPersistidaBackend(empresaId) {
  if (!empresaId) return;
  await backendHttp.delete(`/api/internal/whatsapp/sessao/${empresaId}`);
}

async function forceResetBaileysSession() {
  const authPath = path.join(process.cwd(), 'auth_info_baileys');
  let sessionLocalRemovida = false;
  let sessoesPersistidasRemovidas = 0;

  if (fs.existsSync(authPath)) {
    try {
      fs.rmSync(authPath, { recursive: true, force: true });
      sessionLocalRemovida = true;
      console.log('[baileys-force-reset] sessão local removida', { authPath });
    } catch (error) {
      console.warn('[baileys-force-reset] falha ao remover sessão local', {
        authPath,
        detalhe: error.message,
      });
    }
  } else {
    console.log('[baileys-force-reset] nenhuma sessão local encontrada', { authPath });
  }

  if (FORCE_RESET_BAILEYS_SESSION) {
    try {
      const sessoes = await listarSessoesPersistidasBackend();
      for (const item of sessoes) {
        const empresaId = Number(item?.empresaId || item?.empresa_id);
        if (!empresaId) continue;
        try {
          await removerSessaoPersistidaBackend(empresaId);
          sessoesPersistidasRemovidas += 1;
        } catch (error) {
          console.warn('[baileys-force-reset] falha ao remover sessão persistida', {
            empresaId,
            detalhe: error.message,
          });
        }
      }
    } catch (error) {
      console.warn('[baileys-force-reset] falha ao listar sessões persistidas', {
        detalhe: error.message,
      });
    }
  }

  console.warn('[baileys-force-reset] resumo', {
    habilitado: FORCE_RESET_BAILEYS_SESSION,
    sessionLocalRemovida,
    sessoesPersistidasRemovidas,
  });
}

async function resetWhatsappSession(empresaId, motivo = 'reset') {
  const session = obterSessaoAtiva(empresaId);
  if (!session) {
    limparTimeoutPareamento(empresaId);
    limparFilasRespostaEmpresa(empresaId);
    limparReconnectTimerEmpresa(empresaId);
    liberarLockPareamento(empresaId);
    return {
      success: true,
      status: 'desconectado',
      statusLabel: statusLabel('desconectado'),
    };
  }

  const chave = chaveSessao(session.empresaId);
  try {
    session.manualDisconnect = true;
    await session.flushAuthSave?.();
    session.cancelAuthSave?.();
    session.sock?.ev?.removeAllListeners?.();
    session.sock?.end?.();
  } catch {
    // ignore
  }

  session.cancelAuthSave?.();
  sessions.delete(chave);
  limparTimeoutPareamento(session.empresaId);
  limparFilasRespostaEmpresa(empresaId);
  limparReconnectTimerEmpresa(empresaId);
  await removerSessaoPersistidaBackend(session.empresaId);
  session.status = 'desconectado';
  session.pairingCode = null;
  session.pairingExpiresAt = null;
  session.lastError = motivo;
  session.reconnectAttempts = 0;
  lastStatusSentByEmpresa.delete(String(empresaId));
  liberarLockPareamento(session.empresaId);

  return {
    success: true,
    status: 'desconectado',
    statusLabel: statusLabel('desconectado'),
  };
}

async function marcarSessionError(empresaId, session, motivo) {
  if (!session) return;
  session.status = 'session_error';
  session.lastError = motivo;
  session.pairingCode = null;
  session.pairingExpiresAt = null;
  session.disconnectedAt = new Date();
  session.reconnectAttempts = 0;
  try {
    await session.flushAuthSave?.();
    session.cancelAuthSave?.();
    session.sock?.ev?.removeAllListeners?.();
    session.sock?.end?.();
  } catch {
    // ignore
  }
  session.cancelAuthSave?.();
  limparTimeoutPareamento(empresaId);
  limparFilasRespostaEmpresa(empresaId);
  limparReconnectTimerEmpresa(empresaId);
  await removerSessaoPersistidaBackend(session.empresaId);
  console.warn('[Bot-Service] sessão quebrada detectada:', {
    empresaId,
    phoneNumber: session.phoneNumber,
    motivo,
  });
  await sincronizarWhatsappConectado(empresaId, false, 'session_error');
  liberarLockPareamento(empresaId);
}

async function marcarPareamentoFalhou(empresaId, session, motivo) {
  if (!session) return;
  session.status = 'pairing_failed';
  session.lastError = motivo;
  session.pairingCode = null;
  session.pairingExpiresAt = null;
  session.disconnectedAt = new Date();
  session.reconnectAttempts = 0;
  try {
    await session.flushAuthSave?.();
    session.cancelAuthSave?.();
    session.sock?.ev?.removeAllListeners?.();
    session.sock?.end?.();
  } catch {
    // ignore
  }
  session.cancelAuthSave?.();
  limparTimeoutPareamento(empresaId);
  limparFilasRespostaEmpresa(empresaId);
  limparReconnectTimerEmpresa(empresaId);
  await removerSessaoPersistidaBackend(session.empresaId);
  console.warn('[Bot-Service] pareamento falhou:', {
    empresaId,
    phoneNumber: session.phoneNumber,
    motivo,
  });
  await sincronizarWhatsappConectado(empresaId, false, 'pairing_failed');
  liberarLockPareamento(empresaId);
}

function desserializarAuthJson(valor, fallback) {
  try {
    if (!valor || typeof valor !== 'string' || !valor.trim()) {
      return fallback;
    }
    return JSON.parse(valor, BufferJSON.reviver);
  } catch {
    return fallback;
  }
}

function normalizarKeysData(keysData) {
  if (!keysData || typeof keysData !== 'object' || Array.isArray(keysData)) {
    return {};
  }
  return keysData;
}

async function criarAuthStatePersistido(empresaId, phoneNumber, authSnapshot = null) {
  const snapshotCompleto = authSnapshot?.credsJson || authSnapshot?.keysJson
    ? authSnapshot
    : await carregarSessaoPersistidaBackend(empresaId);
  const snapshot = snapshotCompleto || authSnapshot;
  const creds = desserializarAuthJson(snapshot?.credsJson, initAuthCreds());
  const keysData = normalizarKeysData(desserializarAuthJson(snapshot?.keysJson, {}));
  const saveDebounceMs = Number(process.env.WHATSAPP_AUTH_SAVE_DEBOUNCE_MS || 2000);
  let saveTimeout = null;
  let savePendente = false;
  let saveEmAndamento = false;

  const construirPayloadSessao = () => ({
    empresaId,
    creds,
    keysData,
    phoneNumber: phoneNumber || snapshot?.phoneNumber || '',
    registered: Boolean(creds?.registered),
    meId: creds?.me?.id || null,
    meLid: creds?.me?.lid || null,
    status: 'connecting',
    lastError: null,
  });

  const executarSalvamento = async () => {
    if (saveEmAndamento) {
      savePendente = true;
      return;
    }

    savePendente = false;
    saveEmAndamento = true;
    try {
      await salvarSessaoPersistidaBackend(empresaId, construirPayloadSessao(), 'connecting', null);
    } catch (error) {
      console.warn('[Bot-Service] falha ao persistir sessao (debounced):', {
        empresaId,
        detalhe: error.message,
      });
    } finally {
      saveEmAndamento = false;
      if (savePendente) {
        agendarSalvamentoDebounced();
      }
    }
  };

  const agendarSalvamentoDebounced = () => {
    savePendente = true;
    if (saveTimeout) clearTimeout(saveTimeout);
    saveTimeout = setTimeout(async () => {
      saveTimeout = null;
      if (!savePendente) return;
      await executarSalvamento();
    }, saveDebounceMs);
  };

  const flushSalvamentoDebounced = async () => {
    if (saveTimeout) {
      clearTimeout(saveTimeout);
      saveTimeout = null;
    }
    if (!savePendente && !saveEmAndamento) return;
    await executarSalvamento();
  };

  const cancelarSalvamentoDebounced = () => {
    if (saveTimeout) {
      clearTimeout(saveTimeout);
      saveTimeout = null;
    }
    savePendente = false;
  };

  const state = {
    creds,
    keys: {
      get: async (type, ids) => {
        const data = {};
        for (const id of ids || []) {
          let value = keysData?.[type]?.[id] ?? null;
          if (type === 'app-state-sync-key' && value) {
            value = proto.Message.AppStateSyncKeyData.fromObject(value);
          }
          data[id] = value;
        }
        return data;
      },
      set: async (data) => {
        for (const type in data || {}) {
          if (!keysData[type]) {
            keysData[type] = {};
          }
          for (const id in data[type]) {
            const value = data[type][id];
            if (value === null || typeof value === 'undefined') {
              delete keysData[type][id];
            } else {
              keysData[type][id] = value;
            }
          }
          if (!Object.keys(keysData[type]).length) {
            delete keysData[type];
          }
        }
        agendarSalvamentoDebounced();
      },
    },
  };

  const saveCreds = async () => {
    agendarSalvamentoDebounced();
  };

  return {
    state,
    saveCreds,
    creds,
    keysData,
    snapshot,
    flushSalvamentoDebounced,
    cancelarSalvamentoDebounced,
  };
}

async function criarSocket(empresaId, phoneNumber, opcoes = {}) {
  const { authSnapshot = null, statusInicial = 'generating_code' } = opcoes;
  const { state, saveCreds, flushSalvamentoDebounced, cancelarSalvamentoDebounced } = await criarAuthStatePersistido(empresaId, phoneNumber, authSnapshot);
  const { version, isLatest } = await fetchLatestBaileysVersion();
  console.log('[whatsapp] versao Baileys:', version, '| isLatest:', isLatest);
  if (!isLatest) {
    console.warn('[whatsapp] Baileys nao esta na versao mais recente.');
  }
  const authState = {
    creds: state.creds,
    keys: makeCacheableSignalKeyStore(state.keys),
  };

  const session = {
    empresaId,
    phoneNumber: normalizarTelefone(phoneNumber) || extrairNumeroDeCreds(state.creds) || '',
    sock: null,
    status: statusInicial,
    pairingCode: null,
    pairingExpiresAt: null,
    reconnectAttempts: 0,
    connectedAt: null,
    disconnectedAt: null,
    lastError: null,
    reconnecting: false,
    registered: Boolean(state?.creds?.registered),
    pairingRequested: false,
  };

  const sock = makeWASocket({
    version,
    auth: authState,
    browser: Browsers.windows('Chrome'),
    printQRInTerminal: false,
    syncFullHistory: false,
    markOnlineOnConnect: false,
    connectTimeoutMs: 60000,
    defaultQueryTimeoutMs: 60000,
    retryRequestDelayMs: 2000,
    maxMsgRetryCount: 0,
  });

  session.sock = sock;
  session.flushAuthSave = flushSalvamentoDebounced;
  session.cancelAuthSave = cancelarSalvamentoDebounced;
  sessions.set(chaveSessao(empresaId), session);

  sock.ev.on('creds.update', saveCreds);
  sock.ev.on('lid-mapping.update', (update) => {
    const entradas = [];
    if (Array.isArray(update?.mapping)) {
      entradas.push(...update.mapping);
    } else if (update?.mapping && typeof update.mapping === 'object') {
      for (const [lid, pn] of Object.entries(update.mapping)) {
        entradas.push({ lid, pn });
      }
    }
    if (update?.lid && update?.pn) {
      entradas.push({ lid: update.lid, pn: update.pn });
    }
    if (update?.lidUser && update?.pnUser) {
      entradas.push({ lid: update.lidUser, pn: update.pnUser });
    }

    let totalAtualizados = 0;
    for (const entrada of entradas) {
      const lid = String(entrada?.lid || entrada?.lidUser || '').trim();
      const pn = String(entrada?.pn || entrada?.pnUser || '').trim();
      if (!lid || !pn) continue;
      lidMappingCache.set(lid, pn);
      totalAtualizados += 1;
    }

    if (totalAtualizados > 0) {
      console.log('[lid-mapping] cache atualizado', {
        empresaId,
        totalAtualizados,
      });
    }
  });
  sock.ev.on('messages.upsert', async ({ messages }) => {
    for (const message of messages || []) {
      try {
        const remoteJid = String(message.key?.remoteJid || '');
        const fromMe = message.key?.fromMe === true;
        const messageId = String(message.key?.id || '').trim();
        const isGroup = remoteJid.endsWith('@g.us');
        const isStatus = remoteJid === 'status@broadcast' || remoteJid.endsWith('@broadcast');
        const isNewsletter = remoteJid.endsWith('@newsletter');
        const tipoMensagem = Object.keys(message.message || {})[0] || '';
        const texto = extrairTextoMensagemBaileys(message);
        const textoReal = String(texto || '').trim();
        const eEcoDoBot = fromMe && Boolean(messageId && mensagemBotConhecida(empresaId, remoteJid, messageId));

        console.log('[Bot-Service] mensagem recebida', {
          remoteJid,
          fromMe,
          isGroup,
          isStatus,
          isNewsletter,
          tipoMensagem,
          temTexto: Boolean(textoReal),
        });

        if (!message?.message) continue;
        if (eEcoDoBot) {
          console.log('[Bot-Service] mensagem ignorada: eco do proprio bot', { remoteJid, messageId });
          continue;
        }
        const numeroCorreto = await resolverNumeroMensagemBaileys({
          message,
          remoteJid,
          sock,
        });
        const remoteJidOriginal = remoteJid;
        if (fromMe) {
          const conversaResolvida = numeroCorreto || remoteJid;
          const estadoConversa = conversationState.get(conversaKey(empresaId, conversaResolvida)) || null;
          const fluxoPagamentoDonoAtivo = Boolean(
            estadoConversa?.fluxoConfirmacaoPagamentoDono?.ativo &&
            estadoConversa?.fluxoConfirmacaoPagamentoDono?.etapa === 'AGUARDANDO_RESPOSTA_PAGAMENTO_DONO'
          );

          if (textoReal && fluxoPagamentoDonoAtivo) {
            console.log('[bot-pagamento-dono] resposta recebida do dono', {
              empresaId,
              remoteJid,
              messageId,
            });
            agendarProcessamentoResposta({
              empresaId,
              remoteJid: remoteJidOriginal,
              messageId: messageId || undefined,
              telefone: numeroCorreto || '',
              identificadorCliente: numeroCorreto || remoteJidOriginal,
              texto: textoReal,
              sock,
            });
            continue;
          }
          if (textoReal && !isStatus && !isNewsletter && !isGroup) {
            marcarPausaAtendimentoHumano(empresaId, remoteJid || '', DURACAO_PAUSA_HUMANO_MS);
            console.log('[Bot-Service] dono respondeu manualmente, pausando bot para este cliente', {
              remoteJid,
              empresaId,
              pausadoAte: new Date(Date.now() + DURACAO_PAUSA_HUMANO_MS).toISOString(),
            });
          } else {
            console.log('[bot-pausa] fromMe sem texto real, não pausar bot', {
              empresaId,
              remoteJid,
              isGroup,
              isStatus,
              isNewsletter,
              temTexto: Boolean(textoReal),
            });
          }
          console.log('[Bot-Service] mensagem ignorada: fromMe', { remoteJid, tipoMensagem });
          continue;
        }
        if (!remoteJid) {
          console.log('[Bot-Service] mensagem ignorada: remoteJid ausente');
          continue;
        }
        if (isStatus || isNewsletter) {
          console.log('[Bot-Service] mensagem ignorada: status/newsletter', { remoteJid });
          continue;
        }
        if (isGroup) {
          console.log('[Bot-Service] mensagem ignorada: grupo', { remoteJid });
          continue;
        }
        if (!ehConversaPrivadaValida(remoteJid)) {
          console.log('[Bot-Service] mensagem ignorada: nao eh conversa privada valida', { remoteJid });
          continue;
        }
        if (!textoReal) {
          console.log('[Bot-Service] mensagem ignorada: sem texto extraivel', {
            remoteJid,
            tipoMensagem,
          });
          continue;
        }

        const messageTimestamp = Number(message.messageTimestamp || 0) * 1000;
        if (messageTimestamp && Date.now() - messageTimestamp > 30000) continue;

        const phoneCliente = numeroCorreto;
        if (!phoneCliente) {
          console.warn('[numero-extracao] não foi possível resolver um telefone confiável', {
            remoteJid,
            senderPn: message?.key?.senderPn || null,
            participantPn: message?.key?.participantPn || null,
            senderLid: message?.key?.senderLid || null,
            participantLid: message?.key?.participantLid || null,
          });
          continue;
        }

        console.log('[numero-extraido]', {
          remoteJid,
          numeroFinal: phoneCliente,
        });
        console.log('[Bot-Service] mensagem aceita para bot', {
          remoteJid,
          telefoneExtraido: phoneCliente || null,
          tipoMensagem,
        });
        agendarProcessamentoResposta({
          empresaId,
          remoteJid: remoteJidOriginal,
          messageId: message.key?.id,
          telefone: phoneCliente,
          identificadorCliente: phoneCliente,
          texto: textoReal,
          sock,
        });
      } catch (error) {
        const motivo = String(error?.message || error || '');
        if (isSessionErrorMessage(motivo)) {
          await marcarSessionError(empresaId, sessions.get(String(empresaId)), motivo);
          continue;
        }
        console.warn('[Bot-Service] erro ao processar mensagem:', motivo);
      }
    }
  });

  sock.ev.on('connection.update', async (update) => {
    const { connection, lastDisconnect } = update;
    const current = sessions.get(empresaKey(empresaId));
    if (!current) return;
    const reason = lastDisconnect?.error?.output?.statusCode;
    console.log('[Socket] connection=' + String(connection || 'undefined') + ' status=' + String(reason || 'n/a'));
    const reasonMessage = String(lastDisconnect?.error?.message || lastDisconnect?.error || '').toLowerCase();
    console.log('[Bot-Service] connection.update:', {
      empresaId,
      connection,
      reason,
      registered: Boolean(current.sock?.authState?.creds?.registered),
    });
    if (connection === 'open') {
      if (!current.sock?.user && !current.sock?.authState?.creds?.registered) {
        console.warn('[Bot-Service] open ignorado sem sock.user:', {
          empresaId,
          phoneNumber: current.phoneNumber,
        });
        return;
      }
      current.status = 'conectado';
      current.reconnecting = false;
      current.connectedAt = new Date();
      current.disconnectedAt = null;
      current.lastError = null;
      current.reconnectAttempts = 0;
      current.pairingCode = null;
      current.pairingExpiresAt = null;
      current.manualDisconnect = false;
      current.pairingRequested = false;
      current.registered = true;
      limparTimeoutPareamento(empresaId);
      liberarLockPareamento(empresaId);
      console.log('[Bot-Service] bailey conectado:', {
        empresaId,
        phoneNumber: current.phoneNumber,
      });
      await sincronizarWhatsappConectado(empresaId, true, 'connection.open');
      return;
    }

    if (connection === 'close') {
      current.lastError = reason ? `disconnect:${reason}` : 'disconnect';
      console.log('[Bot-Service] bailey desconectou:', {
        empresaId,
        phoneNumber: current.phoneNumber,
        reason,
        registered: Boolean(current.sock?.authState?.creds?.registered),
        manualDisconnect: Boolean(current.manualDisconnect),
      });

      if (reason === 515) {
        console.log('[whatsapp] 515/restart required tratado como RECONNECTING', {
          empresaId,
          registered: Boolean(current.sock?.authState?.creds?.registered),
        });
        current.connectedAt = current.connectedAt || null;
        current.disconnectedAt = null;
        current.pairingRequested = false;
        current.lastError = 'restartRequired:515';
        await sincronizarWhatsappConectado(empresaId, false, 'restartRequired:515');
        agendarReconnectEmpresa(empresaId, current, reason, reasonMessage, RECONNECT_BASE_DELAY_MS);
        return;
      }

      if (current.manualDisconnect) {
        current.status = 'desconectado';
        current.disconnectedAt = new Date();
        current.pairingCode = null;
        current.pairingExpiresAt = null;
        current.lastError = null;
        current.pairingRequested = false;
        limparTimeoutPareamento(empresaId);
        liberarLockPareamento(empresaId);
        await sincronizarWhatsappConectado(empresaId, false, 'manualDisconnect');
        return;
      }

      if (isSessionErrorMessage(current.lastError) || isSessionErrorMessage(lastDisconnect?.error?.message) || isSessionErrorMessage(lastDisconnect?.error?.stack)) {
        current.status = 'session_error';
        current.disconnectedAt = new Date();
        current.pairingCode = null;
        current.pairingExpiresAt = null;
        current.pairingRequested = false;
        current.lastError = current.lastError || 'Sessão do WhatsApp inválida. Desconecte e conecte novamente.';
        await marcarSessionError(empresaId, current, current.lastError);
        return;
      }

      if (reason === 401) {
        if (current.registered || current.status === 'conectado') {
          console.warn('[whatsapp] 401 tratado como autenticação inválida após conexão estabelecida', {
            empresaId,
            registered: Boolean(current.sock?.authState?.creds?.registered),
            statusAtual: current.status,
          });
          await sincronizarWhatsappConectado(empresaId, false, 'authInvalid401');
          await marcarSessionError(empresaId, current, 'Autenticacao invalida retornada pelo WhatsApp.');
          return;
        }
        await marcarPareamentoFalhou(empresaId, current, 'Não foi possível conectar o WhatsApp. Gere um novo código e tente novamente.');
        return;
      }

      if (
        reason === 408 ||
        reason === 428 ||
        reason === DisconnectReason.timedOut ||
        reason === DisconnectReason.connectionClosed ||
        reason === DisconnectReason.connectionLost ||
        reason === DisconnectReason.restartRequired ||
        isPairingFailureMessage(reasonMessage)
      ) {
        if (current.status === 'generating_code' || current.status === 'waiting_pairing') {
          await marcarPareamentoFalhou(empresaId, current, 'Não foi possível conectar o WhatsApp. Gere um novo código e tente novamente.');
          return;
        }
        console.log('[whatsapp] queda temporária detectada, iniciando reconexão automática', {
          empresaId,
          reason,
          statusAtual: current.status,
          registered: Boolean(current.sock?.authState?.creds?.registered),
        });
        current.status = 'reconnecting';
        current.reconnecting = true;
        current.disconnectedAt = null;
        current.pairingRequested = false;
        await sincronizarWhatsappConectado(empresaId, false, 'reconnecting');
        agendarReconnectEmpresa(empresaId, current, reason, reasonMessage, RECONNECT_BASE_DELAY_MS);
        return;
      }

      if (reason === DisconnectReason.loggedOut) {
        current.status = 'desconectado';
        current.disconnectedAt = new Date();
        current.pairingCode = null;
        current.pairingExpiresAt = null;
        current.pairingRequested = false;
        limparTimeoutPareamento(empresaId);
        liberarLockPareamento(empresaId);
        await sincronizarWhatsappConectado(empresaId, false, 'loggedOut');
        await resetWhatsappSession(empresaId, 'loggedOut');
        return;
      }

      current.status = ['generating_code', 'waiting_pairing'].includes(current.status) ? current.status : 'desconectado';
      current.disconnectedAt = new Date();
      if (['generating_code', 'waiting_pairing'].includes(current.status)) {
        current.lastError = 'Conexão encerrada antes da conclusão do pareamento.';
        current.pairingRequested = false;
        await marcarPareamentoFalhou(empresaId, current, 'Não foi possível conectar o WhatsApp. Gere um novo código e tente novamente.');
        return;
      }
      current.pairingCode = null;
      current.pairingExpiresAt = null;
      current.pairingRequested = false;
      await sincronizarWhatsappConectado(empresaId, false, current.status);
      limparTimeoutPareamento(empresaId);
      liberarLockPareamento(empresaId);
    }
  });

  return session;
}

async function restaurarSessoesPersistidas() {
  try {
    const sessoesPersistidas = await listarSessoesPersistidasBackend();
    if (!sessoesPersistidas.length) {
      console.log('[Bot-Service] nenhuma sessão persistida encontrada no backend');
      return;
    }

    for (const item of sessoesPersistidas) {
      try {
        const empresaId = Number(item?.empresaId || item?.empresa_id);
        if (!empresaId) continue;
        if (sessions.has(empresaKey(empresaId))) continue;
        if (!item?.registered) {
          console.log('[Bot-Service] sessão persistida sem registro ignorada:', { empresaId });
          continue;
        }

        console.log('[Bot-Service] restaurando sessão persistida via backend:', {
          empresaId,
          registered: Boolean(item?.registered),
          phoneNumber: item?.phoneNumber || null,
        });

        const session = await criarSocketComLock(empresaId, item?.phoneNumber || '', {
          authSnapshot: item,
          statusInicial: 'connecting',
        });
        session.status = 'connecting';
        sessions.set(empresaKey(empresaId), session);
      } catch (error) {
        console.warn('[Bot-Service] falha ao restaurar sessão persistida:', {
          detalhe: error.message,
        });
      }
    }
  } catch (error) {
    console.warn('[Bot-Service] falha ao listar sessoes persistidas no backend:', error.message);
  }
}

async function restaurarSessoesAtivas() {
  return restaurarSessoesPersistidas();
}

function aguardarSocketPareamento(sock, timeoutMs = 20000) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      sock.ev.off('connection.update', onUpdate);
      reject(new Error('Tempo excedido ao preparar o pareamento do WhatsApp.'));
    }, timeoutMs);

    function onUpdate(update) {
      const { connection, lastDisconnect } = update || {};

      if (connection === 'connecting') {
        clearTimeout(timeout);
        sock.ev.off('connection.update', onUpdate);
        resolve('connecting');
        return;
      }

      if (connection === 'open') {
        clearTimeout(timeout);
        sock.ev.off('connection.update', onUpdate);
        resolve('open');
        return;
      }

      if (connection === 'close') {
        clearTimeout(timeout);
        sock.ev.off('connection.update', onUpdate);
        const statusCode = lastDisconnect?.error?.output?.statusCode;
        const message = String(lastDisconnect?.error?.message || 'Connection Closed');
        reject(new Error(statusCode ? `${message} (${statusCode})` : message));
      }
    }

    sock.ev.on('connection.update', onUpdate);
  });
}

function mensagemPareamentoAmigavel(error) {
  const mensagem = String(error?.message || error || '');
  if (isPairingFailureMessage(mensagem) || mensagem.includes('428')) {
    return 'Não foi possível iniciar a conexão com o WhatsApp. Gere um novo código e tente novamente.';
  }
  if (mensagem.toLowerCase().includes('tempo excedido')) {
    return 'Tempo excedido ao iniciar a conexão do WhatsApp. Gere um novo código e tente novamente.';
  }
  return 'Não foi possível gerar o código de conexão. Gere um novo código e tente novamente.';
}

async function reiniciarEmpresa(empresaId, phoneNumber) {
  const chave = chaveSessao(empresaId);
  const sessionAtual = sessions.get(chave);
  if (sessionAtual?.sock) {
    try {
      await sessionAtual.flushAuthSave?.();
      sessionAtual.cancelAuthSave?.();
      sessionAtual.sock.end?.();
    } catch {
      // ignore
    }
  }
  sessionAtual?.cancelAuthSave?.();
  sessions.delete(chave);
  limparTimeoutPareamento(empresaId);
  await removerSessaoPersistidaBackend(empresaId);
  lastStatusSentByEmpresa.delete(String(empresaId));
  return criarSocketComLock(empresaId, phoneNumber);
}

async function conectarEmpresa(empresaId, phoneNumber) {
  const telefone = validarTelefonePareamento(phoneNumber);
  if (!empresaId) {
    throw new Error('Empresa e obrigatoria para conectar o WhatsApp.');
  }

  console.log('[Bot-Service] iniciar connect:', {
    empresaId,
    phoneNumberNormalizado: telefone,
  });
  console.log('[Bot-Service] phoneNumber normalizado para pairing:', telefone);

  const chave = chaveSessao(empresaId);
  let session = sessions.get(chave);
  if (['session_error', 'pairing_failed', 'pairing_expired', 'desconectado'].includes(session?.status)) {
    await resetWhatsappSession(empresaId, session.status);
    session = null;
  }
  if (ehSessaoPareamentoAtivo(session)) {
    console.warn('[Bot-Service] tentativa de connect ignorada - pareamento já em andamento:', {
      empresaId,
      phoneNumberNormalizado: telefone,
    });
    return {
      status: 'waiting_pairing',
      statusLabel: statusLabel('waiting_pairing'),
      message: 'Já existe um pareamento em andamento.',
      pairingCode: session.pairingCode,
      code: session.pairingCode,
      phoneNumber: session.phoneNumber,
      empresaId,
      expiresAt: session.pairingExpiresAt || null,
    };
  }
  if (session?.status === 'conectado' && session.sock?.user) {
    return {
      status: 'conectado',
      statusLabel: statusLabel('conectado'),
      message: 'WhatsApp já está conectado.',
      code: null,
      phoneNumber: telefone,
      empresaId,
      expiresAt: null,
    };
  }

  if (!adquirirLockPareamento(empresaId, telefone)) {
    const lockedSession = sessions.get(chave);
    if (lockedSession?.status === 'generating_code' && lockedSession.pairingRequested && !lockedSession.pairingCode) {
      return {
        status: 'GENERATING_CODE',
        statusLabel: statusLabel('generating_code'),
        message: 'Gerando código de pareamento.',
        pairingCode: null,
        code: null,
        phoneNumber: lockedSession.phoneNumber || telefone,
        empresaId,
        expiresAt: null,
      };
    }
    if (ehSessaoPareamentoAtivo(lockedSession)) {
      return {
        status: 'waiting_pairing',
        statusLabel: statusLabel('waiting_pairing'),
        message: 'Já existe um pareamento em andamento.',
        pairingCode: lockedSession.pairingCode,
        code: lockedSession.pairingCode,
        phoneNumber: lockedSession.phoneNumber,
        empresaId,
        expiresAt: lockedSession.pairingExpiresAt || null,
      };
    }
    throw new Error('Já existe uma tentativa de pareamento em andamento para esta empresa.');
  }

  try {
    if (session?.status === 'generating_code' && session.pairingRequested && !session.pairingCode) {
      return {
        status: 'GENERATING_CODE',
        statusLabel: statusLabel('generating_code'),
        message: 'Gerando código de pareamento.',
        pairingCode: null,
        code: null,
        phoneNumber: session.phoneNumber || telefone,
        empresaId,
        expiresAt: null,
      };
    }

    if (session) {
      await resetWhatsappSession(empresaId, session.status || 'reset');
      session = null;
    }

    let ultimaFalha = null;
    for (let tentativa = 1; tentativa <= 2; tentativa += 1) {
      const attemptSession = await criarSocketComLock(empresaId, telefone);
      attemptSession.phoneNumber = telefone;
      attemptSession.lastError = null;
      attemptSession.status = 'generating_code';
      attemptSession.pairingRequested = true;
      sessions.set(chave, attemptSession);

      try {
        console.log('[Bot-Service] socket criado para pareamento:', {
          empresaId,
          phoneNumberNormalizado: telefone,
          tentativa,
        });
        const inicioEsperandoConexao = Date.now();
        await aguardarSocketPareamento(attemptSession.sock, tentativa === 1 ? 15000 : 25000);
        const tempoConexao = Date.now() - inicioEsperandoConexao;
        console.log('[Bot-Service] estado connecting confirmado:', {
          empresaId,
          phoneNumberNormalizado: telefone,
          tentativa,
          tempoMs: tempoConexao,
        });
        if (tempoConexao > 20000) {
          console.warn('[Bot-Service] connecting demorou mais que 20s:', {
            empresaId,
            phoneNumberNormalizado: telefone,
            tentativa,
            tempoMs: tempoConexao,
          });
        }
        await new Promise((resolve) => setTimeout(resolve, 5000));
        const inicioPairing = Date.now();
        console.log('[whatsapp] iniciando requestPairingCode:', {
          empresaId,
          phoneNumber: attemptSession.phoneNumber,
          tentativa,
          timestamp: new Date().toISOString(),
        });
        const code = await attemptSession.sock.requestPairingCode(attemptSession.phoneNumber);
        const tempoPairing = Date.now() - inicioPairing;
        console.log('[whatsapp] requestPairingCode respondeu em:', tempoPairing, 'ms');
        const pairingCode = limparCodigo(code);
        if (!pairingCode) {
          throw new Error('Baileys não retornou código de pareamento.');
        }
        if (tempoPairing > 20000) {
          console.warn('[Bot-Service] requestPairingCode demorou mais que 20s:', {
            empresaId,
            phoneNumberNormalizado: telefone,
            tentativa,
            tempoMs: tempoPairing,
          });
        }
        console.info('[Bot-Service] pairingCode retornado:', {
          empresaId,
          phoneNumberNormalizado: telefone,
          tentativa,
          tempoMs: tempoPairing,
          pairingCodeLength: pairingCode.length,
        });

        attemptSession.pairingCode = pairingCode;
        attemptSession.pairingExpiresAt = new Date(Date.now() + PAIRING_TTL_MS).toISOString();
        console.info('[Bot-Service] pairingCode gerado pelo Baileys:', {
          empresaId,
          phoneNumberNormalizado: telefone,
          pairingCodeLength: pairingCode.length,
          expiresAt: attemptSession.pairingExpiresAt,
        });
        attemptSession.status = 'waiting_pairing';
        attemptSession.connectedAt = null;
        attemptSession.disconnectedAt = null;
        attemptSession.lastError = null;
        await sincronizarWhatsappConectado(empresaId, false, 'waiting_pairing');
        agendarExpiracaoPareamento(empresaId, attemptSession.pairingExpiresAt);

        return {
          status: 'waiting_pairing',
          statusLabel: statusLabel('waiting_pairing'),
          message: 'Use o código para conectar o WhatsApp desta empresa.',
          pairingCode: attemptSession.pairingCode,
          code: attemptSession.pairingCode,
          phoneNumber: telefone,
          empresaId,
          expiresAt: attemptSession.pairingExpiresAt,
        };
      } catch (error) {
        ultimaFalha = error;
        const mensagem = mensagemPareamentoAmigavel(error);
        console.warn('[Bot-Service] falha ao gerar pairing code:', {
          empresaId,
          phoneNumber: telefone,
          tentativa,
          message: error.message,
        });
        if (tentativa < 2) {
          attemptSession.lastError = mensagem;
          attemptSession.pairingCode = null;
          attemptSession.pairingExpiresAt = null;
          attemptSession.status = 'generating_code';
          try {
            attemptSession.sock?.ev?.removeAllListeners?.();
            attemptSession.sock?.end?.();
          } catch {
            // ignore
          }
          await removerSessaoPersistidaBackend(empresaId);
          continue;
        }
        await marcarPareamentoFalhou(empresaId, attemptSession, mensagem);
        if (tentativa >= 2) {
          throw new Error('Não foi possível iniciar a conexão com o WhatsApp. Limpe a sessão e tente novamente.');
        }
      }
    }

    throw ultimaFalha || new Error('Não foi possível iniciar a conexão com o WhatsApp.');
  } catch (error) {
    liberarLockPareamento(empresaId);
    throw error;
  }
}

async function statusEmpresa(empresaId) {
  if (!empresaId) {
    console.warn('[whatsapp-status] chamada ignorada sem empresaId');
    return {
      empresaId: null,
      status: 'DISCONNECTED',
      statusLabel: statusLabel('disconnected'),
      message: 'Nenhuma conexão ativa.',
      pairingCode: null,
      code: null,
      phoneNumber: null,
      expiresAt: null,
      connected: false,
      conectado: false,
    };
  }
  const session = obterSessaoAtiva(empresaId);
  if (!session) {
    console.log('[Bot-Service] status consultado sem sessão ativa:', { empresaId });
    return {
      empresaId,
      status: 'DISCONNECTED',
      statusLabel: statusLabel('desconectado'),
      message: 'Nenhuma conexão ativa.',
      pairingCode: null,
      code: null,
      phoneNumber: null,
      expiresAt: null,
      connected: false,
      conectado: false,
    };
  }

  const statusAtual = session.status === 'conectado'
    ? 'CONNECTED'
    : session.status === 'reconnecting'
      ? 'RECONNECTING'
    : session.status === 'generating_code'
      ? 'GENERATING_CODE'
    : session.status === 'waiting_pairing'
      ? 'WAITING_PAIRING'
      : session.status === 'pairing_failed'
        ? 'PAIRING_FAILED'
        : session.status === 'pairing_expired'
          ? 'PAIRING_EXPIRED'
          : session.status === 'session_error'
            ? 'SESSION_ERROR'
            : 'DISCONNECTED';
  console.log('[Bot-Service] status consultado:', {
    empresaId,
    statusRetornado: statusAtual,
    registrado: Boolean(session.sock?.authState?.creds?.registered),
    phoneNumber: session.phoneNumber,
  });

  return {
    empresaId,
    status: statusAtual,
    statusLabel: statusLabel(statusAtual),
    message: statusAtual === 'CONNECTED'
      ? 'WhatsApp conectado.'
      : statusAtual === 'RECONNECTING'
        ? 'Reconectando WhatsApp.'
      : statusAtual === 'GENERATING_CODE'
        ? 'Gerando código de pareamento.'
      : statusAtual === 'SESSION_ERROR'
        ? 'Sessão do WhatsApp inválida. Desconecte e conecte novamente.'
      : statusAtual === 'PAIRING_FAILED'
          ? 'Não foi possível conectar o WhatsApp. Gere um novo código e tente novamente.'
          : statusAtual === 'PAIRING_EXPIRED'
            ? 'Código expirado. Gere um novo código e tente novamente.'
            : statusAtual === 'WAITING_PAIRING'
              ? 'Aguardando pareamento.'
              : 'Aguardando código de pareamento.',
    pairingCode: session.pairingCode,
    code: session.pairingCode,
    phoneNumber: session.phoneNumber,
    expiresAt: session.pairingExpiresAt || null,
    connectedAt: session.connectedAt,
    disconnectedAt: session.disconnectedAt,
    lastError: session.lastError,
    registered: Boolean(session.sock?.authState?.creds?.registered),
    connected: statusAtual === 'CONNECTED',
    conectado: statusAtual === 'CONNECTED',
  };
}

async function desconectarEmpresa(empresaId, notificar = true) {
  const session = obterSessaoAtiva(empresaId);
  const chave = session ? chaveSessao(session.empresaId) : String(empresaId || 'default');
  if (!session) {
    limparTimeoutPareamento(empresaId);
    liberarLockPareamento(empresaId);
    return {
      success: true,
      status: 'desconectado',
      statusLabel: statusLabel('desconectado'),
    };
  }

  try {
    session.manualDisconnect = true;
    await session.flushAuthSave?.();
    session.cancelAuthSave?.();
    session.sock?.end?.();
  } catch {
    // ignore
  }

  session.cancelAuthSave?.();
  sessions.delete(chave);
  limparTimeoutPareamento(empresaId || session.empresaId);
  await removerSessaoPersistidaBackend(session.empresaId);
  lastStatusSentByEmpresa.delete(String(empresaId || session.empresaId));
  liberarLockPareamento(empresaId || session.empresaId);
  if (notificar) {
    await sincronizarWhatsappConectado(empresaId, false, 'desconectado');
  }

  return {
    success: true,
    status: 'desconectado',
    statusLabel: statusLabel('desconectado'),
  };
}

async function enviarMensagemEmpresa(empresaId, phone, message) {
  const session = obterSessaoAtiva(empresaId);
  if (!session || session.status !== 'conectado' || !session.sock) {
    throw new Error('Empresa sem WhatsApp conectado.');
  }

  const telefone = normalizarTelefone(phone);
  if (!telefone) {
    throw new Error('Telefone invalido.');
  }

  const conteudo = String(message || '').trim();
  if (!conteudo) {
    throw new Error('Mensagem invalida.');
  }

  const jid = `${telefone}@s.whatsapp.net`;
  const enviado = await sendMessageWithThrottle(session.sock, jid, { text: conteudo });
  registrarMensagemBotEnviada(empresaId, jid, enviado?.key?.id);
  return {
    success: true,
    status: 'enviado',
    message: 'Mensagem enviada com sucesso.',
  };
}

async function enviarMensagemParaProprioNumeroEmpresa(empresaId, message) {
  const session = obterSessaoAtiva(empresaId);
  if (!session || session.status !== 'conectado' || !session.sock) {
    throw new Error('Empresa sem WhatsApp conectado.');
  }

  const telefone = normalizarTelefone(session.phoneNumber || session.numero || session.userPhone || extrairNumeroDeCreds(session.creds));
  if (!telefone) {
    throw new Error('Número do WhatsApp da empresa não encontrado.');
  }

  const conteudo = String(message || '').trim();
  if (!conteudo) {
    throw new Error('Mensagem inválida.');
  }

  const jid = `${telefone}@s.whatsapp.net`;
  const enviado = await sendMessageWithThrottle(session.sock, jid, { text: conteudo });
  registrarMensagemBotEnviada(empresaId, jid, enviado?.key?.id);
  return {
    success: true,
    status: 'enviado',
    phone: telefone,
    remoteJid: jid,
  };
}

async function webhookAgendamento(payload) {
  const empresaId = Number(payload?.empresaId);
  const phone = normalizarTelefone(payload?.clientePhone);
  if (!empresaId || !phone) {
    throw new Error('empresaId e clientePhone sao obrigatorios.');
  }

  const mensagem = montarMensagem(payload?.tipo, {
    nome: payload?.clienteNome,
    servico: payload?.servico,
    profissional: payload?.profissional,
    data: payload?.data,
    hora: payload?.hora,
  });

  return enviarMensagemEmpresa(empresaId, phone, mensagem);
}

async function limparSessaoEmpresa(empresaId) {
  return resetWhatsappSession(empresaId, 'clean_session');
}

function limparCodigo(code) {
  return String(code || '').trim();
}

module.exports = {
  conectarEmpresa,
  statusEmpresa,
  desconectarEmpresa,
  backendHttp,
  enviarMensagemEmpresa,
  enviarMensagemParaProprioNumeroEmpresa,
  webhookAgendamento,
  criarSocket,
  criarSocketComLock,
  reiniciarEmpresa,
  limparSessaoEmpresa,
  getConnectionStatus: statusEmpresa,
  sendMessage: enviarMensagemEmpresa,
  restaurarSessoesPersistidas,
  restaurarSessoesAtivas,
};


