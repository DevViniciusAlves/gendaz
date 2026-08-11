const CHECKOUT_TTL_MS = 15 * 60 * 1000
const CHECKOUT_START_KEY = 'gendaz_checkout_inicio'

function parseData(data) {
  if (!data) return null
  const valor = data instanceof Date ? data : new Date(data)
  return Number.isNaN(valor.getTime()) ? null : valor
}

function getDataReferencia(pagamento) {
  return parseData(
    pagamento?.checkoutSolicitadoEm
    || pagamento?.createdAt
    || pagamento?.dataCriacao
    || pagamento?.data
    || pagamento?.criadoEm
    || pagamento?.created_at
  )
}

function storageDisponivel() {
  return typeof window !== 'undefined' && window.localStorage
}

function pagamentoKey(pagamento) {
  const valor = pagamento?.id
    || pagamento?.providerPaymentId
    || pagamento?.paymentReference
    || pagamento?.checkoutUrl
  return valor ? String(valor) : null
}

function lerMapaCheckout() {
  if (!storageDisponivel()) return {}
  try {
    return JSON.parse(localStorage.getItem(CHECKOUT_START_KEY) || '{}') || {}
  } catch {
    return {}
  }
}

function salvarMapaCheckout(mapa) {
  if (!storageDisponivel()) return
  localStorage.setItem(CHECKOUT_START_KEY, JSON.stringify(mapa))
}

export function registrarInicioCheckout(pagamento, inicio = new Date().toISOString()) {
  const chave = pagamentoKey(pagamento)
  if (!chave) return null
  const mapa = lerMapaCheckout()
  if (!mapa[chave]) {
    mapa[chave] = inicio
    salvarMapaCheckout(mapa)
  }
  return mapa[chave]
}

export function limparInicioCheckout(pagamento) {
  const chave = pagamentoKey(pagamento)
  if (!chave) return
  const mapa = lerMapaCheckout()
  if (mapa[chave]) {
    delete mapa[chave]
    salvarMapaCheckout(mapa)
  }
}

export function getInicioCheckout(pagamento) {
  const chave = pagamentoKey(pagamento)
  if (!chave) return null
  const mapa = lerMapaCheckout()
  return mapa[chave] || pagamento?.checkoutSolicitadoEm || null
}

export function getInicioCheckoutMs(pagamento) {
  const inicioPersistido = parseData(getInicioCheckout(pagamento))
  if (inicioPersistido) return inicioPersistido.getTime()

  const dataReferencia = getDataReferencia(pagamento)
  return dataReferencia?.getTime() || null
}

export function checkoutExpirado(pagamento, agora = new Date()) {
  if (!pagamento?.checkoutUrl) return true

  const status = String(pagamento?.status || '').toUpperCase()
  if (['PAYMENT_APPROVED', 'PAYMENT_REJECTED', 'PAYMENT_CANCELED', 'PAYMENT_EXPIRED'].includes(status)) {
    return true
  }

  const inicioCheckout = getInicioCheckoutMs(pagamento)
  if (inicioCheckout) {
    return agora.getTime() - inicioCheckout >= CHECKOUT_TTL_MS
  }

  return false
}

export function checkoutAtivo(pagamento, agora = new Date()) {
  return Boolean(pagamento?.checkoutUrl) && !checkoutExpirado(pagamento, agora)
}

export function checkoutPodeGerarNovo(pagamento, agora = new Date()) {
  return !checkoutAtivo(pagamento, agora)
}

