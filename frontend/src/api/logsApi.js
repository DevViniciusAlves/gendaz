import { requestOrLocal } from './request.js'

const paginaVazia = {
  content: [],
  totalElements: 0,
  totalPages: 1,
  number: 0,
  size: 20,
}

export const logsApi = {
  listar: (params = {}) =>
    requestOrLocal(
      (api) => api.get('/logs-atividade', { params }),
      () => paginaVazia,
    ),
}
