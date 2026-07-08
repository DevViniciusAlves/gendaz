export function somenteNumeros(valor) {
  return String(valor || '').replace(/\D/g, '')
}

export function aplicarMascara(telefone) {
  if (!telefone) return ''
  const digitos = somenteNumeros(telefone)
  if (digitos.length === 0) return ''
  if (digitos.length <= 2) return `+${digitos}`
  if (digitos.length <= 4) return `+${digitos.slice(0, 2)} (${digitos.slice(2)}`
  if (digitos.length <= 9) return `+${digitos.slice(0, 2)} (${digitos.slice(2, 4)}) ${digitos.slice(4)}`
  return `+${digitos.slice(0, 2)} (${digitos.slice(2, 4)}) ${digitos.slice(4, 9)}-${digitos.slice(9, 13)}`
}

export function validarTelefone(telefone) {
  if (!telefone) return 'Telefone é obrigatório'
  const digitos = somenteNumeros(telefone)
  if (digitos.length === 0) return 'Telefone é obrigatório'
  if (digitos.length < 13) {
    return `Incompleto: ${digitos.length}/13 dígitos. Formato: +55 (DDD) 99999-9999`
  }
  if (digitos.length > 13) return 'Telefone muito longo'
  if (!digitos.startsWith('55')) return 'Adicione o código do país +55'
  const ddd = parseInt(digitos.substring(2, 4), 10)
  if (ddd < 11 || ddd > 99) return 'DDD inválido. Deve ser entre 11 e 99'
  return ''
}

export function padronizarTelefone(entrada) {
  const digitos = somenteNumeros(entrada)
  if (!digitos) return null

  let normalizado = digitos
  if (!normalizado.startsWith('55')) {
    normalizado = `55${normalizado}`
  }
  if (normalizado.length !== 13) return null

  const ddd = parseInt(normalizado.substring(2, 4), 10)
  if (ddd < 11 || ddd > 99) return null

  return normalizado
}

export function exibirTelefone(numero) {
  if (!numero) return ''
  return aplicarMascara(numero)
}
