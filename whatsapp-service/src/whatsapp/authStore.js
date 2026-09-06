'use strict';

// Abstracao de armazenamento de autenticacao/sessao do provider.
//
// Interface:
//   load(companyId)  -> Promise<{ state, saveCreds }>  (formato do Baileys)
//   clear(companyId) -> Promise<void>                   (remove credenciais)
//
// O SessionManager depende SOMENTE desta interface. Em fase posterior,
// FileAuthStateStore pode ser trocado por implementacao duravel
// (database-backed) sem reescrever o gerenciamento de sessoes.

const fs = require('fs');
const path = require('path');
const { useMultiFileAuthState } = require('@whiskeysockets/baileys');

class FileAuthStateStore {
  // Adapter de desenvolvimento/stage inicial sobre useMultiFileAuthState.
  // A propria documentacao do Baileys nao recomenda arquivos em producao;
  // a troca futura preserva a interface acima.
  constructor(baseDir) {
    this.baseDir = baseDir;
  }

  dirFor(companyId) {
    // companyId ja validado (charset restrito, sem separadores) — segmento unico.
    return path.join(this.baseDir, companyId);
  }

  async load(companyId) {
    const dir = this.dirFor(companyId);
    await fs.promises.mkdir(dir, { recursive: true, mode: 0o700 });
    return useMultiFileAuthState(dir);
  }

  async clear(companyId) {
    await fs.promises.rm(this.dirFor(companyId), { recursive: true, force: true });
  }
}

module.exports = { FileAuthStateStore };
