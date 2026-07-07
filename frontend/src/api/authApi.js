import api from './axiosConfig.js'

export const authApi = {
  login: (email, senha) => api.post('/auth/login', { email, senha }).then((response) => response.data),
  criarConta: (payload) => api.post('/auth/criar-conta', payload).then((response) => response.data),
}
