import { useCallback, useMemo, useState } from 'react'
import { analisarPerguntaInsightsComHistorico, recalcularInsights } from '../api/insightsApi.js'
import { useLocalData } from './useLocalData.js'
import { persistirCacheLocal } from './useLocalData.js'

function normalizarDashboard(data) {
  return data?.dashboard || data?.dashboardResumo || data?.resumo || null
}

export function useInsights() {
  const [data, , { loading, error, reload }] = useLocalData('insights')
  const [dashboardAtual, setDashboardAtual] = useState(null)

  const dashboardBase = useMemo(() => normalizarDashboard(data), [data])
  const dashboard = dashboardAtual || dashboardBase
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
    if (resposta) {
      setDashboardAtual(resposta)
      persistirCacheLocal('insights', resposta)
    }
    await reload(false)
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
