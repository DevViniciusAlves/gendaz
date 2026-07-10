import axios from 'axios'

const API_BASE = import.meta.env.VITE_API_URL
  ? (import.meta.env.VITE_API_URL.endsWith('/api') ? import.meta.env.VITE_API_URL : `${import.meta.env.VITE_API_URL}/api`)
  : 'https://api.gendaz.site/api'

const clienteApi = axios.create({
  baseURL: API_BASE,
  timeout: 25000,
  withCredentials: true,
})

const MAX_REQUESTS = 60
let requestCount = 0
let resetTime = Date.now() + 60000
let isRefreshing = false
let failedQueue = []

function processQueue(error, token = null) {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error)
    else resolve(token)
  })
  failedQueue = []
}

function emitirToast(type, message) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('agendapro:toast', {
    detail: { type, message },
  }))
}

clienteApi.interceptors.request.use((config) => {
  const now = Date.now()
  if (now > resetTime) {
    requestCount = 0
    resetTime = now + 60000
  }
  if (requestCount >= MAX_REQUESTS) {
    emitirToast('error', 'Limite de requisições atingido. Aguarde um momento.')
    return Promise.reject(new Error('RATE_LIMIT_EXCEEDED'))
  }
  requestCount++

  const token = localStorage.getItem('clienteToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

async function tentarRefreshToken(config) {
  const refreshToken = localStorage.getItem('clienteRefreshToken')
  const url = String(config?.url || '')
  if (!refreshToken || config?._retry || url.includes('/clientes/auth/')) {
    return false
  }
  config._retry = true
  try {
    const { data } = await axios.post(`${API_BASE}/clientes/auth/refresh`, {
      refreshToken,
    })
    localStorage.setItem('clienteToken', data.token)
    localStorage.setItem('clienteRefreshToken', data.refreshToken)
    return true
  } catch {
    localStorage.removeItem('clienteToken')
    localStorage.removeItem('clienteRefreshToken')
    window.location.href = '/gendaz/login'
    return false
  }
}

clienteApi.interceptors.response.use(
  (response) => response,
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

    const originalRequest = error.config || {}
    const status = error.response?.status

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      const refreshToken = localStorage.getItem('clienteRefreshToken')
      if (refreshToken) {
        try {
          const { data } = await axios.post(`${API_BASE}/clientes/auth/refresh`, {
            refreshToken,
          })

          localStorage.setItem('clienteToken', data.token)
          localStorage.setItem('clienteRefreshToken', data.refreshToken)

          originalRequest.headers.Authorization = `Bearer ${data.token}`
          return clienteApi(originalRequest)
        } catch (err) {
          localStorage.removeItem('clienteToken')
          localStorage.removeItem('clienteRefreshToken')
          window.location.href = '/gendaz/login'
        }
      }
    }

    if (status === 429) {
      emitirToast('error', 'Muitas requisições. Aguarde um momento e tente novamente.')
    }

    return Promise.reject(error)
  },
)

export default clienteApi
