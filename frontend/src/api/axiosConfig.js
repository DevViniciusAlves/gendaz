import axios from 'axios'

function normalizarBaseUrl(url) {
  const base = String(url || 'https://api.gendaz.site').trim().replace(/\/+$/, '')
  return base.endsWith('/api') ? base : `${base}/api`
}

const api = axios.create({
  baseURL: normalizarBaseUrl(import.meta.env.VITE_API_URL),
  timeout: 25000,
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

let sessionUser = null

export function setSessionUser(usuario) {
  sessionUser = usuario || null
}

export function getSessionUser() {
  return sessionUser
}

export async function garantirCsrfCookie() {
  if (typeof window === 'undefined') return
  await api.get('/health', { skipUsuarioHeader: true })
}

api.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const metodo = String(config.method || 'get').toLowerCase()
    const precisaCsrf = ['post', 'put', 'patch', 'delete'].includes(metodo)
    if (precisaCsrf) {
      const cookies = document.cookie ? document.cookie.split('; ') : []
      const xsrf = cookies
        .map((item) => item.split('='))
        .find(([nome]) => nome === 'XSRF-TOKEN')
      if (xsrf?.[1]) {
        config.headers = config.headers || {}
        config.headers['X-XSRF-TOKEN'] = decodeURIComponent(xsrf[1])
      }
    }
  }
  return config
})

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

    const url = String(error.config?.url || '')
    if (status === 401 && url.includes('/auth/refresh')) {
      window.dispatchEvent(new Event('agendeasy:session-expired'))
    }

    return Promise.reject(error)
  },
)

export const modoDemo = import.meta.env.VITE_MODO_DEMO === 'true'
export default api
