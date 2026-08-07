import { useEffect, useRef } from 'react'

const SESSION_ID_KEY = 'agendapro_session_id'

function lerLocalStorage() {
  if (typeof window === 'undefined') return null
  try {
    return window.localStorage.getItem(SESSION_ID_KEY)
  } catch {
    return null
  }
}

function lerSessionStorage() {
  if (typeof window === 'undefined') return null
  try {
    return window.sessionStorage.getItem(SESSION_ID_KEY)
  } catch {
    return null
  }
}

export function useSessionCheck(onInvalidated) {
  const callbackRef = useRef(onInvalidated)
  callbackRef.current = onInvalidated
  const sessaoIdTabRef = useRef(null)

  useEffect(() => {
    if (typeof window === 'undefined') return undefined

    function registrarSessaoAtual() {
      const minha = sessaoIdTabRef.current || lerSessionStorage()
      if (minha) {
        sessaoIdTabRef.current = minha
        return
      }
      const global = lerLocalStorage()
      if (global) {
        try {
          window.sessionStorage.setItem(SESSION_ID_KEY, global)
        } catch {
          // armazenamento indisponivel
        }
        sessaoIdTabRef.current = global
      }
    }

    function detectarInvalidacao() {
      const minha = sessaoIdTabRef.current || lerSessionStorage()
      const global = lerLocalStorage()
      if (minha && global && minha !== global) {
        callbackRef.current()
      }
    }

    function lidarStorage(event) {
      if (event.key !== SESSION_ID_KEY || !event.newValue) return
      const minha = sessaoIdTabRef.current || lerSessionStorage()
      if (minha && minha !== event.newValue) {
        callbackRef.current()
      }
    }

    function lidarFocus() {
      detectarInvalidacao()
    }

    function lidarVisibilidade() {
      if (document.visibilityState === 'visible') detectarInvalidacao()
    }

    registrarSessaoAtual()
    detectarInvalidacao()

    window.addEventListener('storage', lidarStorage)
    window.addEventListener('focus', lidarFocus)
    document.addEventListener('visibilitychange', lidarVisibilidade)

    return () => {
      window.removeEventListener('storage', lidarStorage)
      window.removeEventListener('focus', lidarFocus)
      document.removeEventListener('visibilitychange', lidarVisibilidade)
    }
  }, [])
}
