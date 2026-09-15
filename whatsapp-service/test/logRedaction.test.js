'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { installLibsignalLogRedaction, REDACTED_NOTE } = require('../src/whatsapp/logRedaction');

function fakeConsole() {
  const calls = { info: [], warn: [] };
  return {
    calls,
    info: (...args) => calls.info.push(args),
    warn: (...args) => calls.warn.push(args),
  };
}

function sessionEntryLike() {
  return {
    _chains: {},
    registrationId: 123,
    ephemeralKeyPair: { privKey: 'SECRETO', pubKey: 'x' },
    privKey: Buffer.from('segredo'),
    rootKey: Buffer.from('segredo'),
    remoteIdentityKey: Buffer.from('segredo'),
    indexInfo: { closed: 1, used: 2 },
  };
}

describe('logRedaction (libsignal)', () => {
  it('redige dump de Closing session sem imprimir chaves', () => {
    const fake = fakeConsole();
    installLibsignalLogRedaction(fake);
    fake.info('Closing session:', sessionEntryLike());
    assert.equal(fake.calls.info.length, 1);
    const logged = JSON.stringify(fake.calls.info[0]);
    assert.ok(!logged.includes('SECRETO'), 'vazou privKey');
    assert.ok(!logged.includes('segredo'), 'vazou chave');
    assert.ok(logged.includes(REDACTED_NOTE));
  });

  it('redige Opening session e preserva outros logs intactos', () => {
    const fake = fakeConsole();
    installLibsignalLogRedaction(fake);
    fake.info('Opening session:', sessionEntryLike());
    fake.info('[whatsapp-service] send company=2 request=abc ok');
    fake.warn('algum aviso operacional');
    assert.equal(fake.calls.info.length, 2);
    assert.ok(!JSON.stringify(fake.calls.info[0]).includes('SECRETO'));
    assert.deepEqual(fake.calls.info[1], ['[whatsapp-service] send company=2 request=abc ok']);
    assert.deepEqual(fake.calls.warn[0], ['algum aviso operacional']);
  });

  it('instalacao e idempotente', () => {
    const fake = fakeConsole();
    installLibsignalLogRedaction(fake);
    const firstInfo = fake.info;
    installLibsignalLogRedaction(fake);
    assert.equal(fake.info, firstInfo);
  });
});
