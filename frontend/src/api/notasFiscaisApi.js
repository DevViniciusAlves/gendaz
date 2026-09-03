import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'
import { getSessionUser } from './axiosConfig.js'

export const notasFiscaisApi = {
  listar: () => {
    const usuario = getSessionUser()
    const empresaId = usuario?.empresaId
    return requestOrLocal(
      (api) => api.get(empresaId ? `/notas-fiscais/empresa/${empresaId}` : '/notas-fiscais'),
      () => getData().notasFiscais
    )
  },
}
