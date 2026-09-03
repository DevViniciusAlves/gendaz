import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'
import { getSessionUser } from './axiosConfig.js'

export const servicosApi = {
  listar: () => {
    const usuario = getSessionUser()
    const empresaId = usuario?.empresaId
    return requestOrLocal(
      (api) => api.get(empresaId ? `/servicos/empresa/${empresaId}` : '/servicos'),
      () => getData().servicos
    )
  },
  listarPorEmpresa: (empresaId) =>
    requestOrLocal(
      (api) => api.get(empresaId ? `/servicos/empresa/${empresaId}` : '/servicos'),
      () => getData().servicos
    ),
}
