import { BadgeCheck, Ban, BarChart2, CheckCircle2, CreditCard, Eye, LayoutDashboard, Loader, LogOut, Pencil, Power, RefreshCw, ScrollText, Search, Settings2, Ticket, Trash2, Users, XCircle } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { adminApi } from '../../api/adminApi.js'
import { formatoCompactoReceita } from '../../utils/formatters.js'
import { normalizarParaApi, exibirTelefone } from '../../utils/phoneUtils.js'
import InternationalPhoneInput from '../../components/InternationalPhoneInput.jsx'
import Button from '../../components/Button.jsx'
import Modal from '../../components/Modal.jsx'
import StatusBadge from '../../components/StatusBadge.jsx'
import Table from '../../components/Table.jsx'
import GraficoReceitaMes from '../../components/gendaz/GraficoReceitaMes.jsx'
import { useAuth } from '../../contexts/AuthContext.jsx'
import logoAdmin from '../../assets/logos/gendaz-logo-branco.png'

const abas = [
  { label: 'Dashboard', icon: LayoutDashboard },
  { label: 'Usuarios', icon: Users },
  { label: 'Pagamentos', icon: CreditCard },
  { label: 'Aprovar Pagamentos', icon: BadgeCheck },
  { label: 'Chamados', icon: Ticket },
  { label: 'Logs', icon: ScrollText },
  { label: 'Configuracoes', icon: Settings2 },
]
const STATUS_PAGAMENTO_CONFIRMADO = new Set([
  'PAGO',
  'PAGA',
  'CONFIRMADO',
  'CONFIRMADA',
  'APROVADO',
  'APPROVED',
  'PAID',
  'PAYMENT_APPROVED',
  'PURCHASE_APPROVED',
])

function todayIso() {
  const hoje = new Date()
  const offset = hoje.getTimezoneOffset() * 60000
  return new Date(hoje.getTime() - offset).toISOString().slice(0, 10)
}

function statusNormalizado(valor) {
  return String(valor || '').toUpperCase()
}

