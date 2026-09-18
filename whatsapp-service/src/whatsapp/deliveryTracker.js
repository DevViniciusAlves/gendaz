'use strict';

// Classificacao de ACKs de entrega do Baileys (messages.update).
//
// Usa o enum real exportado por @whiskeysockets/baileys (WAMessageStatus):
//   ERROR=0, PENDING=1, SERVER_ACK=2, DELIVERY_ACK=3, READ=4, PLAYED=5
//
// Regra de negocio: somente DELIVERY_ACK comprova entrega; READ e PLAYED sao
// estados posteriores e tambem comprovam (caso o DELIVERY_ACK intermediario
// nao tenha sido observado). SERVER_ACK/PENDING/ERROR nunca comprovam.
// Nenhum valor numerico e inventado aqui: tudo deriva de WAMessageStatus.
//
// Aceita tanto o numero do enum quanto o nome (string), porque o payload de
// messages.update pode chegar em qualquer forma dependendo da versao/caminho
// interno do Baileys. Nunca loga telefone, JID, texto ou credenciais.

const { WAMessageStatus } = require('@whiskeysockets/baileys');

// Estados que comprovam entrega, derivados do enum real (nomes -> numeros).
const PROOF_STATUS_NUMBERS = new Set(
  ['DELIVERY_ACK', 'READ', 'PLAYED']
    .map((name) => WAMessageStatus[name])
    .filter((n) => typeof n === 'number')
);

const PROOF_STATUS_NAMES = new Set(['DELIVERY_ACK', 'READ', 'PLAYED']);

// DELIVERY_ACK = entregue. READ/PLAYED posteriores tambem comprovam.
// Qualquer outro estado (SERVER_ACK, PENDING, ERROR, desconhecido) -> false.
function isProofOfDelivery(status) {
  if (typeof status === 'number') {
    return PROOF_STATUS_NUMBERS.has(status);
  }
  if (typeof status === 'string' && PROOF_STATUS_NAMES.has(status)) {
    return true;
  }
  return false;
}

// Extrai os ids (message.key.id) com prova de entrega de um lote de
// messages.update: [{ key: { id }, update: { status } }].
// Deduplica dentro do lote. Ignora entradas sem id ou sem prova.
function extractDeliveredIds(updates) {
  if (!Array.isArray(updates)) {
    return [];
  }
  const seen = new Set();
  const out = [];
  for (const entry of updates) {
    const id = entry && entry.key && entry.key.id;
    const status = entry && entry.update && entry.update.status;
    if (typeof id !== 'string' || id.trim() === '') {
      continue;
    }
    if (!isProofOfDelivery(status)) {
      continue;
    }
    if (seen.has(id)) {
      continue;
    }
    seen.add(id);
    out.push(id);
  }
  return out;
}

module.exports = { isProofOfDelivery, extractDeliveredIds, PROOF_STATUS_NUMBERS };
