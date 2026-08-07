import { todayIso } from '../services/localStore.js'

// Delimitador ';' por compatibilidade com o Excel em português.
const DELIMITADOR = ';'
const QUEBRA_DE_LINHA = '\r\n'
const BOM = '\uFEFF'

// Caracteres usados em fórmulas no Excel. Valores que começam com eles são
// neutralizados com um apóstrofo antes de entrarem no CSV (proteção contra
// CSV Injection / fórmula maliciosa).
const CARACTERES_DE_INJECAO = ['=', '+', '-', '@']

function precisaNeutralizar(texto) {
  if (!texto) return false
  const primeiro = texto.trimStart().charAt(0)
  if (!CARACTERES_DE_INJECAO.includes(primeiro)) return false

  const normalizado = texto.trim()
  // Não neutralizar números e datas gerados pelo próprio sistema.
  if (/^-?\d{1,3}(\.\d{3})*([.,]\d+)?$/.test(normalizado)) return false
  if (/^\d{1,2}\/\d{1,2}\/\d{2,4}$/.test(normalizado)) return false
  if (/^\d{4}-\d{2}-\d{2}/.test(normalizado)) return false
  return true
}

function normalizarCelula(valor) {
  if (valor === null || valor === undefined) return ''
  let texto = String(valor)
  if (precisaNeutralizar(texto)) texto = `'${texto}`
  return texto
}

function escaparCelula(valor) {
  const texto = normalizarCelula(valor)
  const precisaAspas = texto.includes(DELIMITADOR)
    || texto.includes('"')
    || texto.includes('\r')
    || texto.includes('\n')
  if (!precisaAspas) return texto
  return `"${texto.replaceAll('"', '""')}"`
}

/**
 * Monta o conteúdo do CSV (com BOM UTF-8), separado por ';' e com quebra
 * de linha compatível com o Excel (\r\n).
 */
export function gerarCsv({ columns = [], rows = [] }) {
  const linhas = [columns.map(escaparCelula).join(DELIMITADOR)]
  for (const linha of rows) {
    linhas.push(linha.map(escaparCelula).join(DELIMITADOR))
  }
  return `${BOM}${linhas.join(QUEBRA_DE_LINHA)}`
}

/**
 * Cria o Blob, dispara o download e revoga a URL criada.
 */
export function baixarCsv({ fileName, content }) {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/**
 * Gera e baixa o arquivo CSV. Retorna false quando não há registros,
 * para a tela avisar o usuário em vez de baixar um CSV vazio.
 */
export function exportarCsv({ fileName, columns, rows }) {
  if (!Array.isArray(rows) || rows.length === 0) return false
  baixarCsv({ fileName, content: gerarCsv({ columns, rows }) })
  return true
}

/**
 * Formata datas em DD/MM/AAAA. Aceita LocalDate (2026-06-16) e
 * LocalDateTime (2026-06-16T10:05:00) serializados pelo backend.
 */
export function formatarData(valor) {
  if (valor === null || valor === undefined || valor === '') return ''
  const texto = String(valor)
  const partes = texto.match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (partes) return `${partes[3]}/${partes[2]}/${partes[1]}`
  const data = new Date(texto)
  if (!Number.isNaN(data.getTime())) return data.toLocaleDateString('pt-BR')
  return ''
}

export function metodoPagamentoLegivel(metodo) {
  if (!metodo) return ''
  const mapa = {
    PIX: 'PIX',
    PIX_AUTO: 'PIX automático',
    CREDIT_CARD: 'Cartão',
    CARTAO: 'Cartão',
    DEBIT_CARD: 'Cartão de débito',
    DEBITO: 'Débito',
    DINHEIRO: 'Dinheiro',
    BOLETO: 'Boleto',
    TRANSFERENCIA: 'Transferência',
    TRANSFER: 'Transferência',
    OUTRO: 'Outro',
  }
  return mapa[String(metodo).toUpperCase()] || String(metodo)
}

export function statusPagamentoLegivel(status) {
  const mapa = {
    PAGO: 'Aprovado',
    PAGA: 'Aprovado',
    APROVADO: 'Aprovado',
    APPROVED: 'Aprovado',
    PAID: 'Aprovado',
    PAYMENT_APPROVED: 'Aprovado',
    PENDENTE: 'Pendente',
    PAYMENT_PENDING: 'Pendente',
    CANCELADO: 'Cancelado',
    PAYMENT_CANCELED: 'Cancelado',
    ESTORNADO: 'Estornado',
    REFUNDED: 'Estornado',
    PAYMENT_REJECTED: 'Recusado',
    REJECTED: 'Recusado',
    PAYMENT_EXPIRED: 'Expirado',
    EXPIRED: 'Expirado',
  }
  return mapa[String(status || '').toUpperCase()] || String(status || '')
}

export function statusAgendamentoLegivel(status) {
  const mapa = {
    PENDENTE: 'Pendente',
    CONFIRMADO: 'Confirmado',
    EM_ATENDIMENTO: 'Em atendimento',
    PAUSADO: 'Pausado',
    FINALIZADO: 'Finalizado',
    CANCELADO: 'Cancelado',
  }
  return mapa[String(status || '').toUpperCase()] || String(status || '')
}

/** Converte "YYYY-MM-DD" (input date) em "DD-MM-AAAA" para nome de arquivo. */
export function isoParaArquivo(iso) {
  if (!iso) return ''
  const [ano, mes, dia] = String(iso).split('-')
  if (!ano || !mes || !dia) return ''
  return `${dia}-${mes}-${ano}`
}

/** Data de hoje no formato DD-MM-AAAA (timezone local, mesmo padrão de todayIso). */
export function dataHojeDdMmAAAA() {
  return isoParaArquivo(todayIso())
}

/** Intervalo "DD-MM-AAAA-a-DD-MM-AAAA" para nomes de arquivo por período. */
export function periodoParaArquivo(dataInicial, dataFinal) {
  return `${isoParaArquivo(dataInicial)}-a-${isoParaArquivo(dataFinal)}`
}
