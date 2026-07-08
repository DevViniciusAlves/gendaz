import { useState, useEffect, useCallback } from 'react'
import { appApi } from '../api/appApi.js'

export function usePagamentosPendentes() {
  const [contagemPendentes, setContagemPendentes] = useState(0)

  const carregarContagemPendentes = useCallback(async () => {
    try {
      const count = await appApi.contarPagamentosPendentes()
      setContagemPendentes(count)
    } catch (error) {
      console.error('Erro ao carregar contagem de pendentes:', error)
    }
  }, [])

  useEffect(() => {
    carregarContagemPendentes()
    const intervalo = setInterval(carregarContagemPendentes, 30000)
    return () => clearInterval(intervalo)
  }, [carregarContagemPendentes])

  const atualizarContagem = () => {
    carregarContagemPendentes()
  }

  return { contagemPendentes, atualizarContagem }
}
