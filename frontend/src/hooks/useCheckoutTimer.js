import { useEffect, useState } from 'react'

const LIMITE_CHECKOUT_MS = 15 * 60 * 1000

function parseData(valor) {
  if (!valor) return null
  const data = valor instanceof Date ? valor : new Date(valor)
  return Number.isNaN(data.getTime()) ? null : data
}

function normalizarInicioCheckout(pagamento, agoraMs = Date.now()) {
  const inicioLocal = pagamento?.checkoutSolicitadoEm
  if (inicioLocal) {
    const dataInicio = parseData(inicioLocal)
    if (dataInicio) return Math.min(dataInicio.getTime(), agoraMs)
  }

  const dataCriacao = parseData(pagamento?.dataCriacao)
  return dataCriacao ? Math.min(dataCriacao.getTime(), agoraMs) : null
}

export function useCheckoutTimer(pagamento) {
  const [tempoRestante, setTempoRestante] = useState(null)
  const [expirou, setExpirou] = useState(false)

  useEffect(() => {
    if (!pagamento?.checkoutUrl) {
      setExpirou(true)
      setTempoRestante(null)
      return
    }

    const inicioCheckout = normalizarInicioCheckout(pagamento)
    if (!inicioCheckout) {
      setExpirou(true)
      setTempoRestante(null)
      return
    }

    const expiracaoCalculada = inicioCheckout + LIMITE_CHECKOUT_MS

    const calcularTempo = () => {
      const agora = Date.now()
      const resto = expiracaoCalculada - agora

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
  }, [pagamento?.checkoutUrl, pagamento?.dataCriacao, pagamento?.checkoutSolicitadoEm])

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
