import clienteApi from './clienteApi.js'

function comCacheBuster(config = {}) {
  return {
    ...config,
    params: {
      ...(config.params || {}),
      _ts: Date.now(),
    },
  }
}

export const meuGendazPromocoesApi = {
  listar: () => clienteApi.get('/meu-gendaz/promocoes', { ...comCacheBuster(), skipMeuGendazLogout: true }).then((response) => response.data),
  usados: () => clienteApi.get('/meu-gendaz/cupons', { ...comCacheBuster(), skipMeuGendazLogout: true }).then((response) => response.data),
  notificacoes: () => clienteApi.get('/meu-gendaz/notificacoes', { ...comCacheBuster(), skipMeuGendazLogout: true }).then((response) => response.data),
  marcarLida: (promocaoId) => clienteApi.patch(`/meu-gendaz/notificacoes/${promocaoId}/lido`, {}, { skipMeuGendazLogout: true }).then((response) => response.data),
}
