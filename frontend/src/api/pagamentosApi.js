import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'

export const pagamentosApi = {
  listar: () => requestOrLocal((api) => api.get('/pagamentos/empresa/1'), () => getData().pagamentos),
}
