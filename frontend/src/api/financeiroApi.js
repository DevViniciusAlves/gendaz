import { requestOrLocal } from './request.js'
import { getData } from '../services/localStore.js'

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
    return requestOrLocal((api) => api.get(`/financeiro/resumo?empresaId=1&mes=${periodo.mes}&ano=${periodo.ano}`), () => getData())
  },
}
