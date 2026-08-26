import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'

export const profissionaisApi = {
  listar: () => requestOrLocal((api) => api.get('/profissionais/empresa/1'), () => getData().profissionais),
}
