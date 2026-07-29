const CHECKOUT_TTL_MS = 15 * 60 * 1000

function parseData(data) {
  if (!data) return null
  const valor = data instanceof Date ? data : new Date(data)
  return Number.isNaN(valor.getTime()) ? null : valor
}

function getDataReferencia(pagamento) {
  return parseData(
    pagamento?.dataExpiracao
    || pagamento?.createdAt
    || pagamento?.dataCriacao
    || pagamento?.data
    || pagamento?.criadoEm
    || pagamento?.created_at
  )
}

export function checkoutExpirado(pagamento, agora = new Date()) {
  if (!pagamento?.checkoutUrl) return true

  const status = String(pagamento?.status || '').toUpperCase()
  if (['PAYMENT_APPROVED', 'PAYMENT_REJECTED', 'PAYMENT_CANCELED', 'PAYMENT_EXPIRED'].includes(status)) {
    return true
  }

  const dataExpiracao = parseData(pagamento?.dataExpiracao)
  if (dataExpiracao) return dataExpiracao.getTime() <= agora.getTime()

  const dataReferencia = getDataReferencia(pagamento)
  if (dataReferencia) {
    return agora.getTime() - dataReferencia.getTime() >= CHECKOUT_TTL_MS
  }

  return false
}

export function checkoutAtivo(pagamento, agora = new Date()) {
  return Boolean(pagamento?.checkoutUrl) && !checkoutExpirado(pagamento, agora)
}

export function checkoutPodeGerarNovo(pagamento, agora = new Date()) {
  return !checkoutAtivo(pagamento, agora)
}
