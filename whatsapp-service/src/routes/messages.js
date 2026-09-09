'use strict';

// POST /internal/whatsapp/sessions/{companyId}/messages/text
//
// Envio de texto (unico tipo da V1). Contrato:
//   body: { recipient: "<somente digitos>", text: "<mensagem>", requestId: "<chave>" }
//   200:  { status: "sent", messageId, requestId }
//   400:  invalid_company_id | invalid_recipient | invalid_message |
//         invalid_request_id | invalid_json
//   401:  unauthorized (middleware)
//   409:  session_not_connected (+ state publico da sessao)
//   413:  invalid_message (corpo acima do teto)
//   500:  provider_send_failed
//   503:  service_unavailable (middleware, sem token configurado)
//
// Respostas e logs nunca incluem texto, destinatario, JID, token, QR ou
// auth state: apenas companyId, requestId tecnico e classe de erro.

const { normalizeCompanyId } = require('../whatsapp/companyId');

const MAX_BODY_BYTES = 16 * 1024;
const RECIPIENT_PATTERN = /^[0-9]{8,15}$/;
const MAX_TEXT_LENGTH = 4096;
const MAX_REQUEST_ID_LENGTH = 120;

function sendJson(res, statusCode, body) {
  res.writeHead(statusCode, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
}

function readBody(req, limitBytes) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    let rejected = false;
    req.on('data', (chunk) => {
      if (rejected) {
        return;
      }
      size += chunk.length;
      if (size > limitBytes) {
        rejected = true;
        reject(Object.assign(new Error('body_too_large'), { code: 'body_too_large' }));
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      if (!rejected) {
        resolve(Buffer.concat(chunks).toString('utf8'));
      }
    });
    req.on('error', (err) => {
      if (!rejected) {
        rejected = true;
        reject(err);
      }
    });
  });
}

async function messagesTextHandler(req, res, companyId, ctx) {
  const validCompany = normalizeCompanyId(companyId);
  if (!validCompany) {
    sendJson(res, 400, { error: 'invalid_company_id' });
    return;
  }
  let raw;
  try {
    raw = await readBody(req, MAX_BODY_BYTES);
  } catch (err) {
    if (err && err.code === 'body_too_large') {
      sendJson(res, 413, { error: 'invalid_message' });
      return;
    }
    sendJson(res, 400, { error: 'invalid_json' });
    return;
  }
  let payload;
  try {
    payload = raw === '' ? null : JSON.parse(raw);
  } catch (_) {
    sendJson(res, 400, { error: 'invalid_json' });
    return;
  }
  const recipient = payload && payload.recipient;
  const text = payload && payload.text;
  const requestId = payload && payload.requestId;
  if (typeof recipient !== 'string' || !RECIPIENT_PATTERN.test(recipient)) {
    sendJson(res, 400, { error: 'invalid_recipient' });
    return;
  }
  if (typeof text !== 'string' || text.trim() === '' || text.length > MAX_TEXT_LENGTH) {
    sendJson(res, 400, { error: 'invalid_message' });
    return;
  }
  if (typeof requestId !== 'string' || requestId.trim() === '' || requestId.length > MAX_REQUEST_ID_LENGTH) {
    sendJson(res, 400, { error: 'invalid_request_id' });
    return;
  }
  try {
    const { messageId } = await ctx.messageSender.send({
      companyId: validCompany,
      recipient,
      text,
      requestId,
    });
    sendJson(res, 200, { status: 'sent', messageId: messageId || null, requestId });
  } catch (err) {
    if (err && err.code === 'session_not_connected') {
      sendJson(res, 409, { error: 'session_not_connected', state: err.state || 'UNKNOWN' });
      return;
    }
    sendJson(res, 500, { error: 'provider_send_failed' });
  }
}

module.exports = {
  messagesTextHandler,
  MAX_BODY_BYTES,
  MAX_TEXT_LENGTH,
  MAX_REQUEST_ID_LENGTH,
};
