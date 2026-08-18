import { useEffect, useState } from 'react'
import { getDataExpiracao } from '../utils/checkoutUtils.js'

export function useCheckoutTimer(pagamento) {
  const [tempoRestante, setTempoRestante] = useState(null)
  const [expirou, setExpirou] = useState(false)

  useEffect(() => {
    if (!pagamento?.checkoutUrl) {
      setExpirou(true)
      setTempoRestante(null)
      return
    }

    const dataExpiracao = getDataExpiracao(pagamento)
    if (!dataExpiracao) {
      setExpirou(true)
      setTempoRestante(null)
      return
    }

    const calcularTempo = () => {
      const agora = Date.now()
      const resto = dataExpiracao.getTime() - agora

      if (resto <= 0) {
        setTempoRestante(0)
        setExpirou(true)
        return true
      }

      setTempoRestante(resto)
      setExpirou(false)
      return false
    }

    calcularTempo()

    const intervalo = setInterval(() => {
      const expirado = calcularTempo()
      if (expirado) clearInterval(intervalo)
    }, 1000)

    return () => clearInterval(intervalo)
  }, [pagamento])

  const minutos = tempoRestante ? Math.max(0, Math.floor(tempoRestante / 1000 / 60)) : 0
  const segundos = tempoRestante ? Math.max(0, Math.floor((tempoRestante / 1000) % 60)) : 0

  return {
    tempoRestante,
    minutos,
    segundos,
    expirou,
    formatado: tempoRestante !== null
      ? `${String(minutos).padStart(2, '0')}:${String(segundos).padStart(2, '0')}`
      : '--:--',
  }
}
