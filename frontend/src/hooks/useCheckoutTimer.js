import { useEffect, useRef, useState } from 'react'
import { getDataExpiracao } from '../utils/checkoutUtils.js'

export function useCheckoutTimer(pagamento) {
  const [tempoRestante, setTempoRestante] = useState(null)
  const [expirou, setExpirou] = useState(false)
  const pagamentoRef = useRef(pagamento)
  pagamentoRef.current = pagamento

  useEffect(() => {
    const calcularTempo = () => {
      const atual = pagamentoRef.current
      if (!atual?.checkoutUrl) {
        setTempoRestante(null)
        setExpirou(true)
        return
      }

      const dataExpiracao = getDataExpiracao(atual)
      if (!dataExpiracao) {
        setTempoRestante(null)
        setExpirou(true)
        return
      }

      const resto = dataExpiracao.getTime() - Date.now()
      if (resto <= 0) {
        setTempoRestante(0)
        setExpirou(true)
        return
      }

      setTempoRestante(resto)
      setExpirou(false)
    }

    calcularTempo()

    const intervalo = setInterval(calcularTempo, 1000)
    const aoFocar = () => calcularTempo()

    window.addEventListener('focus', aoFocar)
    document.addEventListener('visibilitychange', aoFocar)

    return () => {
      clearInterval(intervalo)
      window.removeEventListener('focus', aoFocar)
      document.removeEventListener('visibilitychange', aoFocar)
    }
  }, [])

  const minutos = tempoRestante != null ? Math.max(0, Math.floor(tempoRestante / 1000 / 60)) : 0
  const segundos = tempoRestante != null ? Math.max(0, Math.floor((tempoRestante / 1000) % 60)) : 0

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
