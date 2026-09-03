import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'
import { getSessionUser } from './axiosConfig.js'

export const conversasApi = {
  listar: () => {
    const usuario = getSessionUser()
    const empresaId = usuario?.empresaId
    return requestOrLocal(
      (api) => api.get(empresaId ? `/conversas/empresa/${empresaId}` : '/conversas'),
      () => getData().conversas
    )
  },
}
