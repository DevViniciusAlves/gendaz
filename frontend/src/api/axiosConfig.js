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
let csrfToken = null

export function setSessionUser(usuario) {
  sessionUser = usuario || null
}

export function getSessionUser() {
  return sessionUser
}

export function setCsrfToken(token) {
  csrfToken = token || null
}

export async function garantirCsrfCookie() {
  if (typeof window === 'undefined') return
  const response = await api.get('/auth/csrf', { skipUsuarioHeader: true })
  setCsrfToken(response.data?.token || null)
}

api.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const metodo = String(config.method || 'get').toLowerCase()
    const precisaCsrf = ['post', 'put', 'patch', 'delete'].includes(metodo)
    if (precisaCsrf && csrfToken) {
      config.headers = config.headers || {}
      config.headers['X-XSRF-TOKEN'] = csrfToken
    }
  }
  return config
})

api.interceptors.response.use(
  (response) => {
    notificarDadoAlterado(response?.config)
    return response
  },
  async (error) => {
    const original = error.config
    const codigoStatus = error.response?.status
    const metodo = String(original?.method || 'get').toLowerCase()
    const precisaCsrf = ['post', 'put', 'patch', 'delete'].includes(metodo)
    const urlReq = String(original?.url || '')
    const ehApi = urlReq.includes('/api/') || urlReq.startsWith('/api/')

    if (codigoStatus === 403 && precisaCsrf && ehApi && !original._csrfRetry) {
      original._csrfRetry = true
      try {
        await garantirCsrfCookie()
        return api(original)
      } catch (retryError) {
        // Mantém o erro 403 original se o refresh de CSRF não resolver
      }
    }

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
    const isPublicAuth = url.includes('/auth/login')
      || url.includes('/auth/criar-conta')
      || url.includes('/auth/recuperar-senha')
      || url.includes('/auth/redefinir-senha')
      || url.includes('/auth/csrf')
      || url.includes('/usuarios/convites/')
    const isMeuGendaz = url.includes('/meu-gendaz/')
    const isAdmin = url.includes('/admin/')
    if (status === 401 && (url.includes('/auth/refresh') || (!isPublicAuth && !isMeuGendaz && !isAdmin))) {
      window.dispatchEvent(new Event('agendeasy:session-expired'))
    }

    return Promise.reject(error)
  },
)

export const modoDemo = import.meta.env.VITE_MODO_DEMO === 'true'

/**
 * Produtor central do evento de sincronizacao global.
 * Toda mutation de negocio bem-sucedida (POST/PUT/PATCH/DELETE) avisa os
 * hooks (useLocalData), que invalidam o cache do escopo e recarregam do
 * backend. Leitura (GET) nunca dispara — logo nao ha loop de reload.
 */
function notificarDadoAlterado(config) {
  if (typeof window === 'undefined') return
  const metodo = String(config?.method || 'get').toLowerCase()
  if (!['post', 'put', 'patch', 'delete'].includes(metodo)) return
  if (config?.skipDataChanged) return
  const url = String(config?.url || '')
  // Auth/conta possuem fluxo proprio de sessao (session-changed).
  if (url.includes('/auth/') || url.includes('/usuarios/convites/') || url.includes('/meu-gendaz/auth/')) return
  // Consulta de status do checkout via POST (polling) nao e mutation de negocio.
  if (url.includes('/public/pagamentos/stripe/checkout/status')) return
  window.dispatchEvent(new Event('gendaz:data-changed'))
}
export default api
