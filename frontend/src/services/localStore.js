import { initialData } from '../data/mockData.js'

const KEY = 'agendapro_data'
const USER_KEY = 'agendapro_usuario'
const DATA_VERSION = 4

export const PLANOS = {
  BASICO: {
    nome: 'Plano Basico',
    rotas: ['dashboard', 'agenda', 'clientes', 'crm', 'servicos', 'configuracoes'],
  },
  PRO: {
    nome: 'Plano Pro',
    rotas: ['dashboard', 'agenda', 'clientes', 'crm', 'insights', 'servicos', 'profissionais', 'financeiro', 'relatorios', 'configuracoes'],
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
  const saved = localStorage.getItem(KEY)
  if (saved) {
    const parsed = JSON.parse(saved)
    if (parsed.__version === DATA_VERSION) return parsed
  }
  localStorage.setItem(KEY, JSON.stringify(initialData))
  return structuredClone(initialData)
}

export function clearLocalData() {
  localStorage.removeItem(KEY)
  window.dispatchEvent(new Event('agendapro:data-changed'))
}

export function setData(data) {
  localStorage.setItem(KEY, JSON.stringify(data))
  window.dispatchEvent(new Event('agendapro:data-changed'))
}

export function updateCurrentUser(partial) {
  const current = JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  if (!current) return null

  const updated = { ...current, ...partial }
  localStorage.setItem(USER_KEY, JSON.stringify(updated))
  return updated
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



