'use strict';

const crypto = require('crypto');
const config = require('../config');

// Helper preparado para proteger futuros endpoints internos com:
//   Authorization: Bearer <WHATSAPP_INTERNAL_TOKEN>
//
// Nao utilizado pelo /health (publico). Retorna true somente quando um token
// esta configurado E o cabecalho confere (comparacao em tempo constante).
function isAuthorized(req) {
  if (!config.internalToken) {
    return false;
  }
  const header = req.headers.authorization || '';
  const [scheme, value] = header.split(' ');
  if (scheme !== 'Bearer' || !value) {
    return false;
  }
  const expected = Buffer.from(config.internalToken, 'utf8');
  const received = Buffer.from(value, 'utf8');
  if (expected.length !== received.length) {
    return false;
  }
  return crypto.timingSafeEqual(expected, received);
}

// Middleware opcional para rotas internas futuras: responde 503 quando o
// servico esta sem token configurado, 401 quando nao autorizado.
function requireInternalAuth(req, res, next) {
  if (!config.internalToken) {
    res.writeHead(503, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'service_unavailable' }));
    return;
  }
  if (!isAuthorized(req)) {
    res.writeHead(401, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'unauthorized' }));
    return;
  }
  next();
}

module.exports = { isAuthorized, requireInternalAuth };
