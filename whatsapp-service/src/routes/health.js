'use strict';

// Rota publica de health check. Nao exige autenticacao e nao expoe
// nenhum dado sensivel.

function healthHandler(req, res) {
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(
    JSON.stringify({
      status: 'ok',
      service: 'whatsapp-service',
      timestamp: new Date().toISOString(),
    })
  );
}

module.exports = { healthHandler };
