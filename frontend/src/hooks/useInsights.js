import { useCallback, useMemo, useState } from 'react'
import { analisarPerguntaInsightsComHistorico, recalcularInsights } from '../api/insightsApi.js'
import { persistirCacheLocal, useLocalData } from './useLocalData.js'

function normalizarDashboard(data) {
  const dashboard = data?.dashboard || data?.dashboardResumo || data?.resumo || null
  if (!dashboard || dashboard?.sincronizado === false || !dashboard?.geradoEm) return null
  return dashboard
}

export function useInsights() {
  const [data, , { loading, error, reload }] = useLocalData('insights')
  const [dashboardAtual, setDashboardAtual] = useState(null)

  const dashboardBase = useMemo(() => normalizarDashboard(data), [data])
  const dashboard = dashboardAtual || dashboardBase
  const semSnapshot = !loading && !dashboard
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
      persistirCacheLocal('insights', {
        ...(data || {}),
        dashboard: resposta,
        dashboardResumo: resposta,
        resumo: resposta,
      })
      setDashboardAtual(resposta)
    }
    return resposta
  }, [data])

  return {
    dashboard,
    historico,
    loading,
    error,
    semSnapshot,
    recarregar,
    analisar,
  }
}
