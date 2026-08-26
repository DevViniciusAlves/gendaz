import { useEffect, useRef } from 'react'

function ehMeuGendazPath() {
  if (typeof window === 'undefined') return false
  return window.location.pathname.startsWith('/meu-gendaz/')
}

function urlWebSocket() {
  if (typeof window === 'undefined') return null
  const base = String(import.meta.env.VITE_API_URL || 'https://api.gendaz.site')
    .trim()
    .replace(/\/+$/, '')
    .replace(/\/api$/, '')
  const protocol = base.startsWith('https://') ? 'wss' : 'ws'
  const host = base.replace(/^https?:\/\//, '')
  return `${protocol}://${host}/ws/session`
}

/**
 * Mantém uma conexão WebSocket com o backend para receber a invalidação de
 * sessão quando a mesma conta entra em outro navegador.
 *
 * - O callback é guardado em ref, então a conexão NÃO é recriada a cada render.
 * - Não conecta em /meu-gendaz (sessão própria) e reconecta com backoff.
 */
export function useSessionWebSocket(onInvalidated, { enabled = true } = {}) {
  const callbackRef = useRef(onInvalidated)
  callbackRef.current = onInvalidated
  const wsRef = useRef(null)

  useEffect(() => {
    if (!enabled || ehMeuGendazPath()) return undefined

    let fechadoManualmente = false
    let temporizador = null

    function conectar() {
      if (fechadoManualmente || ehMeuGendazPath()) return
      const url = urlWebSocket()
      if (!url) return

      const socket = new WebSocket(url)

      socket.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          if (data.type === 'SESSION_INVALIDATED') {
            callbackRef.current()
          }
        } catch {
          // mensagem invalida ignorada
        }
      }

      socket.onclose = () => {
        wsRef.current = null
        if (!fechadoManualmente) {
          temporizador = setTimeout(conectar, 5000)
        }
      }

      socket.onerror = () => {
        socket.close()
      }

      wsRef.current = socket
    }

    conectar()

    return () => {
      fechadoManualmente = true
      if (temporizador) clearTimeout(temporizador)
      if (wsRef.current) {
        wsRef.current.onclose = null
        wsRef.current.close()
        wsRef.current = null
      }
    }
  }, [enabled])
}
