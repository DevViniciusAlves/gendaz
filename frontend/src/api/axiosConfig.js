import axios from 'axios'

function normalizarBaseUrl(url) {
  const base = String(url || 'https://api.gendaz.site').trim().replace(/\/+$/, '')
  return base.endsWith('/api') ? base : `${base}/api`
}

const api = axios.create({
  baseURL: normalizarBaseUrl(import.meta.env.VITE_API_URL),
  timeout: 25000,
  withCredentials: true,
})

let sessionUser = null
const SESSION_USER_STORAGE_KEY = 'agendapro_session_user'

function lerUsuarioPersistido() {
  if (typeof window === 'undefined' || !window.sessionStorage) return null
  try {
    const raw = window.sessionStorage.getItem(SESSION_USER_STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function salvarUsuarioPersistido(usuario) {
  if (typeof window === 'undefined' || !window.sessionStorage) return
  try {
    if (usuario) {
      window.sessionStorage.setItem(SESSION_USER_STORAGE_KEY, JSON.stringify(usuario))
    } else {
      window.sessionStorage.removeItem(SESSION_USER_STORAGE_KEY)
    }
  } catch {
    // fallback
  }
}

export function setSessionUser(usuario) {
  sessionUser = usuario || null
  salvarUsuarioPersistido(sessionUser)
}

export function getSessionUser() {
  if (sessionUser) return sessionUser
  const persistido = lerUsuarioPersistido()
  if (persistido) {
    sessionUser = persistido
    return sessionUser
  }
  return null
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response) {
      const isHtml = typeof error.response.data === 'string' &&
        (error.response.data.includes('<!DOCTYPE') || error.response.data.includes('<html') || error.response.data.includes('<body'));
      if (isHtml || error.response.status >= 500) {
        error.response.data = {
          mensagem: 'Serviço temporariamente indisponível. Tente novamente em instantes.',
          message: 'Serviço temporariamente indisponível. Tente novamente em instantes.'
        }
      }
    }

    const status = error.response?.status
    const mensagem = String(error.response?.data?.mensagem || error.response?.data?.message || '').toLowerCase()

    if (mensagem.includes('conta indisponivel')
      || mensagem.includes('conta suspensa')
      || mensagem.includes('suspensa pelo administrador')) {
      window.dispatchEvent(new CustomEvent('agendeasy:account-inactive', { detail: { motivoInatividade: 'ADMIN_SUSPENSAO' } }))
      return Promise.reject(error)
    }
    if (mensagem.includes('conta inativa')
      || mensagem.includes('periodo gratuito terminou')
      || mensagem.includes('mensalidade')) {
      window.dispatchEvent(new CustomEvent('agendeasy:account-inactive', { detail: { motivoInatividade: 'PAGAMENTO_PENDENTE' } }))
      return Promise.reject(error)
    }

    if (status === 401) {
       window.dispatchEvent(new Event('agendeasy:session-expired'))
    }

    return Promise.reject(error)
  },
)

export const modoDemo = import.meta.env.VITE_MODO_DEMO === 'true'
export default api
