import axios from 'axios'

const API_BASE = import.meta.env.VITE_API_URL
  ? (import.meta.env.VITE_API_URL.endsWith('/api') ? import.meta.env.VITE_API_URL : `${import.meta.env.VITE_API_URL}/api`)
  : 'https://api.gendaz.site/api'

const clienteApi = axios.create({
  baseURL: API_BASE,
  timeout: 25000,
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

const MAX_REQUESTS = 60
let requestCount = 0
let resetTime = Date.now() + 60000
let csrfToken = null
let csrfPromise = null

function ehAuthPublicoMeuGendaz(config) {
  const url = String(config?.url || '')
  return url.includes('/meu-gendaz/auth/solicitar-codigo')
    || url.includes('/meu-gendaz/auth/validar-codigo')
}

function precisaCsrf(config) {
  const metodo = String(config.method || 'get').toLowerCase()
  if (!['post', 'put', 'patch', 'delete'].includes(metodo)) return false
  return !ehAuthPublicoMeuGendaz(config)
}

async function garantirCsrfToken() {
  if (csrfToken) return csrfToken

  if (!csrfPromise) {
    csrfPromise = axios.get(`${API_BASE}/auth/csrf`, {
      timeout: 10000,
      withCredentials: true,
    }).then((response) => {
      csrfToken = response.data?.token || null
      return csrfToken
    }).finally(() => {
      csrfPromise = null
    })
  }
  return csrfPromise
}

function emitirToast(type, message) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('gendaz:toast', {
    detail: { type, message },
  }))
}

clienteApi.interceptors.request.use(async (config) => {
  const now = Date.now()
  if (now > resetTime) {
    requestCount = 0
    resetTime = now + 60000
  }
  if (requestCount >= MAX_REQUESTS) {
    emitirToast('warning', 'Sistema está carregando. Aguarde um momento.')
    return Promise.reject(new Error('RATE_LIMIT_EXCEEDED'))
  }
  requestCount++

  if (precisaCsrf(config)) {
    const token = await garantirCsrfToken()
    if (token) {
      config.headers = config.headers || {}
      config.headers['X-XSRF-TOKEN'] = token
    }
  }

  return config
})

function emitirDadoAlterado(config) {
  if (typeof window === 'undefined') return
  const metodo = String(config?.method || 'get').toLowerCase()
  if (!['post', 'put', 'patch', 'delete'].includes(metodo)) return
  if (config?.skipDataChanged) return
  const url = String(config?.url || '')
  // OTP/logout possuem fluxo proprio (meu-gendaz:logout); IA e conversa sem escrita.
  if (url.includes('/meu-gendaz/auth/') || url.includes('/meu-gendaz/ia')) return
  window.dispatchEvent(new Event('gendaz:data-changed'))
}

clienteApi.interceptors.response.use(
  (response) => {
    emitirDadoAlterado(response?.config)
    return response
  },
  async (error) => {
    if (error.response) {
      const isHtml = typeof error.response.data === 'string' &&
        (error.response.data.includes('<!DOCTYPE') || error.response.data.includes('<html'))
      if (isHtml) {
        const status = error.response.status
        if (status === 404) {
          error.response.data = { mensagem: 'Recurso não encontrado no servidor.' }
        } else if (status >= 500) {
          error.response.data = { mensagem: 'Erro interno do servidor. Tente novamente em instantes.' }
        } else {
          error.response.data = { mensagem: `Erro ${status} ao comunicar com o servidor.` }
        }
      }
    }

    const status = error.response?.status
    const skipLogout = error.config?.skipMeuGendazLogout === true
    const authPublicoMeuGendaz = ehAuthPublicoMeuGendaz(error.config)
    if (status === 403 && precisaCsrf(error.config || {})) {
      csrfToken = null
    }
    if (status === 401 && !skipLogout && !authPublicoMeuGendaz) {
      window.dispatchEvent(new CustomEvent('meu-gendaz:logout'))
    }

    if (status === 429) {
      emitirToast('warning', 'Sistema está carregando. Aguarde um momento.')
    }

    return Promise.reject(error)
  },
)

export default clienteApi