function moeda(valor) {
  return Number(valor || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function pagamentoConfirmado(status) {
  return STATUS_PAGAMENTO_CONFIRMADO.has(statusNormalizado(status))
}

function extrairDataPagamento(pagamento) {
  return String(pagamento?.dataPagamento || pagamento?.dataCriacao || pagamento?.data || pagamento?.createdAt || '').slice(0, 10)
}

function diasDoMesAtual() {
  const hoje = new Date(`${todayIso()}T12:00:00`)
  return new Date(hoje.getFullYear(), hoje.getMonth() + 1, 0).getDate()
}

function buildReceitaMes(pagamentos) {
  const listaPagamentos = Array.isArray(pagamentos) ? pagamentos : []
  const hoje = new Date(`${todayIso()}T12:00:00`)
  const mapaReceita = {}

  listaPagamentos.forEach((p) => {
    if (!pagamentoConfirmado(p.status)) return
    const dia = extrairDataPagamento(p)
    if (!dia || !dia.startsWith(`${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}`)) return
    
    // Extrai o dia para usar como chave e label
    const diaDoMes = parseInt(dia.split('-')[2], 10)
    mapaReceita[diaDoMes] = (mapaReceita[diaDoMes] || 0) + Number(p.valor || 0)
  })

  // Ordena os dias que tiveram movimento
  return Object.keys(mapaReceita)
    .map(Number)
    .sort((a, b) => a - b)
    .map((dia) => ({
      iso: dia.toString(),
      label: dia.toString(),
      valor: mapaReceita[dia]
    }))
}



function acaoModalTitulo(modal) {
  if (modal?.tipo === 'pagamento-aprovar') return `Aprovar pagamento de ${modal?.empresa || ''}`
  if (modal?.tipo === 'pagamento-desaprovar') return `Reverter pagamento de ${modal?.empresa || ''}`
  if (modal?.tipo === 'pagamento-detalhes') return `Detalhes do pagamento de ${modal?.empresa || ''}`
  if (modal?.tipo === 'empresa-ativar') return `Ativar conta de ${modal?.empresa || ''}`
  if (modal?.tipo === 'empresa-desativar') return `Desativar conta de ${modal?.empresa || ''}`
  if (modal?.tipo === 'empresa-editar') return `Editar empresa ${modal?.empresa || ''}`
  if (modal?.tipo === 'chamado-status') return `Atualizar chamado de ${modal?.assunto || modal?.empresa || ''}`
  return `Acessar conta de ${modal?.empresa || ''}`
}

function mensagemErroApi(error, fallback) {
  return error.response?.data?.mensagem
    || Object.values(error.response?.data?.campos || {})[0]
    || error.response?.data?.message
    || fallback
}

function contemTermo(item, termo, campos) {
  const normalizado = termo.trim().toLowerCase()
  if (!normalizado) return true
  return campos.some((campo) => String(item[campo] || '').toLowerCase().includes(normalizado))
}

function formatarDataHora(valor) {
  if (!valor) return '-'
  const texto = String(valor)
  const temTimezone = /([zZ]|[+-]\d{2}:?\d{2})$/.test(texto)
  const data = new Date(temTimezone ? texto : `${texto}Z`)
  if (Number.isNaN(data.getTime())) return '-'
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(data)
}

function formatarDataSimples(valor) {
  if (!valor) return '-'
  const texto = String(valor).slice(0, 10)
  const data = new Date(`${texto}T12:00:00`)
  if (Number.isNaN(data.getTime())) return '-'
  return data.toLocaleDateString('pt-BR')
}

function rotuloPlano(valor) {
  const plano = String(valor || '').trim().toUpperCase()
  if (plano === 'BASICO') return 'Básico'
  if (plano === 'PRO') return 'Pro'
  return 'Plano não identificado'
}

const PLANOS_PADRAO_ADMIN = [
  { id: 'BASICO', nome: 'BASICO', descricao: 'Agenda, clientes e servicos.' },
  { id: 'PRO', nome: 'PRO', descricao: 'Agenda com financeiro, pagamentos e relatorios.' },
]

function normalizarPlanosAdmin(planosRecebidos) {
  const planosValidos = Array.isArray(planosRecebidos) ? planosRecebidos.filter((plano) => plano && plano.id != null && plano.nome) : []
  if (planosValidos.length > 0) return planosValidos
  return PLANOS_PADRAO_ADMIN
}

export default function AdminDashboard() {
  const navigate = useNavigate()
  const { adminUsuario, adminLogout, iniciarImpersonacao, impersonation, encerrarImpersonacao } = useAuth()
  const [aba, setAba] = useState('Dashboard')
  const [dashboard, setDashboard] = useState(null)
  const [usuarios, setUsuarios] = useState([])
  const [pagamentos, setPagamentos] = useState([])
  const [pagamentosModeracao, setPagamentosModeracao] = useState([])
  const [logs, setLogs] = useState([])
  const [chamados, setChamados] = useState([])
  const [config, setConfig] = useState(null)
  const [planos, setPlanos] = useState([])
  const [modal, setModal] = useState(null)
  const [motivo, setMotivo] = useState('')
  const [transacaoId, setTransacaoId] = useState('')
  const [empresaEdicao, setEmpresaEdicao] = useState({ nomeFantasia: '', telefone: '', email: '' })
  const [assinaturas, setAssinaturas] = useState([])
  const [adicionandoPlano, setAdicionandoPlano] = useState(false)
  const [novaAssinatura, setNovaAssinatura] = useState({ planoId: '', dias: 30 })
  const [editandoAssinaturaId, setEditandoAssinaturaId] = useState(null)
  const [assinaturaEditForm, setAssinaturaEditForm] = useState({ planoId: '', dias: 30 })
  const [salvandoAssinatura, setSalvandoAssinatura] = useState(false)
  const [chamadoEdicao, setChamadoEdicao] = useState({ status: 'EM_ANALISE', resposta: '' })
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [aviso, setAviso] = useState('')
  const [carregandoAcao, setCarregandoAcao] = useState(false)
  const [recarregando, setRecarregando] = useState('')
  const [filtroPagamento, setFiltroPagamento] = useState({ status: '', plano: '' })
  const [filtroLog, setFiltroLog] = useState({ tipo: '', severidade: '' })
  const [pesquisaPagamento, setPesquisaPagamento] = useState('')
  const [pesquisaAprovacao, setPesquisaAprovacao] = useState('')
  const [pesquisaChamado, setPesquisaChamado] = useState('')
  const [pesquisaLog, setPesquisaLog] = useState('')
  const motivoValido = motivo.trim().length >= 8
  const motivoRestante = Math.max(0, 8 - motivo.trim().length)

  useEffect(() => {
    if (!aviso) return undefined
    const timer = setTimeout(() => setAviso(''), 3000)
    return () => clearTimeout(timer)
  }, [aviso])

  useEffect(() => {
    if (!erro) return undefined
    const timer = setTimeout(() => setErro(''), 5000)
    return () => clearTimeout(timer)
  }, [erro])

  async function carregarAdmin() {
    setCarregando(true)
    try {
      const results = await Promise.allSettled([
        adminApi.dashboard(),
        adminApi.usuarios(),
        adminApi.pagamentos(),
        adminApi.chamados(),
        adminApi.logs(),
        adminApi.configuracoes(),
        adminApi.planos(),
      ])

      if (results[0].status === 'fulfilled') setDashboard(results[0].value)
      if (results[1].status === 'fulfilled') setUsuarios(Array.isArray(results[1].value) ? results[1].value : [])
      if (results[2].status === 'fulfilled') {
        const listaPagamentos = Array.isArray(results[2].value) ? results[2].value : []
        setPagamentos(listaPagamentos)
        setPagamentosModeracao(listaPagamentos.filter((item) => ['PAYMENT_PENDING', 'PAYMENT_APPROVED', 'PAYMENT_REJECTED'].includes(item.status)))
      }
      if (results[3].status === 'fulfilled') setChamados(Array.isArray(results[3].value) ? results[3].value : [])
      if (results[4].status === 'fulfilled') setLogs(Array.isArray(results[4].value) ? results[4].value : [])
      if (results[5].status === 'fulfilled') setConfig(results[5].value)
      if (results[6].status === 'fulfilled') setPlanos(normalizarPlanosAdmin(results[6].value))
      else setPlanos(PLANOS_PADRAO_ADMIN)

      if (results.some(r => r.status === 'rejected')) {
        console.error('Algumas chamadas do painel falharam')
      }
    } catch (e) {
      console.error('Falha crítica ao carregar painel')
      setErro('Erro crítico ao carregar painel. Tente novamente.')
    } finally {
      setCarregando(false)
    }
  }

  useEffect(() => {
    if (!adminUsuario) {
      navigate('/admin/login')
      return
    }
    carregarAdmin().catch(() => {
      setErro('Nao foi possivel carregar os dados do painel admin agora. Tente recarregar.')
    })
  }, [adminUsuario, navigate])

  useEffect(() => {
    if (!adminUsuario) return
    let ativo = true

    async function atualizarFinanceiro() {
      try {
        const [dashboardData, pagamentosData] = await Promise.all([
          adminApi.dashboard(),
          adminApi.pagamentos(filtroPagamento),
        ])
        const listaPagamentos = Array.isArray(pagamentosData) ? pagamentosData : []
        let pagamentosModeracao
        if (filtroPagamento.status === '' && filtroPagamento.plano === '') {
          pagamentosModeracao = listaPagamentos.filter((item) => ['PAYMENT_PENDING', 'PAYMENT_APPROVED', 'PAYMENT_REJECTED'].includes(item.status))
        } else {
          const respostaPagamentos = await adminApi.pagamentos()
          const todosPagamentos = Array.isArray(respostaPagamentos) ? respostaPagamentos : []
          pagamentosModeracao = todosPagamentos.filter((item) => ['PAYMENT_PENDING', 'PAYMENT_APPROVED', 'PAYMENT_REJECTED'].includes(item.status))
        }
        if (!ativo) return
        setDashboard(dashboardData)
        setPagamentos(listaPagamentos)
        setPagamentosModeracao(Array.isArray(pagamentosModeracao) ? pagamentosModeracao : [])
      } catch {
        // polling silencioso para nao poluir o painel com erros
      }
    }

    atualizarFinanceiro()
    const timer = setInterval(atualizarFinanceiro, 15000)
    const onFocus = () => { atualizarFinanceiro() }
    window.addEventListener('focus', onFocus)
    return () => {
      ativo = false
      clearInterval(timer)
      window.removeEventListener('focus', onFocus)
    }
  }, [adminUsuario, filtroPagamento])

  async function recarregarAbaAtual() {
    if (recarregando) return
    setErro('')
    setAviso('')
    setRecarregando(aba)
    try {
      if (aba === 'Dashboard' || aba === 'Usuarios' || aba === 'Configuracoes') {
        await carregarAdmin()
      } else if (aba === 'Pagamentos') {
        const respostaPagamentos = await adminApi.pagamentos(filtroPagamento)
        setPagamentos(Array.isArray(respostaPagamentos) ? respostaPagamentos : [])
      } else if (aba === 'Aprovar Pagamentos') {
        const respostaPagamentos = await adminApi.pagamentos()
        const pagamentosData = Array.isArray(respostaPagamentos) ? respostaPagamentos : []
        setPagamentosModeracao(pagamentosData.filter((item) => ['PAYMENT_PENDING', 'PAYMENT_APPROVED', 'PAYMENT_REJECTED'].includes(item.status)))
      } else if (aba === 'Chamados') {
        setChamados(await adminApi.chamados())
      } else if (aba === 'Logs') {
        setLogs(await adminApi.logs())
      }
      setAviso('Dados atualizados.')
    } catch (error) {
      setErro(mensagemErroApi(error, 'Nao foi possivel recarregar os dados agora.'))
    } finally {
      setRecarregando('')
    }
  }

  const metricas = useMemo(() => dashboard ? [
    ['Faturamento total', moeda(dashboard.faturamentoTotal)],
    ['Faturamento do mes', moeda(dashboard.faturamentoMes)],
    ['Pagamentos confirmados', dashboard.pagamentosConfirmados],
    ['Pagamentos pendentes', dashboard.pagamentosPendentes],
    ['Assinaturas ativas', dashboard.assinaturasAtivas],
    ['Empresas em teste', dashboard.empresasTesteGratis],
    ['Empresas vencidas', dashboard.empresasVencidas],
    ['Usuarios ativos', dashboard.usuariosAtivos],
    ['Novos cadastros', dashboard.novosCadastros],
  ] : [], [dashboard])

  const contasAtivas = dashboard?.assinaturasAtivas || 0
  const contasCanceladas = dashboard?.empresasVencidas || 0
  const contasTeste = dashboard?.empresasTesteGratis || 0
  const contasAtivasPct = Math.round((contasAtivas / Math.max(contasAtivas + contasCanceladas + contasTeste, 1)) * 100)
  const receitaMensalGrafico = buildReceitaMes(Array.isArray(pagamentos) ? pagamentos : [])
  const pagamentosConfirmadosLista = (Array.isArray(pagamentos) ? pagamentos : []).filter((item) => pagamentoConfirmado(item.status))
  const pagamentosPendentesLista = (Array.isArray(pagamentos) ? pagamentos : []).filter((item) => statusNormalizado(item.status) === 'PENDENTE')
  const pagamentoMaisRecente = (Array.isArray(pagamentos) ? [...pagamentos] : [])
    .sort((a, b) => String(b.dataPagamento || b.dataCriacao || b.data || '').localeCompare(String(a.dataPagamento || a.dataCriacao || a.data || '')))
    .slice(0, 5)
  const planoResumo = useMemo(() => {
    const mapa = {}
    ;(usuarios || []).forEach((item) => {
      const plano = rotuloPlano(item.plano)
      mapa[plano] = (mapa[plano] || 0) + 1
    })
    return Object.entries(mapa).sort((a, b) => b[1] - a[1]).slice(0, 4)
  }, [usuarios])

  const assinaturasAtivas = useMemo(() => (assinaturas || []).filter((item) => {
    const status = String(item.status || '').toUpperCase()
    if (status !== 'ATIVA' && status !== 'TESTE') return false
    const fim = String(item.dataFim || '')
    if (!fim) return true
    return fim > todayIso()
  }), [assinaturas])

  const pagamentosFiltrados = useMemo(() => (Array.isArray(pagamentos) ? pagamentos : []).filter((item) => (
    contemTermo(item, pesquisaPagamento, ['empresa', 'responsavel', 'email', 'telefone', 'plano', 'status', 'gateway', 'externalPaymentId', 'paymentReference'])
  )), [pagamentos, pesquisaPagamento])

  const pagamentosModeracaoFiltrados = useMemo(() => (Array.isArray(pagamentosModeracao) ? pagamentosModeracao : []).filter((item) => (
    contemTermo(item, pesquisaAprovacao, ['empresa', 'responsavel', 'email', 'telefone', 'plano', 'status', 'statusEmpresa', 'externalPaymentId', 'paymentReference'])
  )), [pagamentosModeracao, pesquisaAprovacao])

  const chamadosFiltrados = useMemo(() => (Array.isArray(chamados) ? chamados : []).filter((item) => (
    contemTermo(item, pesquisaChamado, ['assunto', 'empresa', 'usuario', 'status', 'resposta'])
  )), [chamados, pesquisaChamado])

  const logsFiltrados = useMemo(() => (Array.isArray(logs) ? logs : []).filter((item) => {
    const tipoOk = !filtroLog.tipo || item.tipo?.toLowerCase().includes(filtroLog.tipo.toLowerCase())
    const severidadeOk = !filtroLog.severidade || item.severidade === filtroLog.severidade
    const buscaOk = contemTermo(item, pesquisaLog, ['tipo', 'severidade', 'admin', 'usuario', 'empresa', 'descricao', 'motivo'])
    return tipoOk && severidadeOk && buscaOk
  }), [logs, filtroLog, pesquisaLog])

  function abrirModal(item, tipo) {
    setModal({ ...item, tipo })
    setMotivo('')
    setTransacaoId('')
    setEmpresaEdicao({
      nomeFantasia: item?.empresa || '',
      telefone: item?.telefone || '',
      email: item?.emailEmpresa || item?.email || '',
    })
    setChamadoEdicao({
      status: item?.status || 'EM_ANALISE',
      resposta: item?.resposta || '',
    })
    setErro('')
    setAviso('')

    if (tipo === 'empresa-editar') {
      setAssinaturas([])
      setAdicionandoPlano(false)
      setEditandoAssinaturaId(null)
      setNovaAssinatura({ planoId: '', dias: 30 })
      setAssinaturaEditForm({ planoId: '', dias: 30 })
      adminApi.listarAssinaturas(item.empresaId).then(setAssinaturas).catch(() => setAssinaturas([]))
    }
  }

  function atualizarEmpresaNaTabela(empresaAtualizada) {
    setUsuarios((atuais) => (Array.isArray(atuais) ? atuais : []).map((item) => (
      item.empresaId === empresaAtualizada.empresaId ? { ...item, ...empresaAtualizada } : item
    )))
  }

  function sair() {
    adminLogout()
    navigate('/admin/login')
  }

  function validarMotivo() {
    if (!motivoValido) {
      setErro(`Informe um motivo com pelo menos 8 caracteres. Faltam ${motivoRestante} caractere${motivoRestante === 1 ? '' : 's'}.`)
      return false
    }
    setErro('')
    return true
  }

  async function confirmarImpersonacao() {
    if (!modal) return
    if (!modal.usuarioId) {
      setErro('Usuario responsavel da empresa nao encontrado para inspecao.')
      return
    }
    setCarregandoAcao(true)
    try {
      const session = await adminApi.startImpersonation({ empresaId: modal.empresaId, usuarioId: modal.usuarioId })
      iniciarImpersonacao({
        ...session,
        empresa: modal.empresa,
        usuarioNome: modal.responsavel,
        usuarioEmail: modal.email,
        plano: modal.plano,
      })
      setModal(null)
      navigate('/sistema/dashboard')
    } catch (error) {
      setErro(mensagemErroApi(error, 'Nao foi possivel acessar esta conta agora.'))
    } finally {
      setCarregandoAcao(false)
    }
  }

  async function aprovarPagamentoManual() {
    if (!modal) return
    setCarregandoAcao(true)
    try {
      await adminApi.aprovarPagamentoManualmente(modal.id)
      await carregarAdmin()
      setAviso('Pagamento aprovado. Conta, assinatura e plano foram sincronizados.')
      setModal(null)
    } catch (error) {
      setErro(mensagemErroApi(error, 'Nao foi possivel aprovar o pagamento manualmente.'))
    } finally {
      setCarregandoAcao(false)
    }
  }

  async function desaprovarPagamentoManual() {
    if (!modal || !validarMotivo()) return
    setCarregandoAcao(true)
    try {
      await adminApi.desaprovarPagamentoManualmente(modal.id, {
        motivo: motivo.trim(),
        transacaoId: transacaoId.trim() || null,
      })
      await carregarAdmin()
      setAviso('Pagamento revertido e conta atualizada conforme a assinatura.')
      setModal(null)
    } catch (error) {
      setErro(mensagemErroApi(error, 'Nao foi possivel reverter o pagamento.'))
    } finally {
      setCarregandoAcao(false)
    }
  }

  async function atualizarStatusEmpresa() {
    if (!modal || !validarMotivo()) return
    setCarregandoAcao(true)
    try {
      let empresaAtualizada
      if (modal.tipo === 'empresa-ativar') {
        empresaAtualizada = await adminApi.ativarEmpresa(modal.empresaId, motivo.trim())
      } else {
        empresaAtualizada = await adminApi.desativarEmpresa(modal.empresaId, motivo.trim())
      }
      atualizarEmpresaNaTabela(empresaAtualizada)
      setModal(null)
      setAviso(modal.tipo === 'empresa-ativar' ? 'Conta ativada com sucesso.' : 'Conta desativada com sucesso.')
      carregarAdmin().catch(() => {
        setErro('A conta foi atualizada, mas nao foi possivel recarregar a tabela agora.')
      })
    } catch (error) {
      setErro(mensagemErroApi(error, modal.tipo === 'empresa-ativar' ? 'Nao foi possivel ativar a conta.' : 'Nao foi possivel desativar a conta.'))
    } finally {
      setCarregandoAcao(false)
    }
  }

  async function salvarEmpresaEditada() {
    if (!modal || !validarMotivo()) return
    setCarregandoAcao(true)
    try {
      const payload = {
        nomeFantasia: empresaEdicao.nomeFantasia.trim(),
        telefone: normalizarParaApi(empresaEdicao.telefone || ''),
        email: empresaEdicao.email.trim().toLowerCase(),
        planoId: null,
        diasPlano: null,
        motivo: motivo.trim(),
      }
      const empresaAtualizada = await adminApi.atualizarEmpresa(modal.empresaId, {
        ...payload,
      })
      atualizarEmpresaNaTabela(empresaAtualizada)
      setModal(null)
      setAviso('Dados da empresa atualizados com sucesso.')
      carregarAdmin().catch(() => {
        setErro('A empresa foi atualizada, mas nao foi possivel recarregar a tabela agora.')
      })
    } catch (error) {
      setErro(mensagemErroApi(error, 'Nao foi possivel atualizar os dados da empresa.'))
    } finally {
      setCarregandoAcao(false)
    }
  }

  async function salvarChamadoEditado() {
    if (!modal) return
    setCarregandoAcao(true)
    try {
      await adminApi.atualizarChamado(modal.id, {
        status: chamadoEdicao.status,
        resposta: chamadoEdicao.resposta?.trim() || null,
      })
      await carregarAdmin()
      setModal(null)
      setAviso('Chamado atualizado com sucesso.')
    } catch (error) {
      setErro(mensagemErroApi(error, 'Nao foi possivel atualizar o chamado.'))
    } finally {
      setCarregandoAcao(false)
    }
  }

  function iniciarEdicaoAssinatura(assinatura) {
    setEditandoAssinaturaId(assinatura.id)
    setAssinaturaEditForm({
      planoId: assinatura.planoId != null ? String(assinatura.planoId) : '',
      dias: assinatura.dias >= 0 ? assinatura.dias : 30,
    })
  }

  async function criarNovaAssinatura() {
    if (!modal || !novaAssinatura.planoId) {
      setErro('Selecione um plano para adicionar a conta.')
      return
    }
    const dias = novaAssinatura.dias === '' || novaAssinatura.dias == null ? 30 : Math.max(0, Number(novaAssinatura.dias))
    setSalvandoAssinatura(true)
    setErro('')
    try {
      const lista = await adminApi.criarAssinatura(modal.empresaId, {
        planoId: Number(novaAssinatura.planoId),
        dias,
      })
      setAssinaturas(lista)
      setNovaAssinatura({ planoId: '', dias: 30 })
      setAdicionandoPlano(false)
      setAviso('Plano adicionado a conta com sucesso.')
    } catch (error) {
      setErro(mensagemErroApi(error, 'Nao foi possivel adicionar o plano.'))
    } finally {
      setSalvandoAssinatura(false)
    }
  }

  async function salvarEdicaoAssinatura() {
    if (!modal || !editandoAssinaturaId || !assinaturaEditForm.planoId) {
      setErro('Selecione um plano para atualizar.')
      return
    }
    const dias = assinaturaEditForm.dias === '' || assinaturaEditForm.dias == null ? 30 : Math.max(0, Number(assinaturaEditForm.dias))
    setSalvandoAssinatura(true)
    setErro('')
    try {
      const lista = await adminApi.editarAssinatura(modal.empresaId, editandoAssinaturaId, {
        planoId: Number(assinaturaEditForm.planoId),
        dias,
        status: 'ATIVA',
      })
      setAssinaturas(lista)
      setEditandoAssinaturaId(null)
      setAviso('Plano atualizado com sucesso.')
    } catch (error) {
      setErro(mensagemErroApi(error, 'Nao foi possivel atualizar o plano.'))
    } finally {
      setSalvandoAssinatura(false)
    }
  }

  async function removerPlanoDaConta(assinatura) {
    if (!modal) return
    const semPlano = window.confirm(`Remover o plano "${assinatura.planoNome || 'Plano'}" da conta?\n\nSe nao restar nenhum plano, a conta ficara inativa.`)
    if (!semPlano) return
    setSalvandoAssinatura(true)
    setErro('')
    try {
      const lista = await adminApi.removerAssinatura(modal.empresaId, assinatura.id)
      setAssinaturas(lista)
      setEditandoAssinaturaId(null)
      setAviso('Plano removido da conta com sucesso.')
      carregarAdmin().catch(() => {
        setErro('O plano foi removido, mas nao foi possivel recarregar a tabela agora.')
      })
    } catch (error) {
      setErro(mensagemErroApi(error, 'Nao foi possivel remover o plano.'))
    } finally {
      setSalvandoAssinatura(false)
    }
  }

  function renderAcoesPagamento(item) {
    return (
      <div className="table-actions">
        <button className="icon-btn" type="button" title="Ver detalhes" onClick={() => abrirModal(item, 'pagamento-detalhes')}>
          <Eye size={16} />
        </button>
        {['PAYMENT_PENDING', 'PAYMENT_REJECTED'].includes(item.status) && (
          <button className="icon-btn" type="button" title="Aprovar manualmente" onClick={() => abrirModal(item, 'pagamento-aprovar')}>
            <CheckCircle2 size={16} />
          </button>
        )}
        {['PAYMENT_PENDING', 'PAYMENT_APPROVED', 'PAYMENT_REJECTED'].includes(item.status) && (
          <button className="icon-btn" type="button" title="Desaprovar pagamento" onClick={() => abrirModal(item, 'pagamento-desaprovar')}>
            <XCircle size={16} />
          </button>
        )}
      </div>
    )
  }

  return (
    <main className="admin-shell admin-gendaz gendaz-shell">
      <aside className="admin-gendaz-sidebar gendaz-sidebar">
        <div className="sidebar-logo-wrapper">
          <img src={logoAdmin} alt="gendaz" className="sidebar-logo" />
        </div>
        <span className="nav-label">Painel admin</span>
        <nav>
          {abas.map(({ label, icon: Icon }) => (
            <button
              key={label}
              type="button"
              className={`gendaz-sidebar__link ${aba === label ? 'is-active' : ''}`}
              onClick={() => setAba(label)}
            >
              <Icon size={18} />
              <span>{label}</span>
            </button>
          ))}
        </nav>
        <div className="admin-gendaz-sidebar-foot">
          {adminUsuario?.email && <span className="admin-gendaz-sidebar-email">{adminUsuario.email}</span>}
          <button type="button" className="gendaz-sidebar__link" onClick={sair}>
            <LogOut size={16} />
            <span>Sair</span>
          </button>
        </div>
      </aside>

      <section className="admin-gendaz-content gendaz-main">
        {carregando && <div className="admin-loading">Carregando...</div>}
        {impersonation && (
          <div className="impersonation-banner" style={{ margin: '0 0 16px' }}>
            <strong>Contexto ativo: {impersonation.empresa}.</strong>
            <span>Voce esta com acesso visual a esta conta.</span>
            <button
              type="button"
              onClick={() => navigate('/sistema/dashboard')}
            >
              Ver conta
            </button>
            <button
              type="button"
              onClick={() => {
                encerrarImpersonacao()
                setAviso('Contexto da empresa encerrado.')
              }}
            >
              Encerrar contexto
            </button>
          </div>
        )}
        {(aviso || (!modal && erro)) && (
          <div className={`admin-toast ${aviso ? 'success' : 'error'}`} role="status">
            <span>{aviso || erro}</span>
            <button type="button" aria-label="Fechar notificacao" onClick={() => { setAviso(''); setErro('') }}>x</button>
          </div>
        )}

        {aba === 'Dashboard' && (
          <div className="admin-gendaz-page">
            <div className="admin-gendaz-page-head">
              <div>
                <span className="admin-gendaz-kicker">Super Admin</span>
                <h1>Dashboard administrativo</h1>
                <p>Visao tática da saude do Gendaz com contas, pagamentos e fluxo operacional.</p>
              </div>
              <div className="page-title-actions">
                <Button icon={RefreshCw} variant="secondary" onClick={recarregarAbaAtual} loading={recarregando === 'Dashboard'} loadingText="Recarregando...">
                  Recarregar
                </Button>
              </div>
            </div>
            <div className="admin-strategy-grid">
              <article className="admin-strategy-card">
                <span>Contas ativas</span>
                <strong>{contasAtivas}</strong>
                <small>{contasAtivasPct}% da base atual</small>
              </article>
              <article className="admin-strategy-card">
                <span>Contas canceladas</span>
                <strong>{contasCanceladas}</strong>
                <small>vencidas ou bloqueadas</small>
              </article>
              <article className="admin-strategy-card">
                <span>Contas em teste</span>
                <strong>{contasTeste}</strong>
                <small>periodo gratuito ativo</small>
              </article>
              <article className="admin-strategy-card admin-strategy-card--highlight">
                <span>Total ganho</span>
                <strong>{moeda(dashboard?.faturamentoTotal)}</strong>
                <small>{moeda(dashboard?.faturamentoMes)} neste mes</small>
              </article>
            </div>
            <div className="admin-metrics">
              {metricas.map(([label, value]) => (
                <article key={label}>
                  <span>{label}</span>
                  <strong>{value}</strong>
                </article>
              ))}
            </div>
            <div className="admin-panels admin-panels--tactical">
               <section className="admin-tactical-panel">
                 <div className="panel-head">
                   <div>
                     <span className="section-kicker">Financeiro</span>
                     <h2>Receita dos pagamentos</h2>
                     <p>Base confirmada por data de pagamento no mês corrente.</p>
                   </div>
                 </div>
                 <GraficoReceitaMes dados={receitaMensalGrafico} />
               </section>
              <section className="admin-tactical-panel">
                <div className="panel-head">
                  <div>
                    <span className="section-kicker">Operacao</span>
                    <h2>Status geral das contas</h2>
                    <p>Leitura rapida da saude da base Gendaz.</p>
                  </div>
                </div>
                <div className="admin-status-stack">
                  <div className="admin-status-row">
                    <span>Ativas</span>
                    <strong>{contasAtivas}</strong>
                  </div>
                  <div className="admin-status-row">
                    <span>Canceladas</span>
                    <strong>{contasCanceladas}</strong>
                  </div>
                  <div className="admin-status-row">
                    <span>Teste</span>
                    <strong>{contasTeste}</strong>
                  </div>
                  <div className="admin-status-row">
                    <span>Usuarios ativos</span>
                    <strong>{dashboard?.usuariosAtivos || 0}</strong>
                  </div>
                </div>
                <div className="admin-mini-bars">
                  {planoResumo.map(([plano, total]) => (
                    <div key={plano} className="admin-mini-bar">
                      <div>
                        <span>{plano}</span>
                        <strong>{total}</strong>
                      </div>
                      <div className="admin-mini-bar-track">
                        <i style={{ width: `${Math.max(12, (total / Math.max(planoResumo[0]?.[1] || 1, 1)) * 100)}%` }} />
                      </div>
                    </div>
                  ))}
                </div>
              </section>
            </div>
            <div className="admin-panels">
              <section>
                <h2>Receita</h2>
                {(dashboard?.receita || []).map((item) => (
                  <div className="admin-bar" key={item.periodo}>
                    <span>{item.periodo}</span>
                    <strong>{moeda(item.valor)}</strong>
                  </div>
                ))}
              </section>
              <section>
                <h2>Planos</h2>
                {(dashboard?.distribuicaoPlanos || []).map((item) => (
                  <div className="admin-bar" key={item.plano}>
                    <span>{rotuloPlano(item.plano)}</span>
                    <strong>{item.total}</strong>
                  </div>
                ))}
              </section>
              <section>
                <h2>Pagamentos recentes</h2>
                {pagamentoMaisRecente.length === 0 ? (
                  <div className="admin-empty-tactical">Sem pagamentos recentes.</div>
                ) : (
                  pagamentoMaisRecente.map((item) => (
                    <div className="admin-bar" key={`${item.id}-${item.dataPagamento || item.dataCriacao || item.data || ''}`}>
                      <span>{item.empresa || 'Empresa'}</span>
                      <strong>{moeda(item.valor)}</strong>
                    </div>
                  ))
                )}
              </section>
            </div>
            <div className="admin-panels">
              <section>
                <h2>Pagamentos confirmados</h2>
                <div className="admin-bar-row">
                  <span>Confirmados no periodo</span>
                  <strong>{pagamentosConfirmadosLista.length}</strong>
                </div>
                <div className="admin-bar-row">
                  <span>Pendentes</span>
                  <strong>{pagamentosPendentesLista.length}</strong>
                </div>
              </section>
              <section>
                <h2>Resumo pratico</h2>
                <div className="admin-bar-row">
                  <span>Total ganho</span>
                  <strong>{moeda(dashboard?.faturamentoTotal)}</strong>
                </div>
                <div className="admin-bar-row">
                  <span>Faturamento do mes</span>
                  <strong>{moeda(dashboard?.faturamentoMes)}</strong>
                </div>
              </section>
            </div>
          </div>
        )}

        {aba === 'Usuarios' && (
          <section className="admin-section">
            <div className="admin-gendaz-page-head">
              <div>
                <span className="admin-gendaz-kicker">Cadastro e contas</span>
                <h1>Usuarios e empresas</h1>
              </div>
              <div className="page-title-actions">
                <Button icon={RefreshCw} variant="secondary" onClick={recarregarAbaAtual} loading={recarregando === 'Usuarios'} loadingText="Recarregando...">
                  Recarregar
                </Button>
              </div>
            </div>
            <Table columns={['Empresa', 'Responsavel', 'E-mail', 'Telefone', 'Plano', 'Status empresa', 'Assinatura', 'Ultimo pagamento', 'Acoes']}>
              {usuarios.map((item) => (
                <tr key={item.empresaId}>
                  <td>{item.empresa}</td>
                  <td>{item.responsavel}</td>
                  <td>{item.email}</td>
                  <td>{item.telefone ? exibirTelefone(item.telefone) : '-'}</td>
                  <td>{rotuloPlano(item.plano)}</td>
                  <td><StatusBadge status={item.statusEmpresa} /></td>
                  <td><StatusBadge status={item.statusAssinatura} /></td>
                  <td>{formatarDataHora(item.ultimoPagamento)}</td>
                  <td>
                    <div className="table-actions">
                      <button className="icon-btn" type="button" title="Editar empresa" onClick={() => abrirModal(item, 'empresa-editar')}>
                        <Pencil size={16} />
                      </button>
                      <button className="icon-btn" type="button" title="Acessar conta" onClick={() => abrirModal(item, 'impersonar')}>
                        <Eye size={16} />
                      </button>
                      <button
                        className="icon-btn"
                        type="button"
                        title={item.statusEmpresa === 'ATIVA' ? 'Desativar conta' : 'Ativar conta'}
                        onClick={() => abrirModal(item, item.statusEmpresa === 'ATIVA' ? 'empresa-desativar' : 'empresa-ativar')}
                      >
                        {item.statusEmpresa === 'ATIVA' ? <Ban size={16} /> : <Power size={16} />}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </Table>
          </section>
        )}

        {aba === 'Pagamentos' && (
          <section className="admin-section">
            <div className="admin-gendaz-page-head">
              <div>
                <span className="admin-gendaz-kicker">Financeiro</span>
                <h1>Pagamentos</h1>
              </div>
              <div className="page-title-actions">
                <Button icon={RefreshCw} variant="secondary" onClick={recarregarAbaAtual} loading={recarregando === 'Pagamentos'} loadingText="Recarregando...">
                  Recarregar
                </Button>
              </div>
            </div>
            <div className="admin-filters admin-filters-payments">
              <label className="search-shell">
                <input
                  maxLength={120}
                  value={pesquisaPagamento}
                  onChange={(event) => setPesquisaPagamento(event.target.value)}
                  placeholder="Pesquisar por empresa, cliente, e-mail ou telefone"
                />
              </label>
              <select value={filtroPagamento.status} onChange={(event) => setFiltroPagamento((atual) => ({ ...atual, status: event.target.value }))}>
                <option value="">Todos os status</option>
                <option value="PAGO">Pagos</option>
                <option value="PENDENTE">Pendentes</option>
                <option value="PAYMENT_PENDING">Aguardando pagamento</option>
                <option value="PAYMENT_APPROVED">Pagamento aprovado</option>
                <option value="CANCELADO">Cancelados</option>
                <option value="PAYMENT_REJECTED">Recusados</option>
                <option value="PAYMENT_EXPIRED">Vencidos</option>
              </select>
              <select value={filtroPagamento.plano} onChange={(event) => setFiltroPagamento((atual) => ({ ...atual, plano: event.target.value }))}>
                <option value="">Todos os planos</option>
                <option value="BASICO">Básico</option>
                <option value="PRO">Pro</option>
              </select>
            </div>
            <Table columns={['Empresa', 'Responsavel', 'E-mail', 'Telefone', 'Plano', 'Valor', 'Gateway', 'Status', 'Status empresa', 'Vencimento', 'Pagamento', 'Acoes']}>
              {pagamentosFiltrados.map((item) => (
                <tr key={item.id}>
                  <td>{item.empresa}</td>
                  <td>{item.responsavel || '-'}</td>
                  <td>{item.email || '-'}</td>
                  <td>{item.telefone ? exibirTelefone(item.telefone) : '-'}</td>
                  <td>{rotuloPlano(item.plano)}</td>
                  <td>{moeda(item.valor)}</td>
                  <td>{item.gateway}</td>
                  <td><StatusBadge status={item.status} /></td>
                  <td><StatusBadge status={item.statusEmpresa} /></td>
                  <td>{formatarDataHora(item.vencimento)}</td>
                  <td>{formatarDataHora(item.dataPagamento)}</td>
                  <td>{renderAcoesPagamento(item)}</td>
                </tr>
              ))}
            </Table>
          </section>
        )}

        {aba === 'Aprovar Pagamentos' && (
          <section className="admin-section">
            <div className="admin-gendaz-page-head">
              <div>
                <span className="admin-gendaz-kicker">Moderacao</span>
                <h1>Aprovar pagamentos</h1>
              </div>
              <div className="page-title-actions">
                <Button icon={RefreshCw} variant="secondary" onClick={recarregarAbaAtual} loading={recarregando === 'Aprovar Pagamentos'} loadingText="Recarregando...">
                  Recarregar
                </Button>
              </div>
            </div>
            <div className="admin-filters">
              <label className="search-shell">
                <input
                  maxLength={120}
                  value={pesquisaAprovacao}
                  onChange={(event) => setPesquisaAprovacao(event.target.value)}
                  placeholder="Pesquisar por empresa, responsavel, e-mail ou telefone"
                />
              </label>
            </div>
            <Table columns={['Empresa', 'Responsavel', 'E-mail', 'Telefone', 'Plano', 'Valor', 'Status pagamento', 'Status empresa', 'Referencia', 'Provider ID', 'Criado em', 'Acoes']}>
              {pagamentosModeracaoFiltrados.map((item) => (
                <tr key={item.id}>
                  <td>{item.empresa}</td>
                  <td>{item.responsavel || '-'}</td>
                  <td>{item.email || '-'}</td>
                  <td>{item.telefone ? exibirTelefone(item.telefone) : '-'}</td>
                  <td>{rotuloPlano(item.plano)}</td>
                  <td>{moeda(item.valor)}</td>
                  <td><StatusBadge status={item.status} /></td>
                  <td><StatusBadge status={item.statusEmpresa} /></td>
                  <td>{item.paymentReference || '-'}</td>
                  <td>{item.externalPaymentId || '-'}</td>
                  <td>{formatarDataHora(item.dataCriacao)}</td>
                  <td>{renderAcoesPagamento(item)}</td>
                </tr>
              ))}
            </Table>
          </section>
        )}

        {aba === 'Logs' && (
          <section className="admin-section">
            <div className="admin-gendaz-page-head">
              <div>
                <span className="admin-gendaz-kicker">Auditoria</span>
                <h1>Logs / Auditoria</h1>
              </div>
              <div className="page-title-actions">
                <Button icon={RefreshCw} variant="secondary" onClick={recarregarAbaAtual} loading={recarregando === 'Logs'} loadingText="Recarregando...">
                  Recarregar
                </Button>
              </div>
            </div>
            <div className="admin-filters">
              <input
                value={pesquisaLog}
                maxLength={120}
                onChange={(event) => setPesquisaLog(event.target.value)}
                placeholder="Pesquisar por evento, empresa ou usuario"
              />
              <input value={filtroLog.tipo} onChange={(event) => setFiltroLog((atual) => ({ ...atual, tipo: event.target.value }))} placeholder="Filtrar por tipo" />
              <select value={filtroLog.severidade} onChange={(event) => setFiltroLog((atual) => ({ ...atual, severidade: event.target.value }))}>
                <option value="">Todas as severidades</option>
                <option value="INFO">INFO</option>
                <option value="WARNING">WARNING</option>
                <option value="SECURITY">SECURITY</option>
                <option value="ERROR">ERROR</option>
              </select>
            </div>
            <Table columns={['Tipo', 'Severidade', 'Admin', 'Empresa', 'Descricao', 'Motivo', 'Data']}>
              {logsFiltrados.map((item) => (
                <tr key={item.id}>
                  <td>{item.tipo}</td>
                  <td><StatusBadge status={item.severidade} /></td>
                  <td>{item.admin || '-'}</td>
                  <td>{item.empresa || '-'}</td>
                  <td>{item.descricao}</td>
                  <td>{item.motivo || '-'}</td>
                  <td>{formatarDataHora(item.dataCriacao)}</td>
                </tr>
              ))}
            </Table>
          </section>
        )}

        {aba === 'Chamados' && (
          <section className="admin-section">
            <div className="admin-gendaz-page-head">
              <div>
                <span className="admin-gendaz-kicker">Suporte</span>
                <h1>Chamados</h1>
              </div>
              <div className="page-title-actions">
                <Button icon={RefreshCw} variant="secondary" onClick={recarregarAbaAtual} disabled={recarregando === 'Chamados'}>
                  {recarregando === 'Chamados' ? 'Recarregando...' : 'Recarregar'}
                </Button>
              </div>
            </div>
            <div className="admin-filters">
              <label className="search-shell">
                <input
                  maxLength={120}
                  value={pesquisaChamado}
                  onChange={(event) => setPesquisaChamado(event.target.value)}
                  placeholder="Pesquisar por assunto, empresa, usuario ou status"
                />
              </label>
            </div>
            <Table columns={['Assunto', 'Empresa', 'Usuario', 'Status', 'Resposta', 'Data', 'Acoes']}>
              {chamadosFiltrados.map((item) => (
                <tr key={item.id}>
                  <td>{item.assunto}</td>
                  <td>{item.empresa}</td>
                  <td>{item.usuario}</td>
                  <td><StatusBadge status={item.status} /></td>
                  <td>{item.resposta || '-'}</td>
                  <td>{formatarDataHora(item.dataCriacao)}</td>
                  <td>
                    <div className="table-actions">
                      <button className="icon-btn" type="button" title="Atualizar chamado" onClick={() => abrirModal(item, 'chamado-status')}>
                        <Pencil size={16} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </Table>
          </section>
        )}

        {aba === 'Configuracoes' && (
          <section className="admin-section admin-config">
            <div className="admin-gendaz-page-head">
              <div>
                <span className="admin-gendaz-kicker">Admin seguro</span>
                <h1>Configuracoes seguras</h1>
              </div>
              <div className="page-title-actions">
                <Button icon={RefreshCw} variant="secondary" onClick={recarregarAbaAtual} loading={recarregando === 'Configuracoes'} loadingText="Recarregando...">
                  Recarregar
                </Button>
              </div>
            </div>
            <div><span>PAYMENT_PROVIDER</span><strong>{config?.paymentProvider}</strong></div>
            <div><span>Frontend</span><strong>{config?.frontendUrl}</strong></div>
            <div><span>API</span><strong>{config?.apiUrl}</strong></div>
            <div><span>Status</span><strong>{config?.statusSistema}</strong></div>
            <div><span>Secrets</span><strong>Redigido</strong></div>
          </section>
        )}
      </section>

      <Modal title={acaoModalTitulo(modal)} open={Boolean(modal)} onClose={() => setModal(null)}>
        <div className="confirm-box">
          {modal?.tipo === 'pagamento-detalhes' ? (
            <div className="admin-detail-list">
              <p><strong>Empresa:</strong> {modal.empresa}</p>
              <p><strong>Responsavel:</strong> {modal.responsavel || '-'}</p>
              <p><strong>E-mail:</strong> {modal.email || '-'}</p>
              <p><strong>Status:</strong> {modal.status}</p>
              <p><strong>Provider ID:</strong> {modal.externalPaymentId || '-'}</p>
              <p><strong>Referencia:</strong> {modal.paymentReference || '-'}</p>
              <p><strong>External reference:</strong> {modal.detalhes || '-'}</p>
            </div>
          ) : modal?.tipo === 'empresa-editar' ? (
            <>
              <p>Edite os dados basicos da empresa e confirme a alteracao no painel admin.</p>
              <div className="admin-form-grid">
                <label className="field">
                  <span>Nome fantasia</span>
                  <input
                    maxLength={100}
                    value={empresaEdicao.nomeFantasia}
                    onChange={(event) => setEmpresaEdicao((atual) => ({ ...atual, nomeFantasia: event.target.value }))}
                    placeholder="Nome da empresa"
                  />
                </label>
                <InternationalPhoneInput
                  label="Telefone"
                  value={empresaEdicao.telefone}
                  onChangeValue={(valor) => setEmpresaEdicao((atual) => ({ ...atual, telefone: valor || '' }))}
                  helper="País, DDI e número no formato do país selecionado."
                  className="field-wide"
                />
                <label className="field">
                  <span>E-mail</span>
                  <input
                    maxLength={120}
                    value={empresaEdicao.email}
                    onChange={(event) => setEmpresaEdicao((atual) => ({ ...atual, email: event.target.value }))}
                    placeholder="E-mail da empresa"
                  />
                </label>
              </div>
              <div className="admin-assinaturas-block field-wide">
                <label className="field">
                  <span>Planos da conta (ate 2)</span>
                </label>
                {assinaturas.length === 0 ? (
                  <div className="admin-assinaturas-empty">Nenhum plano cadastrado nesta conta.</div>
                ) : (
                  <div className="admin-assinaturas">
                    {assinaturas.map((assinatura) => (
                      <div className="admin-assinatura" key={assinatura.id}>
                        <div className="admin-assinatura__head">
                          <strong>{assinatura.planoNome || 'Plano'}</strong>
                          <StatusBadge status={assinatura.status} />
                        </div>
                        <div className="admin-assinatura__meta">
                          <span>{formatarDataSimples(assinatura.dataInicio)} ate {formatarDataSimples(assinatura.dataFim)}</span>
                          <small>{assinatura.diasRestantes} dias restantes</small>
                        </div>
                        {editandoAssinaturaId === assinatura.id ? (
                          <div className="admin-assinatura__edit">
                            <select
                              value={assinaturaEditForm.planoId}
                              onChange={(event) => setAssinaturaEditForm((atual) => ({ ...atual, planoId: event.target.value }))}
                            >
                              <option value="">Selecionar plano</option>
                              {planos.map((plano) => (
                                <option key={plano.id} value={plano.id}>{plano.nome} - {plano.descricao}</option>
                              ))}
                            </select>
                            <input
                              type="number"
                              min={0}
                              max={3650}
                              value={assinaturaEditForm.dias}
                              onChange={(event) => setAssinaturaEditForm((atual) => ({ ...atual, dias: event.target.value }))}
                              placeholder="Dias"
                            />
                            <button type="button" className="btn btn-secondary" disabled={salvandoAssinatura} onClick={salvarEdicaoAssinatura}>
                              {salvandoAssinatura ? <><Loader className="spin" size={16} /> Salvando...</> : 'Salvar'}
                            </button>
                            <button type="button" className="btn btn-ghost" disabled={salvandoAssinatura} onClick={() => setEditandoAssinaturaId(null)}>
                              Cancelar
                            </button>
                          </div>
                        ) : (
                          <button type="button" className="btn btn-ghost admin-assinatura__editar" onClick={() => iniciarEdicaoAssinatura(assinatura)}>
                            Editar plano
                          </button>
                        )}
                        <button
                          type="button"
                          className="btn btn-ghost admin-assinatura__remover"
                          disabled={salvandoAssinatura}
                          onClick={() => removerPlanoDaConta(assinatura)}
                        >
                          {salvandoAssinatura ? <><Loader className="spin" size={16} /> Removendo...</> : <><Trash2 size={14} /> Remover</>}
                        </button>
                      </div>
                    ))}
                  </div>
                )}
                {adicionandoPlano ? (
                  <div className="admin-assinatura__add">
                    <div className="admin-assinatura__add-grid">
                      <label className="field">
                        <span>Plano</span>
                        <select
                          value={novaAssinatura.planoId}
                          onChange={(event) => setNovaAssinatura((atual) => ({ ...atual, planoId: event.target.value }))}
                        >
                          <option value="">Selecionar plano</option>
                          {planos.map((plano) => (
                            <option key={plano.id} value={plano.id}>{plano.nome} - {plano.descricao}</option>
                          ))}
                        </select>
                      </label>
                      <label className="field">
                        <span>Dias</span>
                        <input
                          type="number"
                          min={0}
                          max={3650}
                          value={novaAssinatura.dias}
                          onChange={(event) => setNovaAssinatura((atual) => ({ ...atual, dias: event.target.value }))}
                          placeholder="Dias"
                        />
                      </label>
                    </div>
                    <div className="admin-assinatura__add-actions">
                      <button type="button" className="btn btn-secondary" disabled={salvandoAssinatura} onClick={criarNovaAssinatura}>
                        {salvandoAssinatura ? <><Loader className="spin" size={16} /> Adicionando...</> : 'Adicionar'}
                      </button>
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={salvandoAssinatura}
                        onClick={() => { setAdicionandoPlano(false); setNovaAssinatura({ planoId: '', dias: 30 }) }}
                      >
                        Cancelar
                      </button>
                    </div>
                  </div>
                ) : (
                  assinaturasAtivas.length < 2 ? (
                    <button type="button" className="btn btn-secondary admin-assinatura__add-btn" onClick={() => setAdicionandoPlano(true)}>
                      + Adicionar plano
                    </button>
                  ) : (
                    <small className="field-hint">Limite atingido: a conta ja possui 2 planos ativos.</small>
                  )
                )}
              </div>
              <label className="field field-wide">
                <span>Motivo obrigatorio</span>
                <textarea
                  value={motivo}
                  minLength={8}
                  maxLength={500}
                  onChange={(event) => {
                    setMotivo(event.target.value)
                    if (erro) setErro('')
                  }}
                  placeholder="Descreva o motivo da alteracao"
                />
                <small className={motivo.length >= 500 || (!motivoValido && motivo.length > 0) ? 'field-hint limit-reached' : 'field-hint'}>
                  {motivo.length >= 500
                    ? 'Limite de caracteres atingido.'
                    : motivoValido
                      ? 'Motivo valido para auditoria.'
                      : `Informe pelo menos 8 caracteres. Faltam ${motivoRestante}.`}
                  <strong>{motivo.length}/500</strong>
                </small>
              </label>
            </>
          ) : modal?.tipo === 'chamado-status' ? (
            <>
              <p>Atualize o status do chamado e registre uma resposta quando necessario.</p>
              <div className="admin-form-grid">
                <label className="field">
                  <span>Status</span>
                  <select
                    value={chamadoEdicao.status}
                    onChange={(event) => setChamadoEdicao((atual) => ({ ...atual, status: event.target.value }))}
                  >
                    <option value="ABERTO">Aberto</option>
                    <option value="PENDENTE">Pendente</option>
                    <option value="EM_ANALISE">Em análise</option>
                    <option value="EM_ANDAMENTO">Em andamento</option>
                    <option value="RESOLVIDO">Resolvido</option>
                    <option value="NAO_RESOLVIDO">Não resolvido</option>
                    <option value="FECHADO">Fechado</option>
                  </select>
                </label>
              </div>
              <label className="field">
                <span>Mensagem do usuário</span>
                <textarea
                  className="admin-readonly-field"
                  value={modal?.mensagem || 'Nenhuma mensagem registrada.'}
                  readOnly
                  disabled
                  rows={6}
                />
              </label>
              <label className="field">
                <span>Resposta para o chamado</span>
                <textarea
                  value={chamadoEdicao.resposta}
                  maxLength={1200}
                  onChange={(event) => setChamadoEdicao((atual) => ({ ...atual, resposta: event.target.value }))}
                  placeholder="Registre uma resposta ou observacao para a equipe"
                />
                <small className={chamadoEdicao.resposta.length >= 1200 ? 'field-hint limit-reached' : 'field-hint'}>
                  <strong>{chamadoEdicao.resposta.length}/1200</strong>
                </small>
              </label>
            </>
          ) : (
            <>
              <p>
                {modal?.tipo === 'pagamento-aprovar'
                  ? 'Tem certeza que deseja aprovar este pagamento?'
                  : modal?.tipo === 'pagamento-desaprovar'
                    ? `Confirme a reversao do pagamento de ${modal?.empresa}.`
                    : modal?.tipo === 'empresa-ativar'
                      ? `Confirme a ativacao da conta de ${modal?.empresa}.`
                      : modal?.tipo === 'empresa-desativar'
                        ? `Confirme o bloqueio da conta de ${modal?.empresa}.`
                        : 'Tem certeza que deseja entrar nesta conta?'}
              </p>
              {!['pagamento-aprovar', 'impersonar'].includes(modal?.tipo) && (
                <label className="field">
                  <span>Motivo obrigatorio</span>
                  <textarea
                    value={motivo}
                    minLength={8}
                    maxLength={500}
                    onChange={(event) => {
                      setMotivo(event.target.value)
                      if (erro) setErro('')
                    }}
                    placeholder="Descreva o motivo da acao"
                  />
                  <small className={motivo.length >= 500 || (!motivoValido && motivo.length > 0) ? 'field-hint limit-reached' : 'field-hint'}>
                    {motivo.length >= 500
                      ? 'Limite de caracteres atingido.'
                      : motivoValido
                        ? 'Motivo valido para auditoria.'
                        : `Informe pelo menos 8 caracteres. Faltam ${motivoRestante}.`}
                    <strong>{motivo.length}/500</strong>
                  </small>
                </label>
              )}
              {modal?.tipo === 'pagamento-desaprovar' && (
                <label className="field">
                  <span>ID/transacao da Stripe</span>
                  <input value={transacaoId} onChange={(event) => setTransacaoId(event.target.value)} placeholder="Informe o identificador do pagamento, se houver" />
                </label>
              )}
            </>
          )}
          {erro && <p className="form-error">{erro}</p>}
          <div className="confirm-actions">
            <Button variant="secondary" onClick={() => setModal(null)}>Cancelar</Button>
              {modal?.tipo === 'pagamento-aprovar'
                ? <Button icon={CheckCircle2} loading={carregandoAcao} loadingText="Aprovando..." onClick={aprovarPagamentoManual}>Confirmar aprovacao</Button>
                : modal?.tipo === 'pagamento-desaprovar'
                  ? <Button icon={XCircle} disabled={!motivoValido} loading={carregandoAcao} loadingText="Revertendo..." onClick={desaprovarPagamentoManual}>Desaprovar pagamento</Button>
                  : modal?.tipo === 'empresa-ativar' || modal?.tipo === 'empresa-desativar'
                    ? <Button icon={modal?.tipo === 'empresa-ativar' ? Power : Ban} disabled={!motivoValido} loading={carregandoAcao} loadingText="Salvando..." onClick={atualizarStatusEmpresa}>Confirmar</Button>
                    : modal?.tipo === 'empresa-editar'
                      ? <Button icon={Pencil} disabled={!motivoValido} loading={carregandoAcao} loadingText="Salvando..." onClick={salvarEmpresaEditada}>Salvar alteracoes</Button>
                      : modal?.tipo === 'chamado-status'
                        ? <Button icon={CheckCircle2} loading={carregandoAcao} loadingText="Salvando..." onClick={salvarChamadoEditado}>Salvar chamado</Button>
                        : modal?.tipo === 'pagamento-detalhes'
                          ? null
                          : <Button icon={Search} loading={carregandoAcao} loadingText="Acessando..." onClick={confirmarImpersonacao}>Confirmar acesso</Button>}
          </div>
        </div>
      </Modal>
    </main>
  )
}
