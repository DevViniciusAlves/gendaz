import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'

export const agendamentosApi = {
  listar: () => requestOrLocal((api) => api.get('/agendamentos/empresa/1'), () => getData().agendamentos),
}
