'use strict';

// Montagem das rotas sobre http puro (sem dependencias externas).
// - GET /health: publico.
// - /internal/whatsapp/sessions/*: protegidos por Bearer (middleware auth).
// - POST /internal/whatsapp/sessions/{companyId}/messages/text: envio de
//   texto (V1), tambem protegido por Bearer.
// Qualquer rota desconhecida => 404. Erros inesperados de rota caem no
// tratamento global (500 generico, sem vazar detalhes internos).

const { healthHandler } = require('./routes/health');
const { connectHandler, statusHandler, qrHandler, logoutHandler } = require('./routes/sessions');
const { messagesTextHandler } = require('./routes/messages');
const { requireInternalAuth } = require('./middleware/auth');

function notFoundHandler(req, res) {
  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'not_found' }));
}

function methodNotAllowedHandler(res, allowed) {
  res.writeHead(405, { 'Content-Type': 'application/json', Allow: allowed.join(', ') });
  res.end(JSON.stringify({ error: 'method_not_allowed' }));
}

// POST /internal/whatsapp/sessions/{companyId}/connect
// GET  /internal/whatsapp/sessions/{companyId}/status
// GET  /internal/whatsapp/sessions/{companyId}/qr
// POST /internal/whatsapp/sessions/{companyId}/logout
// POST /internal/whatsapp/sessions/{companyId}/messages/text
const SESSION_ROUTE = /^\/internal\/whatsapp\/sessions\/([^/]+)\/(connect|status|qr|logout)$/;
const MESSAGES_TEXT_ROUTE = /^\/internal\/whatsapp\/sessions\/([^/]+)\/messages\/text$/;

function createApp(ctx) {
  return (req, res) => {
    try {
      const url = new URL(req.url || '/', 'http://localhost');
      const pathname = url.pathname;

      if (req.method === 'GET' && pathname === '/health') {
        healthHandler(req, res);
        return;
      }

      const match = SESSION_ROUTE.exec(pathname);
      const messagesMatch = !match && req.method === 'POST' ? MESSAGES_TEXT_ROUTE.exec(pathname) : null;
      if (messagesMatch) {
        const [, companyId] = messagesMatch;
        // companyId bruto vem do path; o handler valida/normaliza antes de usar.
        requireInternalAuth(req, res, () => {
          messagesTextHandler(req, res, companyId, ctx).catch(() => {
            if (!res.headersSent) {
              res.writeHead(500, { 'Content-Type': 'application/json' });
            }
            try {
              res.end(JSON.stringify({ error: 'internal_error' }));
            } catch (_) {
              // socket ja encerrado; nada a fazer
            }
          });
        });
        return;
      }
      if (match) {
        const [, companyId, action] = match;
        const expectedMethod = action === 'connect' || action === 'logout' ? 'POST' : 'GET';
        if (req.method !== expectedMethod) {
          methodNotAllowedHandler(res, [expectedMethod]);
          return;
        }
        // companyId bruto vem do path; cada handler valida/normaliza antes de usar.
        requireInternalAuth(req, res, () => {
          const handler =
            action === 'connect'
              ? connectHandler
              : action === 'status'
                ? statusHandler
                : action === 'qr'
                  ? qrHandler
                  : logoutHandler;
          handler(req, res, companyId, ctx).catch(() => {
            if (!res.headersSent) {
              res.writeHead(500, { 'Content-Type': 'application/json' });
            }
            try {
              res.end(JSON.stringify({ error: 'internal_error' }));
            } catch (_) {
              // socket ja encerrado; nada a fazer
            }
          });
        });
        return;
      }

      notFoundHandler(req, res);
    } catch (err) {
      console.error(`[whatsapp-service] erro inesperado na requisicao ${req.method} ${req.url}:`, err.message);
      if (!res.headersSent) {
        res.writeHead(500, { 'Content-Type': 'application/json' });
      }
      try {
        res.end(JSON.stringify({ error: 'internal_error' }));
      } catch (_) {
        // socket ja encerrado; nada a fazer
      }
    }
  };
}

module.exports = { createApp };
