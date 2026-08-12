import { initialData } from '../data/mockData.js'

const DATA_VERSION = 4
let memoryData = structuredClone(initialData)
let memoryUser = null

export const PLANOS = {
  BASICO: {
    nome: 'Plano Basico',
    rotas: ['dashboard', 'agenda', 'clientes', 'promocoes', 'servicos', 'financeiro', 'relatorios', 'configuracoes'],
  },
  PRO: {
    nome: 'Plano Pro',
    rotas: ['dashboard', 'agenda', 'clientes', 'crm', 'insights', 'promocoes', 'servicos', 'profissionais', 'financeiro', 'relatorios', 'configuracoes'],
  },
}

export function emptyData(usuario = null) {
  return {
    __remote: true,
    empresa: {
      id: usuario?.empresaId || null,
      nomeFantasia: usuario?.empresaNome || '',
      documento: '',
      telefone: '',
      email: usuario?.email || '',
      ramo: null,
      ramoDisplayName: null,
      diasRegular: null,
      diasAltoRisco: null,
      ramoAtualizadoEm: null,
    },
    usuarios: [],
    equipe: [],
    clientes: [],
    servicos: [],
    profissionais: [],
    agendamentos: [],
    conversas: [],
    mensagens: [],
    pagamentos: [],
    planos: [],
    financeiro: {
      totalRecebidoMes: 0,
      totalPendente: 0,
      consultasRealizadas: 0,
      clientesMaisConsultas: [],
      servicosMaisVendidos: [],
    },
    notasFiscais: [],
    entregas: [],
    produtos: [],
    pedidos: [],
  }
}

export function getData() {
  if (memoryData?.__version === DATA_VERSION) return memoryData
  memoryData = structuredClone(initialData)
  return memoryData
}

export function clearLocalData() {
  memoryData = structuredClone(initialData)
  window.dispatchEvent(new Event('gendaz:data-changed'))
}

export function clearSensitiveStorage() {
  return
}

export function setData(data) {
  memoryData = data
  window.dispatchEvent(new Event('gendaz:data-changed'))
}

export function updateCurrentUser(partial) {
  const current = memoryUser
  if (!current) return null

  const updated = { ...current, ...partial }
  memoryUser = updated
  return updated
}

export function setCurrentUser(usuario) {
  memoryUser = usuario || null
}

export function getCurrentUser() {
  return memoryUser
}

export function nextId(items) {
  return items.length ? Math.max(...items.map((item) => item.id)) + 1 : 1
}

export function currency(value) {
  return Number(value || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function todayIso() {
  const hoje = new Date()
  const timezoneOffset = hoje.getTimezoneOffset() * 60000
  return new Date(hoje.getTime() - timezoneOffset).toISOString().slice(0, 10)
}

