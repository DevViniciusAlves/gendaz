import api from './axiosConfig.js'

function obterEmpresaIdUsuario() {
  const usuarioJson = localStorage.getItem('agendapro_usuario')
  if (!usuarioJson) return null

  try {
    const usuario = JSON.parse(usuarioJson)
    return usuario.empresa?.id || usuario.empresaId || usuario.id || null
  } catch (e) {
    console.warn('Erro ao parsear usuario:', e)
    return null
  }
}

export async function buscarClientesCrm(filtros = {}, options = {}) {
  const empresaId = obterEmpresaIdUsuario()

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
  const empresaId = obterEmpresaIdUsuario()
  const usuario = JSON.parse(localStorage.getItem('agendapro_usuario') || 'null')
  const { data } = await api.post(`/crm/clientes/${clienteId}/enviar-mensagem`, payload, {
    params: {
      empresaId: empresaId || '',
    },
    headers: {
      'X-Usuario-Id': usuario?.id || '',
    },
  })
  return data
}

export async function buscarHistoricoContatos(clienteId) {
  const empresaId = obterEmpresaIdUsuario()
  const { data } = await api.get(`/crm/clientes/${clienteId}/historico-contatos`, {
    params: {
      empresaId: empresaId || '',
    },
  })
  return data
}
