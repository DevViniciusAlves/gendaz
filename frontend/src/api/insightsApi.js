import api, { getSessionUser } from './axiosConfig.js'

function obterEmpresaIdUsuario() {
  const usuarioCache = getSessionUser()
  if (usuarioCache) {
    return usuarioCache?.empresaId || usuarioCache?.empresa?.id || null
  }
  return null
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
  return analisarPerguntaInsightsComHistorico(pergunta, [])
}

export function analisarPerguntaInsightsComHistorico(pergunta, historico = []) {
  const empresaId = obterEmpresaIdUsuario()
  return api.post('/insights/analisar', {
    pergunta,
    historico: Array.isArray(historico) ? historico : [],
  }, {
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

export function recalcularInsights(periodo = 30) {
  const empresaId = obterEmpresaIdUsuario()
  return api.post('/insights/recalcular', null, {
    params: {
      periodo,
      empresaId: empresaId || undefined,
    },
  }).then((response) => response.data)
}
