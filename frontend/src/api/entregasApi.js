import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'
import { getSessionUser } from './axiosConfig.js'

export const entregasApi = {
  listar: () => {
    const usuario = getSessionUser()
    const empresaId = usuario?.empresaId
    return requestOrLocal(
      (api) => api.get(empresaId ? `/entregas/empresa/${empresaId}` : '/entregas'),
      () => getData().entregas
    )
  },
}
