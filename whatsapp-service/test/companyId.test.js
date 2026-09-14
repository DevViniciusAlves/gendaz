'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { normalizeCompanyId } = require('../src/whatsapp/companyId');

describe('normalizeCompanyId', () => {
  it('aceita slugs e uuids simples', () => {
    assert.equal(normalizeCompanyId('empresa-1'), 'empresa-1');
    assert.equal(normalizeCompanyId('abc_DEF-123'), 'abc_DEF-123');
    assert.equal(normalizeCompanyId('  empresa-1  '), 'empresa-1');
  });

  it('rejeita path traversal e caracteres arbitrarios', () => {
    for (const bad of [
      '../segredo',
      '..',
      'a/b',
      'a\\b',
      'a b',
      'a.b',
      '',
      '   ',
      'a'.repeat(65),
      null,
      undefined,
      123,
      {},
      'empresa/1?q=x',
      '.hidden',
    ]) {
      assert.equal(normalizeCompanyId(bad), null, `deveria rejeitar: ${JSON.stringify(bad)}`);
    }
  });
});
