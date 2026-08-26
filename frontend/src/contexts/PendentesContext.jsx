import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { appApi } from '../api/appApi.js'

const PendentesContext = createContext({
  pendentes: 0,
  contagemPendentes: 0,
  atualizarContagem: () => {},
})

export function PendentesProvider({ children }) {
  const [pendentes, setPendentes] = useState(0)

  const carregarPendentes = useCallback(async () => {
    try {
      const count = await appApi.contarPagamentosPendentes()
      setPendentes(count)
    } catch (error) {
      console.error('Erro ao carregar pagamentos pendentes')
    }
  }, [])

  // Polling único e global do contador de pendências: o provider fica montado
  // no painel (AppLayout), então Sidebar e Financeiro compartilham o mesmo
  // estado e o mesmo intervalo, sem duplicação de requisições.
  useEffect(() => {
    carregarPendentes()
    const interval = setInterval(carregarPendentes, 30000)
    return () => clearInterval(interval)
  }, [carregarPendentes])

  return (
    <PendentesContext.Provider value={{ pendentes, contagemPendentes: pendentes, atualizarContagem: carregarPendentes }}>
      {children}
    </PendentesContext.Provider>
  )
}

export function usePendentes() {
  return useContext(PendentesContext)
}
