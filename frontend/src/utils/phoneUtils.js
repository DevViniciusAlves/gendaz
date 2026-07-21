export function somenteNumeros(valor) {
  return String(valor || '').replace(/\D/g, '')
}

export function aplicarMascara(telefone) {
  if (!telefone) return ''
  const digitos = somenteNumeros(telefone).slice(0, 14)
  if (digitos.length === 0) return ''
  if (digitos.length <= 2) return digitos

  const codigoCidade = digitos.slice(0, 2)
  const numero = digitos.slice(2)

  if (numero.length <= 4) return `(${codigoCidade}) ${numero}`
  if (numero.length <= 8) return `(${codigoCidade}) ${numero.slice(0, -4)}-${numero.slice(-4)}`

  const prefixo = numero.slice(0, -8)
  const meio = numero.slice(-8, -4)
  const fim = numero.slice(-4)
  return `(${codigoCidade}) ${prefixo} ${meio}-${fim}`
}

export function validarTelefone(telefone) {
  if (!telefone) return 'Telefone e obrigatorio'
  const digitos = somenteNumeros(telefone)
  if (digitos.length === 0) return 'Telefone e obrigatorio'
  const formatado = aplicarMascara(telefone)
  if (formatado.length < 16) {
    return `Incompleto: ${formatado.length}/16 caracteres. Use codigo da cidade + numero.`
  }
  if (formatado.length > 19) return 'Telefone muito longo. Maximo de 19 caracteres.'
  return ''
}

export function padronizarTelefone(entrada) {
  const digitos = somenteNumeros(entrada)
  if (!digitos) return null

  const formatado = aplicarMascara(digitos)
  if (formatado.length < 16 || formatado.length > 19) return null

  return digitos
}

export function exibirTelefone(numero) {
  if (!numero) return ''
  return aplicarMascara(numero)
}
