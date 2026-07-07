import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'

export const conversasApi = {
  listar: () => requestOrLocal((api) => api.get('/conversas/empresa/1'), () => getData().conversas),
}
