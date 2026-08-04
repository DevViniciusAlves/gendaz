import { requestOrLocal } from './request.js'
import api from './axiosConfig.js'

const empty = []

export const promocoesApi = {
  listar: (empresaId) => requestOrLocal((client) => client.get('/promocoes', { params: empresaId ? { empresaId } : undefined }), () => empty),
  criar: (payload, empresaId) => api.post('/promocoes', payload, { params: empresaId ? { empresaId } : undefined }),
  atualizar: (id, payload, empresaId) => api.put(`/promocoes/${id}`, payload, { params: empresaId ? { empresaId } : undefined }),
  desativar: (id, empresaId) => api.patch(`/promocoes/${id}/desativar`, null, { params: empresaId ? { empresaId } : undefined }),
  ativar: (id, empresaId) => api.patch(`/promocoes/${id}/ativar`, null, { params: empresaId ? { empresaId } : undefined }),
  excluir: (id, empresaId) => api.delete(`/promocoes/${id}`, { params: empresaId ? { empresaId } : undefined }),
  notificar: (id, payload, empresaId) => api.post(`/promocoes/${id}/notificar`, payload, { params: empresaId ? { empresaId } : undefined }),
  uso: (id, empresaId) => api.get(`/promocoes/${id}/uso`, { params: empresaId ? { empresaId } : undefined }),
  historico: (id, empresaId) => api.get(`/promocoes/${id}/historico`, { params: empresaId ? { empresaId } : undefined }),
}
