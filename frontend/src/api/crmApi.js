import api from './axiosConfig.js'

export async function buscarClientesCrm(filtros = {}, options = {}) {
  const usuarioJson = localStorage.getItem('agendapro_usuario')
  let empresaId = null

  if (usuarioJson) {
    try {
      const usuario = JSON.parse(usuarioJson)
      empresaId = usuario.empresa?.id || usuario.empresaId || usuario.id
    } catch (e) {
      console.warn('Erro ao parsear usuario:', e)
    }
  }

  const { data } = await api.get('/crm/clientes', {
    params: {
      empresaId: empresaId || '',
      segment: filtros.segment === 'todos' ? null : filtros.segment,
      search: filtros.search || null,
      orderBy: filtros.orderBy,
      period: filtros.period,
    },
    ...options,
  })

  return data
}
export async function enviarMensagemCrm(clienteId, payload) {
  const usuario = JSON.parse(localStorage.getItem('agendapro_usuario') || 'null')
  const { data } = await api.post(`/crm/clientes/${clienteId}/enviar-mensagem`, payload, {
    headers: {
      'X-Usuario-Id': usuario?.id || '',
    },
  })
  return data
}

export async function buscarHistoricoContatos(clienteId) {
  const { data } = await api.get(`/crm/clientes/${clienteId}/historico-contatos`)
  return data
}
