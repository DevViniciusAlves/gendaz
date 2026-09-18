'use strict';

// Abstracao de armazenamento de autenticacao/sessao do provider.
//
// Interface:
//   load(companyId)  -> Promise<{ state, saveCreds }>  (formato do Baileys)
//   clear(companyId) -> Promise<void>                   (remove credenciais)
//   listCompanies()  -> Promise<string[]>               (empresas com sessao registrada)
//   listPersistedCompanies() -> Promise<string[]>       (todas as empresas com sessao persistida, inclusive legacy registered=false)
//   hasRegisteredSession(companyId) -> Promise<boolean> (verifica se tem sessao valida)
//   flush(companyId) -> Promise<void>                   (aguarda writes pendentes)
//   flushAll()       -> Promise<void>                   (aguarda todos os writes)
//   close()          -> Promise<void>                   (encerra conexoes/pool)
//
// O SessionManager depende SOMENTE desta interface. Em fase posterior,
// FileAuthStateStore pode ser trocado por implementacao duravel
// (database-backed) sem reescrever o gerenciamento de sessoes.

const fs = require('fs');
const path = require('path');
const { useMultiFileAuthState } = require('@whiskeysockets/baileys');
const { PostgresAuthStateStore } = require('./postgresAuthStore');

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

  async listCompanies() {
    try {
      const entries = await fs.promises.readdir(this.baseDir, { withFileTypes: true });
      return entries.filter(e => e.isDirectory()).map(e => e.name);
    } catch (err) {
      if (err.code === 'ENOENT') return [];
      throw err;
    }
  }

  async listPersistedCompanies() {
    try {
      const entries = await fs.promises.readdir(this.baseDir, { withFileTypes: true });
      return entries.filter(e => e.isDirectory()).map(e => e.name);
    } catch (err) {
      if (err.code === 'ENOENT') return [];
      throw err;
    }
  }

  async clear(companyId) {
    await fs.promises.rm(this.dirFor(companyId), { recursive: true, force: true });
  }

  async hasRegisteredSession(companyId) {
    const dir = this.dirFor(companyId);
    try {
      const credsFile = path.join(dir, 'creds.json');
      const data = await fs.promises.readFile(credsFile, 'utf8');
      const creds = JSON.parse(data);
      return Boolean(creds.registered);
    } catch (err) {
      if (err.code === 'ENOENT') return false;
      throw err;
    }
  }

  async flush(companyId) {
    // No-op for file store since writes are synchronous
    return Promise.resolve();
  }

  async flushAll() {
    return Promise.resolve();
  }

  async close() {
    // No-op for file store
    return Promise.resolve();
  }
}

module.exports = { FileAuthStateStore, PostgresAuthStateStore };
