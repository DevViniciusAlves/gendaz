import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'

export const servicosApi = {
  listar: () => requestOrLocal((api) => api.get('/servicos/empresa/1'), () => getData().servicos),
  listarPorEmpresa: (empresaId) => requestOrLocal((api) => api.get(`/servicos/empresa/${empresaId}`), () => getData().servicos),
}
