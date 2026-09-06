'use strict';

// Logger entregue ao Baileys: nivel silencioso para nao despejar JIDs,
// metadata, credenciais ou qualquer dado sensivel nos logs.
// Os logs operacionais do gendaz continuam via console no nosso codigo.

const pino = require('pino');

const baileysLogger = pino({ level: 'silent' });

module.exports = { baileysLogger };
