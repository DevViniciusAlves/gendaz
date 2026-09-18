'use strict';

// Fabrica do socket real do Baileys (linha 7.x).
// Separada do SessionManager para permitir injecao de fake nos testes.
//
// Minimiza dados processados (V1 e somente envio):
// - sem QR no terminal, sem QR bruto nos logs;
// - sem sincronizacao de historico (syncFullHistory + shouldSyncHistoryMessage);
// - sem marcar online ao conectar.
// Nenhum listener de messages.upsert / chatbot / inbox e registrado.

const makeWASocket = require('@whiskeysockets/baileys').default;
const { baileysLogger } = require('./logger');

function createSocket({ authState }) {
  return makeWASocket({
    auth: authState,
    logger: baileysLogger,
    browser: ['Gendaz WhatsApp Service', 'Chrome', '1.0.0'],
    printQRInTerminal: false,
    syncFullHistory: false,
    shouldSyncHistoryMessage: () => false,
    markOnlineOnConnect: false,
  });
}

module.exports = { createSocket };
