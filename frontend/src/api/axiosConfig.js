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

function processQueue(error, token = null) {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error)
    else resolve(token)
  })
  failedQueue = []
}

api.interceptors.request.use((config) => {
  if (config?.skipUsuarioHeader) {
    return config
  }
  const usuario = JSON.parse(localStorage.getItem('agendapro_usuario') || 'null')
  if (usuario?.id) config.headers['X-Usuario-Id'] = usuario.id
  if (usuario?.perfil) config.headers['X-Usuario-Perfil'] = usuario.perfil
  return config
})

async function tentarRefreshSessao(config) {
  const usuario = JSON.parse(localStorage.getItem('agendapro_usuario') || 'null')
  const url = String(config?.url || '')
  if (!usuario?.id || config?._retry || url.includes('/auth/login') || url.includes('/auth/refresh') || url.includes('/auth/logout')) {
    return false
  }
  config._retry = true
  try {
    console.log('[auth-debug] iniciando refresh token', { url })
    await api.post('/auth/refresh', null, {
      headers: {
        'X-Usuario-Id': usuario.id,
        'X-Usuario-Perfil': usuario.perfil || '',
      },
    })
    console.log('[auth-debug] refresh token OK', { url })
    return true
  } catch (error) {
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

    if (originalRequest?.skipUsuarioHeader) {
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
