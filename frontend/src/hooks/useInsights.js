import { useCallback, useMemo, useState } from 'react'
import { analisarPerguntaInsightsComHistorico, recalcularInsights } from '../api/insightsApi.js'
import { useLocalData } from './useLocalData.js'
import { persistirCacheLocal } from './useLocalData.js'

function normalizarDashboard(data) {
  return data?.dashboard || data?.dashboardResumo || data?.resumo || null
}

const INSIGHTS_SYNC_CACHE_KEY = 'agendapro_insights_sync_last'

function lerSyncInsights() {
  try {
    const raw = localStorage.getItem(INSIGHTS_SYNC_CACHE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed?.dashboard) return null
    return parsed
  } catch {
    return null
  }
}

function salvarSyncInsights(dashboard) {
  if (!dashboard) return
  try {
    localStorage.setItem(INSIGHTS_SYNC_CACHE_KEY, JSON.stringify({
      dashboard,
      empresaId: dashboard?.empresaId || null,
      salvoEm: Date.now(),
    }))
  } catch {
    return
  }
}

export function useInsights() {
  const [data, , { loading, error, reload }] = useLocalData('insights')
  const [dashboardAtual, setDashboardAtual] = useState(() => lerSyncInsights()?.dashboard || null)

  const dashboardBase = useMemo(() => normalizarDashboard(data), [data])
  const dashboardPersistido = useMemo(() => lerSyncInsights()?.dashboard || null, [data])
  const dashboard = dashboardAtual || dashboardPersistido || dashboardBase
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
      salvarSyncInsights(resposta)
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
