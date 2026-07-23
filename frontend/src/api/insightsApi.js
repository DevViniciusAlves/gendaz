import api from './axiosConfig.js'

function obterEmpresaIdUsuario() {
  const usuarioJson = localStorage.getItem('agendapro_usuario')
  if (!usuarioJson) return null

  try {
    const usuario = JSON.parse(usuarioJson)
    return usuario?.empresaId || usuario?.empresa?.id || usuario?.id || null
  } catch (error) {
    console.warn('[insights-api] erro ao ler usuario para empresaId', error)
    return null
  }
}

export function buscarDashboardInsights(periodo = 30) {
  const empresaId = obterEmpresaIdUsuario()
  return api.get('/insights/dashboard', {
    params: {
      periodo,
      empresaId: empresaId || undefined,
    },
  }).then((response) => response.data)
}

export function analisarPerguntaInsights(pergunta) {
  const empresaId = obterEmpresaIdUsuario()
  return api.post('/insights/analisar', { pergunta }, {
    params: {
      empresaId: empresaId || undefined,
    },
  }).then((response) => response.data)
}

export function buscarHistoricoInsights() {
  const empresaId = obterEmpresaIdUsuario()
  return api.get('/insights/historico', {
    params: {
      empresaId: empresaId || undefined,
    },
  }).then((response) => response.data)
}
