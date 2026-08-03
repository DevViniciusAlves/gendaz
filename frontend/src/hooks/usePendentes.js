import { useCallback, useEffect, useState } from 'react'
import { appApi } from '../api/appApi.js'

export function usePendentes() {
  const [pendentes, setPendentes] = useState(0)

  const carregarPendentes = useCallback(async () => {
    try {
      const count = await appApi.contarPagamentosPendentes()
      setPendentes(count)
    } catch (error) {
      console.error('Erro ao carregar pagamentos pendentes')
    }
  }, [])

  useEffect(() => {
    carregarPendentes()
    const interval = setInterval(carregarPendentes, 30000)
    return () => clearInterval(interval)
  }, [carregarPendentes])

  return {
    pendentes,
    contagemPendentes: pendentes,
    atualizarContagem: carregarPendentes,
  }
}
