import clienteApi from './clienteApi.js'

export const meuGendazPromocoesApi = {
  listar: () => clienteApi.get('/meu-gendaz/promocoes').then((response) => response.data),
  usados: () => clienteApi.get('/meu-gendaz/cupons').then((response) => response.data),
  notificacoes: () => clienteApi.get('/meu-gendaz/notificacoes').then((response) => response.data),
  marcarLida: (promocaoId) => clienteApi.patch(`/meu-gendaz/notificacoes/${promocaoId}/lido`).then((response) => response.data),
}
