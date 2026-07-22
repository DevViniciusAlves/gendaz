import api from './axiosConfig.js'

export function buscarDashboardInsights(periodo = 30) {
  return api.get('/insights/dashboard', { params: { periodo } }).then((response) => response.data)
}

export function analisarPerguntaInsights(pergunta) {
  return api.post('/insights/analisar', { pergunta }).then((response) => response.data)
}

export function buscarHistoricoInsights() {
  return api.get('/insights/historico').then((response) => response.data)
}
