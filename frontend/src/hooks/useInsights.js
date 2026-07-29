import { useCallback, useMemo } from 'react'
import { analisarPerguntaInsightsComHistorico, recalcularInsights } from '../api/insightsApi.js'
import { useLocalData } from './useLocalData.js'

function normalizarDashboard(data) {
  return data?.dashboard || data?.dashboardResumo || data?.resumo || null
}

export function useInsights() {
  const [data, , { loading, error, reload }] = useLocalData('insights')

  const dashboard = useMemo(() => normalizarDashboard(data), [data])
  const historico = useMemo(() => {
    if (Array.isArray(data?.historico)) return data.historico
    return data?.mensagens || []
  }, [data])

  const analisar = useCallback(async (pergunta, historicoChat = []) => {
    const resposta = await analisarPerguntaInsightsComHistorico(pergunta, historicoChat)
    await reload(true)
    return resposta
  }, [reload])

  const recarregar = useCallback(async (periodo = 30) => {
    const resposta = await recalcularInsights(periodo)
    await reload(true)
    return resposta
  }, [reload])

  return {
    dashboard,
    historico,
    loading,
    error,
    recarregar,
    analisar,
  }
}
