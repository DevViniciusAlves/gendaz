import { useCallback, useEffect, useState } from 'react'
import { analisarPerguntaInsights, buscarDashboardInsights, buscarHistoricoInsights } from '../api/insightsApi.js'

export function useInsights() {
  const [dashboard, setDashboard] = useState(null)
  const [historico, setHistorico] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const carregar = useCallback(async (periodo = 30) => {
    setLoading(true)
    setError(null)
    try {
      const [dashboardData, historicoData] = await Promise.all([
        buscarDashboardInsights(periodo),
        buscarHistoricoInsights().catch(() => []),
      ])
      setDashboard(dashboardData)
      setHistorico(Array.isArray(historicoData) ? historicoData : [])
    } catch (err) {
      setError(err?.response?.data?.mensagem || err?.message || 'Não foi possível carregar Insights.')
      setDashboard(null)
      setHistorico([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    carregar(30)
  }, [carregar])

  const analisar = useCallback(async (pergunta) => {
    const resposta = await analisarPerguntaInsights(pergunta)
    const historicoAtualizado = await buscarHistoricoInsights().catch(() => historico)
    setHistorico(Array.isArray(historicoAtualizado) ? historicoAtualizado : historico)
    return resposta
  }, [historico])

  return {
    dashboard,
    historico,
    loading,
    error,
    recarregar: carregar,
    analisar,
  }
}
