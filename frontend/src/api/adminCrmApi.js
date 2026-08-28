import api from './axiosConfig.js'

export async function buscarClientesAdmin(filtros = {}) {
  const { data } = await api.get('/admin/crm/empresas', {
    params: {
      segment: filtros.segment === 'todos' ? null : filtros.segment,
      search: filtros.search || null,
      orderBy: filtros.orderBy,
      period: filtros.period,
    },
  })
  return data
}

export async function enviarMensagemAdmin(empresaId, payload) {
  const { data } = await api.post(`/admin/crm/empresas/${empresaId}/enviar-mensagem`, payload)
  return data
}

export async function buscarHistoricoAdmin(empresaId) {
  const { data } = await api.get(`/admin/crm/empresas/${empresaId}/historico-contatos`)
  return data
}
