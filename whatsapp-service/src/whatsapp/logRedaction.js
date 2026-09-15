'use strict';

// Redacao dos dumps criptograficos do libsignal.
//
// Origem confirmada: node_modules/libsignal/src/session_record.js usa
// console.info("Closing session:", session) / console.info("Opening session:", ...)
// despejando o SessionEntry inteiro (privKey, rootKey, ephemeralKeyPair,
// remoteIdentityKey, ...). O logger pino 'silent' entregue ao Baileys nao
// alcanca esse codigo porque o libsignal chama o console global direto, e
// nao existe opcao suportada na dependencia para silencia-lo.
//
// Estrategia (minima e explicita, sem editar node_modules): envolve
// console.info/console.warn UMA vez no boot e redige SOMENTE:
//   - mensagens "Closing session:" / "Opening session:" (qualquer sufixo),
//   - o objeto SessionEntry que as acompanha (detectado por indexInfo +
//     pelo menos uma chave sensivel conhecida).
// Todo o resto passa intacto para o console original, preservando os logs
// operacionais do gendaz. Nunca imprime chaves, credenciais ou auth state.

const REDACTED_NOTE = '[libsignal] session lifecycle event redacted';

const LIFECYCLE_PATTERN = /^(Closing|Opening) session:\s*$/;

const SENSITIVE_KEYS = new Set([
  'privKey',
  'rootKey',
  'ephemeralKeyPair',
  'remoteIdentityKey',
  'currentRatchet',
]);

function isSessionEntry(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return false;
  }
  if (!('indexInfo' in value)) {
    return false;
  }
  return Object.keys(value).some((key) => SENSITIVE_KEYS.has(key));
}

function redactArgs(args) {
  let changed = false;
  const out = args.map((arg, i) => {
    if (typeof arg === 'string' && LIFECYCLE_PATTERN.test(arg)) {
      changed = true;
      return arg.replace(LIFECYCLE_PATTERN, '[libsignal] session lifecycle event:');
    }
    if (isSessionEntry(arg) && i > 0 && typeof args[i - 1] === 'string'
      && /(Closing|Opening) session/i.test(args[i - 1])) {
      changed = true;
      return REDACTED_NOTE;
    }
    return arg;
  });
  return { out, changed };
}

function installLibsignalLogRedaction(target) {
  const targetConsole = target || console;
  if (!targetConsole || targetConsole.__gendazLibsignalRedacted) {
    return targetConsole;
  }
  for (const method of ['info', 'warn']) {
    const original = targetConsole[method];
    if (typeof original !== 'function') {
      continue;
    }
    const bound = original.bind(targetConsole);
    targetConsole[method] = (...args) => {
      const { out, changed } = redactArgs(args);
      if (!changed) {
        return bound(...args);
      }
      return bound(...out);
    };
  }
  Object.defineProperty(targetConsole, '__gendazLibsignalRedacted', {
    value: true,
    enumerable: false,
    configurable: true,
    writable: true,
  });
  return targetConsole;
}

module.exports = {
  installLibsignalLogRedaction,
  REDACTED_NOTE,
};
