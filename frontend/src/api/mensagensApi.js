import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'

export const mensagensApi = {
  listarPorConversa: (conversaId) => requestOrLocal((api) => api.get(`/mensagens/conversa/${conversaId}`), () => getData().mensagens.filter((m) => m.conversaId === conversaId)),
}
