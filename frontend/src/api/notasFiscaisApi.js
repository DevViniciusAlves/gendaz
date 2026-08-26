import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'

export const notasFiscaisApi = {
  listar: () => requestOrLocal((api) => api.get('/notas-fiscais/empresa/1'), () => getData().notasFiscais),
}
