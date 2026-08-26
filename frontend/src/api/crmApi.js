import api from './axiosConfig.js'
import { getSessionUser } from './axiosConfig.js'

function obterEmpresaIdUsuario() {
  const usuario = getSessionUser()
  return usuario?.empresa?.id || usuario?.empresaId || usuario?.id || null
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
  const usuario = getSessionUser()
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
