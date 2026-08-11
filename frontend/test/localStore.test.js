import test from 'node:test'
import assert from 'node:assert/strict'
import { PLANOS, clearSensitiveStorage } from '../src/services/localStore.js'

test('PLANOS define rotas basicas para BASICO e PRO', () => {
  assert.ok(PLANOS.BASICO.rotas.includes('dashboard'))
  assert.ok(PLANOS.PRO.rotas.includes('insights'))
})

test('clearSensitiveStorage remove chaves sensiveis de cache e pendencia', () => {
  const removidas = []
  global.window = {
    localStorage: {
      length: 4,
      key(index) {
        return ['gendaz_scope_cache_x', 'gendaz_insights_chat_y', 'foo_pagamento_pendente', 'outra-chave'][index] ?? null
      },
      removeItem(chave) {
        removidas.push(chave)
      },
    },
  }

  clearSensitiveStorage()

  assert.deepEqual(removidas.sort(), ['foo_pagamento_pendente', 'gendaz_insights_chat_y', 'gendaz_scope_cache_x'].sort())
  delete global.window
})
