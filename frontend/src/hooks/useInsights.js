import { useCallback, useMemo } from 'react'
import { appApi } from '../api/appApi.js'
import { useLocalData } from './useLocalData.js'

function normalizarDashboard(data) {
  return data?.dashboard || data?.dashboardResumo || null
}

export function useInsights() {
  const [data, , { loading, error, reload }] = useLocalData('insights')

  const dashboard = useMemo(() => normalizarDashboard(data), [data])
  const historico = useMemo(() => {
    if (Array.isArray(data?.historico)) return data.historico
    return data?.mensagens || []
  }, [data])

  const analisar = useCallback(async (pergunta, historicoChat = []) => {
    const resposta = await appApi.analisarPerguntaInsightsComHistorico(pergunta, historicoChat)
    await reload(true)
    return resposta
  }, [reload])

  return {
    dashboard,
    historico,
    loading,
    error,
    recarregar: reload,
    analisar,
  }
}
