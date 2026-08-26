import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'
import { getSessionUser } from './axiosConfig.js'

function periodoAtual() {
  const agora = new Date()
  return {
    mes: String(agora.getMonth() + 1).padStart(2, '0'),
    ano: agora.getFullYear(),
  }
}

export const financeiroApi = {
  resumo: () => {
    const periodo = periodoAtual()
    const usuario = getSessionUser()
    const empresaId = usuario?.empresaId || usuario?.empresa?.id || null
    const query = empresaId ? `?empresaId=${empresaId}&mes=${periodo.mes}&ano=${periodo.ano}` : `?mes=${periodo.mes}&ano=${periodo.ano}`
    return requestOrLocal((api) => api.get(`/financeiro/resumo${query}`), () => getData())
  },
}
