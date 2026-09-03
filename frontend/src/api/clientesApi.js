import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'
import { getSessionUser } from './axiosConfig.js'

export const clientesApi = {
  listar: () => {
    const usuario = getSessionUser()
    const empresaId = usuario?.empresaId
    return requestOrLocal(
      (api) => api.get(empresaId ? `/clientes/empresa/${empresaId}` : '/clientes'),
      () => getData().clientes
    )
  },
  listarPorEmpresa: (empresaId) =>
    requestOrLocal(
      (api) => api.get(empresaId ? `/clientes/empresa/${empresaId}` : '/clientes'),
      () => getData().clientes
    ),
}
