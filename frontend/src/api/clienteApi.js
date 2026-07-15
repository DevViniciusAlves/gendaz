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

  if (config.url?.includes('/meu-gendaz/')) {
    try {
      const raw = localStorage.getItem('meu-gendaz-auth')
      
      if (!raw) {
        console.log('⚠️ Nenhum auth encontrado no localStorage para:', config.url)
        return config
      }
      
      const tokenData = JSON.parse(raw)
      
      if (!tokenData?.sessionToken) {
        console.log('⚠️ sessionToken vazio em localStorage')
        return config
      }
      
      config.headers['X-Session-Token'] = tokenData.sessionToken
      
      console.log('✅ Token enviado para:', config.url.split('/').slice(-1)[0])
      console.log('   Token (primeiros 30 caracteres):', tokenData.sessionToken.slice(0, 30) + '...')
      
    } catch (err) {
      console.error('❌ Erro ao adicionar token:', err)
    }
  }
  return config
})

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

    const status = error.response?.status
    const url = error.config?.url || ''

    if (status === 401) {
      console.log('❌ 401 Recebido do backend - token inválido')
      console.log('URL:', url)
      console.log('Headers enviados:', error.config.headers)
      localStorage.removeItem('meu-gendaz-auth')
      console.log('✅ localStorage limpo')
      window.dispatchEvent(new CustomEvent('meu-gendaz:logout'))
      window.location.href = '/meu-gendaz/login'
    }

    if (status === 429) {
      emitirToast('error', 'Muitas requisições. Aguarde um momento e tente novamente.')
    }

    return Promise.reject(error)
  },
)

export default clienteApi
