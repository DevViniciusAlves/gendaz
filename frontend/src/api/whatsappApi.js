import api from './axiosConfig.js'
import { getSessionUser } from './axiosConfig.js'

function usuarioHeaders() {
  const usuario = getSessionUser()
  return usuario?.id ? { 'X-Usuario-Id': usuario.id } : {}
}

// A empresa sempre vem da sessao autenticada no backend (CompanyContext):
// nenhum endpoint aqui envia empresaId.
export async function buscarResumoWhatsapp(options = {}) {
  const { data } = await api.get('/whatsapp/resumo', {
    headers: usuarioHeaders(),
    ...options,
  })
  return data
}

export async function conectarWhatsapp(options = {}) {
  const { data } = await api.post('/whatsapp/conectar', null, {
    headers: usuarioHeaders(),
    ...options,
  })
  return data
}

export async function obterQrWhatsapp(options = {}) {
  const { data } = await api.get('/whatsapp/qr', {
    headers: usuarioHeaders(),
    ...options,
  })
  return data
}

export async function tentarNovamenteWhatsapp(options = {}) {
  const { data } = await api.post('/whatsapp/retry', null, {
    headers: usuarioHeaders(),
    ...options,
  })
  return data
}

export async function desconectarWhatsapp(options = {}) {
  const { data } = await api.post('/whatsapp/desconectar', null, {
    headers: usuarioHeaders(),
    ...options,
  })
  return data
}

export async function atualizarConfiguracaoWhatsapp(payload, options = {}) {
  const body = typeof payload === 'boolean' ? { lembretesAtivos: payload } : payload
  const { data } = await api.patch('/whatsapp/configuracao', body, {
    headers: usuarioHeaders(),
    ...options,
  })
  return data
}
