import { useEffect, useMemo, useState } from 'react'

function parseData(valor) {
  if (!valor) return null
  const data = valor instanceof Date ? valor : new Date(valor)
  return Number.isNaN(data.getTime()) ? null : data
}

export function useCheckoutTimer(pagamento) {
  const [tempoRestante, setTempoRestante] = useState(null)

  const dataExpiracao = useMemo(() => parseData(pagamento?.dataExpiracao), [pagamento?.dataExpiracao])

  useEffect(() => {
    if (!pagamento?.checkoutUrl || !dataExpiracao) {
      setTempoRestante(null)
      return undefined
    }

    const calcular = () => {
      const restante = dataExpiracao.getTime() - Date.now()
      setTempoRestante(Math.max(0, restante))
      return restante <= 0
    }

    calcular()
    const intervalo = setInterval(() => {
      if (calcular()) {
        clearInterval(intervalo)
      }
    }, 1000)

    return () => clearInterval(intervalo)
  }, [pagamento?.checkoutUrl, dataExpiracao])

  const expirou = tempoRestante !== null && tempoRestante <= 0
  const minutos = tempoRestante !== null ? Math.floor(tempoRestante / 60000) : 0
  const segundos = tempoRestante !== null ? Math.floor((tempoRestante % 60000) / 1000) : 0

  return {
    tempoRestante,
    minutos,
    segundos,
    expirou,
    formatado: `${String(minutos).padStart(2, '0')}:${String(segundos).padStart(2, '0')}`,
  }
}
