import api from './axiosConfig.js'

function extrairMensagemErro(error) {
  return error.response?.data?.mensagem
    || error.response?.data?.message
    || Object.values(error.response?.data?.campos || {})[0]
    || 'Nao foi possivel entrar no painel admin.'
}

export const adminApi = {
  access() {
    return api.get('/admin/access')
  },

  refresh() {
    return api.get('/admin/auth/refresh').then((response) => response.data)
  },

  async login(email, senha) {
    try {
      const response = await api.post('/admin/auth/login', { email: email.trim().toLowerCase(), senha })
      if (response.data?.admin?.perfil !== 'SUPER_ADMIN') {
        throw new Error('Resposta admin invalida.')
      }
      return response.data
    } catch (error) {
      const mensagem = error.response ? extrairMensagemErro(error) : (error.message || 'Nao foi possivel entrar no painel admin.')
      throw new Error(mensagem)
    }
  },

  logout() {
    return api.post('/admin/auth/logout').then((response) => response.data)
  },

  dashboard() {
    return api.get('/admin/dashboard').then((response) => response.data)
  },

  usuarios() {
    return api.get('/admin/usuarios').then((response) => response.data)
  },

  pagamentos(params = {}) {
    return api.get('/admin/pagamentos', { params }).then((response) => response.data)
  },

  aprovarPagamentoManualmente(id, payload = {}) {
    return api.post(`/admin/pagamentos/${id}/aprovar-manualmente`, payload).then((response) => response.data)
  },

  desaprovarPagamentoManualmente(id, payload) {
    return api.post(`/admin/pagamentos/${id}/desaprovar-manualmente`, payload).then((response) => response.data)
  },

  logs() {
    return api.get('/admin/logs').then((response) => response.data)
  },

  configuracoes() {
    return api.get('/admin/configuracoes').then((response) => response.data)
  },

  chamados() {
    return api.get('/admin/chamados').then((response) => response.data)
  },

  planos() {
    return api.get('/planos').then((response) => response.data)
  },

  impersonar(empresaId, motivo) {
    const payload = motivo ? { motivo } : {}
    return api.post(`/admin/empresas/${empresaId}/impersonar`, payload).then((response) => response.data)
  },

  ativarEmpresa(empresaId, motivo) {
    return api.post(`/admin/empresas/${empresaId}/ativar`, { motivo }).then((response) => response.data)
  },

  desativarEmpresa(empresaId, motivo) {
    return api.post(`/admin/empresas/${empresaId}/desativar`, { motivo }).then((response) => response.data)
  },

  atualizarEmpresa(empresaId, payload) {
    return api.put(`/admin/empresas/${empresaId}`, payload).then((response) => response.data)
  },

  atualizarChamado(chamadoId, payload) {
    return api.patch(`/admin/chamados/${chamadoId}`, payload).then((response) => response.data)
  },

  atualizarStatusChamado(chamadoId, status) {
    return api.patch(`/admin/chamados/${chamadoId}/status`, { status }).then((response) => response.data)
  },

  encerrarImpersonacao(sessionId) {
    return api.post(`/admin/impersonacoes/${sessionId}/encerrar`, {}).then((response) => response.data)
  },
}
