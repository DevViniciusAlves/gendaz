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

function ehMeuGendazPath() {
  if (typeof window === 'undefined') return false
  return window.location.pathname.startsWith('/meu-gendaz/')
}

/**
 * Detecta quando a sessão do painel Gendaz foi substituída por um novo login
 * (mesma conta acessada em outra aba/navegador) e avisa o callback.
 *
 * Regras para evitar falsos positivos:
 * - Nunca confia em valor cacheado do sessionStorage: lê sempre ao vivo,
 *   assim um novo login na mesma aba não derruba a sessão logo após entrar.
 * - Não roda em /meu-gendaz, que usa sessão própria (cookie separado).
 */
export function useSessionCheck(onInvalidated, { enabled = true } = {}) {
  const callbackRef = useRef(onInvalidated)
  callbackRef.current = onInvalidated

  useEffect(() => {
    if (!enabled || typeof window === 'undefined') return undefined

    function registrarSessaoAtual() {
      if (!lerSessionStorage()) {
        const global = lerLocalStorage()
        if (global) {
          try {
            window.sessionStorage.setItem(SESSION_ID_KEY, global)
          } catch {
            // armazenamento indisponivel
          }
        }
      }
    }

    function detectarInvalidacao() {
      if (ehMeuGendazPath()) return
      const minha = lerSessionStorage()
      const global = lerLocalStorage()
      if (minha && global && minha !== global) {
        callbackRef.current()
      }
    }

    function lidarStorage(event) {
      if (ehMeuGendazPath()) return
      if (event.key !== SESSION_ID_KEY || !event.newValue) return
      const minha = lerSessionStorage()
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
  }, [enabled])
}
