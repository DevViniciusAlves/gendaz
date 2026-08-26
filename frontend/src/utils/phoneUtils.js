import {
  getExampleNumber,
  getCountryCallingCode,
  isValidPhoneNumber,
  parsePhoneNumberFromString,
} from 'libphonenumber-js'
import examples from 'libphonenumber-js/mobile/examples'

// Adaptador entre o backend (telefone canônico somente dígitos) e a biblioteca
// internacional (E.164 com +). Nenhuma regra manual de DDD/DDI ou Brasil fixo.

function e164Valido(valor) {
  if (!valor) return null
  const txt = String(valor).trim()
  if (!txt) return null
  return parsePhoneNumberFromString(txt)
}

// valorDoBackend (canônico em dígitos) -> valor E164 para o formulário/componente
export function normalizarParaInput(valorDoBackend) {
  const digito = String(valorDoBackend || '').replace(/\D/g, '')
  if (!digito) return ''
  const numero = parsePhoneNumberFromString('+' + digito)
  return numero && numero.isValid() ? numero.number : '+' + digito
}

// valor E164 -> valor canônico (somente dígitos) para enviar à API
export function normalizarParaApi(valorE164) {
  const valor = normalizarParaInput(valorE164)
  return valor ? String(valor).replace(/\D/g, '') : ''
}

// valor canônico -> leitura humana internacional (+55 65 99336-0341)
export function formatarTelefoneExibicao(valorDoBackend) {
  const numero = e164Valido(normalizarParaInput(valorDoBackend))
  if (!numero || !numero.isValid()) {
    const digito = String(valorDoBackend || '').replace(/\D/g, '')
    return digito ? '+' + digito : ''
  }
  return numero.formatInternational()
}

export function obterExemploTelefone(pais) {
  try {
    const exemplo = getExampleNumber((pais || 'BR').toUpperCase(), examples)
    if (!exemplo) return ''
    return (exemplo.formatNational ? exemplo.formatNational() : exemplo.formatInternational()) || ''
  } catch {
    return ''
  }
}

const NOMES_PAIS = {
  BR: 'Brasil',
  US: 'Estados Unidos',
  GB: 'Reino Unido',
  PT: 'Portugal',
  AR: 'Argentina',
  ES: 'Espanha',
  FR: 'França',
  DE: 'Alemanha',
  IT: 'Itália',
  MX: 'México',
  CA: 'Canadá',
  AU: 'Austrália',
  JP: 'Japão',
  CO: 'Colômbia',
  CL: 'Chile',
  PE: 'Peru',
  UY: 'Uruguai',
  PY: 'Paraguai',
  BO: 'Bolívia',
}

export function obterNomePais(pais) {
  if (!pais) return 'Brasil'
  return NOMES_PAIS[pais.toUpperCase()] || pais.toUpperCase()
}

// Mensagens dinâmicas de erro segundo a especificação.
function mensagemNumeroInvalido(pais) {
  const exemplo = obterExemploTelefone(pais)
  if (exemplo) {
    return `Número no formato errado. Exemplo para ${obterNomePais(pais) || 'o país selecionado'}: ${exemplo}.`
  }
  return 'Número no formato errado. Confira o código do país e o número.'
}

// Retorna '' se válido, ou mensagem de erro (obrigatorio pode ser true/false)
export function validarTelefone(valor, pais, obrigatorio = true) {
  const texto = String(valor || '')
  if (!texto.trim()) {
    return obrigatorio ? 'Telefone é obrigatório.' : ''
  }
  const numero = e164Valido(texto)
  if (!numero) {
    return mensagemNumeroInvalido(pais)
  }
  if (!numero.isValid()) {
    return mensagemNumeroInvalido(pais)
  }
  return ''
}

export function validarTelefoneComPais(valor, pais, obrigatorio = true) {
  return validarTelefone(valor, pais, obrigatorio)
}

// Detecta incompatibilidade país selecionado x número, quando a biblioteca tiver
// certeza. Retorna true se incompatível, senão false.
export function telefonePertenceAPaisDiferente(valor, pais) {
  const numero = e164Valido(String(valor || ''))
  if (!numero || !numero.getCountryCode) return false
  const codigoPais = numero.getCountryCode()
  if (!codigoPais || !pais) return false
  return codigoPais.toUpperCase() !== String(pais).toUpperCase()
}

// Alias de leitura amigável para manter compatibilidade de exibição.
export function exibirTelefone(valorDoBackend) {
  return formatarTelefoneExibicao(valorDoBackend)
}

// Mantidos como adaptadores sem regra brasileira (usados por telas legadas
// durante a migração; preferir o componente internacional).
export function somenteNumeros(valor) {
  return String(valor || '').replace(/\D/g, '')
}

export function padronizarTelefone(valor) {
  return normalizarParaApi(valor)
}

export function aplicarMascara(valor) {
  return normalizarParaInput(valor)
}
