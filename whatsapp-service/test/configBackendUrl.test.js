'use strict';
const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { validateBackendUrl } = require('../src/config/index');

describe('DeliveryOutboxWorker - URL Validation Tests', () => {
  it('should accept valid URLs', () => {
    assert.equal(validateBackendUrl('https://gendaz-stage.onrender.com').valid, true);
    assert.equal(validateBackendUrl('https://gendaz-stage.onrender.com/').valid, true);
  });

  it('should reject invalid URLs', () => {
    assert.equal(validateBackendUrl('gendaz-stage.onrender.com').valid, false);
    assert.equal(validateBackendUrl('ftp://example.com').valid, false);
    assert.equal(validateBackendUrl('https://gendaz-stage.onrender.com/api').valid, false);
    assert.equal(validateBackendUrl('https://user:pass@example.com').valid, false);
    assert.equal(validateBackendUrl('https://example.com?token=x').valid, false);
  });

  it('should reject URL with full path /internal/whatsapp/delivery', () => {
    assert.equal(validateBackendUrl('https://gendaz-stage.onrender.com/internal/whatsapp/delivery').valid, false);
  });

  it('should reject URL with fragment', () => {
    assert.equal(validateBackendUrl('https://example.com/#fragment').valid, false);
  });
});
