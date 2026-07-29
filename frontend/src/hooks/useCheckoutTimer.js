import { useEffect, useState } from 'react'

const LIMITE_CHECKOUT_MS = 15 * 60 * 1000

function parseData(valor) {
  if (!valor) return null
  const data = valor instanceof Date ? valor : new Date(valor)
  return Number.isNaN(data.getTime()) ? null : data
}

export function useCheckoutTimer(pagamento) {
  const [tempoRestante, setTempoRestante] = useState(null)
  const [expirou, setExpirou] = useState(false)

  useEffect(() => {
    const dataExpiracao = parseData(pagamento?.dataExpiracao)
    const dataCriacao = parseData(pagamento?.dataCriacao)

    if (!pagamento?.checkoutUrl) {
      setExpirou(true)
      setTempoRestante(null)
      return
    }

    let expiracaoCalculada = dataExpiracao?.getTime() || null
    const criacaoCalculada = dataCriacao?.getTime() || null

    if (criacaoCalculada) {
      const expiracaoMaxima = criacaoCalculada + LIMITE_CHECKOUT_MS
      if (!expiracaoCalculada || expiracaoCalculada > expiracaoMaxima) {
        expiracaoCalculada = expiracaoMaxima
      }
    }

    if (!expiracaoCalculada) {
      setExpirou(true)
      setTempoRestante(null)
      return
    }

    const calcularTempo = () => {
      const agora = new Date().getTime()
      const expiracao = expiracaoCalculada
      const resto = expiracao - agora

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
      if (expirado) {
        clearInterval(intervalo)
      }
    }, 1000)

    return () => clearInterval(intervalo)
  }, [pagamento?.checkoutUrl, pagamento?.dataExpiracao, pagamento?.dataCriacao])

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
