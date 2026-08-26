import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'

export const entregasApi = {
  listar: () => requestOrLocal((api) => api.get('/entregas/empresa/1'), () => getData().entregas),
}
