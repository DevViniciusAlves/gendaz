'use strict';

// Handlers dos endpoints tecnicos de sessao. Todas as respostas sao
// sanitizadas: nunca incluem auth state, Signal Keys, credenciais,
// tokens ou stack traces.

const { normalizeCompanyId } = require('../whatsapp/companyId');

function sendJson(res, statusCode, body) {
  res.writeHead(statusCode, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
}

function invalidCompany(res) {
  sendJson(res, 400, { error: 'invalid_company_id' });
}

function toPublicStatus(manager, record) {
  return manager.publicStatus(record);
}

async function connectHandler(req, res, companyId, ctx) {
  req.resume(); // descarta eventual corpo; endpoint nao consome payload
  const valid = normalizeCompanyId(companyId);
  if (!valid) {
    invalidCompany(res);
    return;
  }
  try {
    const record = await ctx.sessions.connect(valid);
    sendJson(res, 200, toPublicStatus(ctx.sessions, record));
  } catch (err) {
    sendJson(res, 500, { error: 'internal_error' });
  }
}

async function statusHandler(req, res, companyId, ctx) {
  const valid = normalizeCompanyId(companyId);
  if (!valid) {
    invalidCompany(res);
    return;
  }
  try {
    sendJson(res, 200, ctx.sessions.status(valid));
  } catch (err) {
    sendJson(res, 500, { error: 'internal_error' });
  }
}

async function qrHandler(req, res, companyId, ctx) {
  const valid = normalizeCompanyId(companyId);
  if (!valid) {
    invalidCompany(res);
    return;
  }
  try {
    const current = ctx.sessions.getQr(valid);
    if (!current) {
      const status = ctx.sessions.status(valid);
      sendJson(res, 404, { error: 'qr_unavailable', state: status.state });
      return;
    }
    sendJson(res, 200, current);
  } catch (err) {
    sendJson(res, 500, { error: 'internal_error' });
  }
}

async function logoutHandler(req, res, companyId, ctx) {
  req.resume(); // descarta eventual corpo; endpoint nao consome payload
  const valid = normalizeCompanyId(companyId);
  if (!valid) {
    invalidCompany(res);
    return;
  }
  try {
    const record = await ctx.sessions.logout(valid);
    sendJson(res, 200, toPublicStatus(ctx.sessions, record));
  } catch (err) {
    sendJson(res, 500, { error: 'internal_error' });
  }
}

module.exports = { connectHandler, statusHandler, qrHandler, logoutHandler };
