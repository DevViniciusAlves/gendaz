function parseData(data) {
  if (data == null) return null
  const valor = data instanceof Date ? data : new Date(data)
  return Number.isNaN(valor.getTime()) ? null : valor
}

export function getDataExpiracao(pagamento) {
  if (pagamento?.dataExpiracaoEpoch != null) {
    return parseData(Number(pagamento.dataExpiracaoEpoch))
  }
  return parseData(pagamento?.dataExpiracao)
}

export function checkoutExpirado(pagamento, agora = new Date()) {
  if (!pagamento?.checkoutUrl) return true

  const status = String(pagamento?.status || '').toUpperCase()
  if (['PAYMENT_APPROVED', 'PAYMENT_REJECTED', 'PAYMENT_CANCELED', 'PAYMENT_EXPIRED'].includes(status)) {
    return true
  }

  const dataExpiracao = getDataExpiracao(pagamento)
  if (dataExpiracao) {
    return agora.getTime() >= dataExpiracao.getTime()
  }

  // Se não tem deadline confiável, considera expirado por segurança
  return true
}

export function checkoutAtivo(pagamento, agora = new Date()) {
  return Boolean(pagamento?.checkoutUrl) && !checkoutExpirado(pagamento, agora)
}

export function checkoutPodeGerarNovo(pagamento, agora = new Date()) {
  return !checkoutAtivo(pagamento, agora)
}
