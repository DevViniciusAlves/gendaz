import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'

export const clientesApi = {
  listar: () => requestOrLocal((api) => api.get('/clientes/empresa/1'), () => getData().clientes),
  listarPorEmpresa: (empresaId) => requestOrLocal((api) => api.get(`/clientes/empresa/${empresaId}`), () => getData().clientes),
}
