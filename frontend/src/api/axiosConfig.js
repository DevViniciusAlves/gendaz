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

let isRefreshing = false
let failedQueue = []
let sessionUser = null

export function setSessionUser(usuario) {
  sessionUser = usuario || null
}

export function getSessionUser() {
  return sessionUser
}

function processQueue(error, token = null) {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error)
    else resolve(token)
  })
  failedQueue = []
}

function isMeuGendazPath() {
  if (typeof window === 'undefined') return false
  return window.location.pathname.startsWith('/meu-gendaz/')
}

api.interceptors.request.use((config) => {
  if (config?.skipUsuarioHeader) {
    return config
  }
  const usuario = sessionUser
  if (usuario?.id) config.headers['X-Usuario-Id'] = usuario.id
  if (usuario?.perfil) config.headers['X-Usuario-Perfil'] = usuario.perfil
  return config
})

async function tentarRefreshSessao(config) {
  if (isMeuGendazPath()) {
    return false
  }
  const usuario = sessionUser
  const url = String(config?.url || '')
  if (!usuario?.id || config?._retry || url.includes('/auth/login') || url.includes('/auth/refresh') || url.includes('/auth/logout')) {
    return false
  }
  config._retry = true
  try {
    console.log('[auth-debug] iniciando refresh token', { url })
    await api.post('/auth/refresh', null, {
      skipRefreshRetry: true,
      headers: {
        'X-Usuario-Id': usuario.id,
        'X-Usuario-Perfil': usuario.perfil || '',
      },
    })
    console.log('[auth-debug] refresh token OK', { url })
    return true
  } catch (error) {
    const mensagem = String(error.response?.data?.mensagem || error.response?.data?.message || '').toLowerCase()
    if (error.response?.status === 401 && (
      mensagem.includes('outro dispositivo')
      || mensagem.includes('acessada em outro dispositivo')
      || mensagem.includes('acesso em outro dispositivo')
    )) {
      console.warn('[auth-debug] refresh com outro dispositivo, mantendo sessao local', {
        url,
        status: error.response?.status,
        data: error.response?.data,
        message: error.message,
      })
      window.dispatchEvent(new CustomEvent('agendapro:toast', {
        detail: {
          type: 'warning',
          message: 'Sua conta foi acessada em outro dispositivo, mas esta sessão continua ativa.',
        },
      }))
      return true
    }
    if (error.response?.status === 401) {
      window.dispatchEvent(new Event('agendeasy:session-expired'))
    }
    console.warn('[auth-debug] refresh token falhou', {
      url,
      status: error.response?.status,
      data: error.response?.data,
      message: error.message,
    })
    return false
  }
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
    const originalRequest = error.config || {}
    const status = error.response?.status
    const mensagem = String(error.response?.data?.mensagem || error.response?.data?.message || '').toLowerCase()
    const url = String(originalRequest.url || '')

    if (mensagem.includes('conta indisponivel')
      || mensagem.includes('conta inativa')
      || mensagem.includes('periodo gratuito terminou')
      || mensagem.includes('mensalidade')) {
      window.dispatchEvent(new Event('agendeasy:account-inactive'))
      return Promise.reject(error)
    }

    if (status !== 401) {
      return Promise.reject(error)
    }

    if (isMeuGendazPath()) {
      return Promise.reject(error)
    }

    if (originalRequest?.skipUsuarioHeader) {
      return Promise.reject(error)
    }

    if (originalRequest?.skipRefreshRetry) {
      window.dispatchEvent(new Event('agendeasy:session-expired'))
      return Promise.reject(error)
    }

    console.log('[auth-debug] 401 recebido', {
      url,
      method: originalRequest.method,
      vaiTentarRefresh: !originalRequest._retry,
    })

    if (url.includes('/auth/login') || url.includes('/auth/refresh') || url.includes('/auth/logout')) {
      return Promise.reject(error)
    }

    if (originalRequest._retry) {
      return Promise.reject(error)
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({
          resolve: () => resolve(api.request(originalRequest)),
          reject,
        })
      })
    }

    isRefreshing = true
    try {
      if (await tentarRefreshSessao(originalRequest)) {
        processQueue(null)
        console.log('[auth-debug] repetindo request original', {
          url,
          method: originalRequest.method,
        })
        return api.request(originalRequest)
      }
      processQueue(error)
    } finally {
      isRefreshing = false
    }

    return Promise.reject(error)
  },
)

export const modoDemo = import.meta.env.VITE_MODO_DEMO === 'true'
export default api
