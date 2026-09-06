'use strict';

// Montagem das rotas sobre http puro (sem dependencias externas).
// Erros inesperados de rota caem no tratamento global abaixo (500 generico,
// sem vazar detalhes internos).

const { healthHandler } = require('./routes/health');

function notFoundHandler(req, res) {
  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'not_found' }));
}

function createApp() {
  return (req, res) => {
    try {
      const url = new URL(req.url || '/', 'http://localhost');
      if (req.method === 'GET' && url.pathname === '/health') {
        healthHandler(req, res);
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
