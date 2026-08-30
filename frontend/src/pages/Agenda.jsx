import { CalendarPlus, RefreshCw } from 'lucide-react'
import { useContext, useEffect, useMemo, useState } from 'react'
import { RefreshContext } from '../context/RefreshContext.jsx'
import { appApi, empresaIdAtual } from '../api/appApi.js'
import Button from '../components/Button.jsx'
import Input from '../components/Input.jsx'
import Modal from '../components/Modal.jsx'
import AgendaCard from '../components/AgendaCard.jsx'
import Pagination from '../components/Pagination.jsx'
import BulkActionsToolbar from '../components/BulkActionsToolbar.jsx'
import BulkConfirmModal from '../components/BulkConfirmModal.jsx'
import { promocoesApi } from '../api/promocoesApi.js'
import { useAuth } from '../contexts/AuthContext.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { todayIso } from '../services/localStore.js'
import '../styles/agenda.css'

const PROFISSIONAL_AUTOMATICO_VALUE = 'atendimento-principal'
const AGENDA_TIMEZONE = 'America/Cuiaba'

function emitirToast(type, message) {
  window.dispatchEvent(new CustomEvent('gendaz:toast', { detail: { type, message } }))
}

const novoFormulario = {
  clienteId: '',
  servicoId: '',
  profissionalId: PROFISSIONAL_AUTOMATICO_VALUE,
  data: todayIso(),
  horaInicio: '11:00',
  status: 'PENDENTE',
  observações: 'Criado pelo painel.',
}

function limiteDataMaxima() {
  const data = new Date()
  data.setFullYear(data.getFullYear() + 2)
  return data.toISOString().slice(0, 10)
}

function agoraNoFuso(fuso) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: fuso,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(new Date()).reduce((acc, part) => {
    if (part.type !== 'literal') acc[part.type] = part.value
    return acc
  }, {})
}

function criarDataLocal(iso) {
  if (!iso) return null
  const [ano, mes, dia] = iso.split('-').map(Number)
  if (!ano || !mes || !dia) return null
  return new Date(ano, mes - 1, dia, 12, 0, 0, 0)
}

function formatarIsoLocal(data) {
  return data.toISOString().slice(0, 10)
}

function primeiroId(lista) {
  return Array.isArray(lista) ? (lista[0]?.id ?? '') : ''
}

function diaSemanaIso(data) {
  if (!data) return null
  const [ano, mes, dia] = String(data).split('-').map(Number)
  const local = ano && mes && dia ? new Date(ano, mes - 1, dia, 12) : null
  const dias = ['DOMINGO', 'SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO']
  return local ? dias[local.getDay()] : null
}

function trabalhaNaData(profissional, data) {
  const dia = diaSemanaIso(data)
  return !dia || (Array.isArray(profissional?.diasTrabalho) && profissional.diasTrabalho.includes(dia))
}

function profissionaisAtivos(lista) {
  return (lista || []).filter((item) => item?.status === 'ATIVO')
}

function montarFormularioInicial(dados) {
    const ativos = profissionaisAtivos(dados.profissionais).filter((item) => trabalhaNaData(item, todayIso()))

  return {
    ...novoFormulario,
    clienteId: primeiroId(dados.clientes),
    servicoId: primeiroId(dados.servicos),
    profissionalId: primeiroId(ativos) || PROFISSIONAL_AUTOMATICO_VALUE,
    data: todayIso(),
  }
}

export default function Agenda() {
  const [data, , { loading, reload }] = useLocalData('agenda')
  const { refreshTrigger } = useContext(RefreshContext)
  const { usuario, renovarAoRetomarAba } = useAuth()
  const servicosAtivos = (Array.isArray(data.servicos) ? data.servicos : []).filter((item) => item.status !== 'INATIVO')
  const profissionaisAtivosLista = useMemo(() => profissionaisAtivos(data.profissionais), [data.profissionais])
  const temProfissionais = profissionaisAtivosLista.length > 0
  const buscaAgendaPlaceholder = temProfissionais ? 'Cliente, serviço ou profissional' : 'Cliente ou serviço'
  const [dataFiltro, setDataFiltro] = useState('')
  const [profissionalId, setProfissionalId] = useState('todos')
  const [status, setStatus] = useState('todos')
  const [busca, setBusca] = useState('')
  const [modalCriar, setModalCriar] = useState(false)
  const [modalEditar, setModalEditar] = useState(false)
  const [form, setForm] = useState(novoFormulario)
  const [promocoes, setPromocoes] = useState([])
  const [edicao, setEdicao] = useState(null)
  const [erroCriar, setErroCriar] = useState('')
  const [erroEditar, setErroEditar] = useState('')
  const [erroAcao, setErroAcao] = useState('')
  const [salvandoCriar, setSalvandoCriar] = useState(false)
  const [salvandoEditar, setSalvandoEditar] = useState(false)
  const [acaoEmAndamento, setAcaoEmAndamento] = useState(null)
  const [confirmacao, setConfirmacao] = useState(null)
  const [confirmandoAcao, setConfirmandoAcao] = useState(false)
  const [finalizacaoPagamento, setFinalizacaoPagamento] = useState(null)
  const [formasPagamento, setFormasPagamento] = useState(null)
  const [parcelasCredito, setParcelasCredito] = useState(null)
  const [selecionando, setSelecionando] = useState(false)
  const [selecionados, setSelecionados] = useState([])
  const [bulkModal, setBulkModal] = useState(null)
  const [bulkExecutando, setBulkExecutando] = useState(false)
  const [recarregando, setRecarregando] = useState(false)
  const [pagina, setPagina] = useState(1)
  const itensPorPagina = 9
  const [horariosCriar, setHorariosCriar] = useState([])
  const [carregandoHorariosCriar, setCarregandoHorariosCriar] = useState(false)
  const [horariosEditar, setHorariosEditar] = useState([])
  const [carregandoHorariosEditar, setCarregandoHorariosEditar] = useState(false)
  const profissionaisCriacaoDisponiveis = useMemo(() => profissionaisAtivosLista.filter((item) => trabalhaNaData(item, form.data)), [profissionaisAtivosLista, form.data])
  const profissionaisEdicaoDisponiveis = useMemo(() => profissionaisAtivosLista.filter((item) => trabalhaNaData(item, edicao?.data)), [profissionaisAtivosLista, edicao?.data])

  useEffect(() => {
    reload(true)
  }, [refreshTrigger, reload])

  useEffect(() => {
    appApi.buscarFormasPagamento()
      .then(setFormasPagamento)
      .catch(() => setFormasPagamento({ pixAtivo: true, debitoAtivo: true, creditoAtivo: true, parceladoAtivo: false, dinheiroAtivo: true, maxParcelas: 12 }))
  }, [])

  const termoBusca = busca.trim().toLowerCase()

  const filtrosData = useMemo(() => {
    if (!dataFiltro) {
      return { inicio: null, fim: null }
    }
    return { inicio: dataFiltro, fim: dataFiltro }
  }, [dataFiltro])

  const filtrados = useMemo(() => (Array.isArray(data.agendamentos) ? data.agendamentos : []).filter((item) => {
    const matchesPeriodo = !filtrosData.inicio || !filtrosData.fim
      ? true
      : item.data >= filtrosData.inicio && item.data <= filtrosData.fim
    const matchesProfissional = profissionalId === 'todos' || item.profissionalId === Number(profissionalId)
    const matchesStatus = status === 'todos' || item.status === status
    const textoBusca = `${item.clienteNome || ''} ${item.servicoNome || ''} ${item.profissionalNome || ''}`.toLowerCase()
    const matchesBusca = !termoBusca || textoBusca.includes(termoBusca)
    return matchesPeriodo && matchesProfissional && matchesStatus && matchesBusca
  }), [data.agendamentos, filtrosData, profissionalId, status, termoBusca])

  const clientesPorId = useMemo(() => new Map((Array.isArray(data.clientes) ? data.clientes : []).map((cliente) => [cliente.id, cliente])), [data.clientes])
  const servicosPorId = useMemo(() => new Map((Array.isArray(data.servicos) ? data.servicos : []).map((servico) => [servico.id, servico])), [data.servicos])
  const profissionaisPorId = useMemo(() => new Map(profissionaisAtivosLista.map((profissional) => [profissional.id, profissional])), [profissionaisAtivosLista])

  const filtradosEnriquecidos = filtrados.map((item) => {
    const cliente = clientesPorId.get(item.clienteId)
    const servico = servicosPorId.get(item.servicoId)
    const profissional = profissionaisPorId.get(item.profissionalId)
    return {
      ...item,
      clienteNome: cliente ? cliente.nome : item.clienteNome || 'Cliente não encontrado',
      servicoNome: servico ? servico.nome : item.servicoNome || 'Serviço não encontrado',
      profissionalNome: profissional ? profissional.nome : item.profissionalNome || 'Profissional não encontrado',
    }
  })
  const agendamentosOrdenados = [...filtradosEnriquecidos].sort((a, b) => {
    if (a.data === b.data) {
      return String(b.horaInicio || '').localeCompare(String(a.horaInicio || ''))
    }
    return String(b.data || '').localeCompare(String(a.data || ''))
  })
  const totalPaginas = Math.max(1, Math.ceil(agendamentosOrdenados.length / itensPorPagina))
  const paginaAtual = Math.min(pagina, totalPaginas)
  const agendamentosPaginados = useMemo(() => agendamentosOrdenados.slice((paginaAtual - 1) * itensPorPagina, paginaAtual * itensPorPagina), [agendamentosOrdenados, paginaAtual])
  const selectedCount = selecionados.length

  useEffect(() => {
    if (!modalCriar) return
    promocoesApi.listar()
      .then((lista) => setPromocoes(Array.isArray(lista) ? lista : []))
      .catch(() => setPromocoes([]))

    const servicosAtivosAtuais = (Array.isArray(data.servicos) ? data.servicos : []).filter((item) => item.status !== 'INATIVO')
    const clientePadrao = primeiroId(data.clientes)
    const servicoPadrao = primeiroId(servicosAtivosAtuais)
    setForm((current) => {
      const profissionaisDoDia = profissionaisAtivosLista.filter((item) => trabalhaNaData(item, current.data))
      const profissionalPadrao = primeiroId(profissionaisDoDia) || PROFISSIONAL_AUTOMATICO_VALUE
      let atualizou = false
      const proximo = { ...current }

      if (!String(proximo.clienteId ?? '').trim() && clientePadrao) {
        proximo.clienteId = clientePadrao
        atualizou = true
      }

      if (!String(proximo.servicoId ?? '').trim() && servicoPadrao) {
        proximo.servicoId = servicoPadrao
        atualizou = true
      }

      if (temProfissionais) {
        if (!String(proximo.profissionalId ?? '').trim() || (proximo.profissionalId !== PROFISSIONAL_AUTOMATICO_VALUE && !profissionaisDoDia.some((item) => Number(item.id) === Number(proximo.profissionalId)))) {
          proximo.profissionalId = profissionalPadrao
          atualizou = true
        }
      } else if (proximo.profissionalId !== null) {
        proximo.profissionalId = null
        atualizou = true
      }

      return atualizou ? proximo : current
    })
  }, [data.clientes, data.servicos, modalCriar, profissionaisAtivosLista, temProfissionais])

  useEffect(() => {
    if (!modalCriar || !temProfissionais) return
    const atual = String(form.profissionalId ?? '')
    const valido = atual === PROFISSIONAL_AUTOMATICO_VALUE || profissionaisCriacaoDisponiveis.some((item) => String(item.id) === atual)
    if (!valido) {
      setForm((current) => ({ ...current, profissionalId: primeiroId(profissionaisCriacaoDisponiveis) || '' }))
      setErroCriar('Nenhum profissional disponível nesta data. Escolha outro dia.')
    }
  }, [form.profissionalId, modalCriar, profissionaisCriacaoDisponiveis, temProfissionais])

  useEffect(() => {
    if (!modalEditar || !edicao || !temProfissionais) return
    const atual = String(edicao.profissionalId ?? '')
    if (atual && !profissionaisEdicaoDisponiveis.some((item) => String(item.id) === atual)) {
      setEdicao((current) => ({ ...current, profissionalId: primeiroId(profissionaisEdicaoDisponiveis) || '' }))
      setErroEditar('Nenhum profissional disponível nesta data. Escolha outro dia.')
    }
  }, [edicao, modalEditar, profissionaisEdicaoDisponiveis, temProfissionais])

  async function buscarHorariosDisponiveisAgenda(profissionalId, servicoId, dataRef) {
    if (!servicoId || !dataRef) return []
    const empresaId = empresaIdAtual()
    if (!empresaId) return []
    const profissionalParam = (profissionalId == null || profissionalId === '' || profissionalId === PROFISSIONAL_AUTOMATICO_VALUE)
      ? null
      : Number(profissionalId)
    try {
      const resposta = await appApi.horariosDisponiveis(empresaId, profissionalParam, Number(servicoId), dataRef)
      return Array.isArray(resposta) ? resposta : []
    } catch {
      return []
    }
  }

  useEffect(() => {
    if (!modalCriar) {
      setHorariosCriar([])
      return
    }
    if (!form.servicoId || !form.data) {
      setHorariosCriar([])
      return
    }
    let ativo = true
    setCarregandoHorariosCriar(true)
    buscarHorariosDisponiveisAgenda(form.profissionalId, form.servicoId, form.data)
      .then((lista) => { if (ativo) setHorariosCriar(lista) })
      .catch(() => { if (ativo) setHorariosCriar([]) })
      .finally(() => { if (ativo) setCarregandoHorariosCriar(false) })
    return () => { ativo = false }
  }, [modalCriar, form.servicoId, form.profissionalId, form.data])

  useEffect(() => {
    if (!modalEditar || !edicao) {
      setHorariosEditar([])
      return
    }
    if (!edicao.servicoId || !edicao.data) {
      setHorariosEditar([])
      return
    }
    let ativo = true
    setCarregandoHorariosEditar(true)
    buscarHorariosDisponiveisAgenda(edicao.profissionalId, edicao.servicoId, edicao.data)
      .then((lista) => { if (ativo) setHorariosEditar(lista) })
      .catch(() => { if (ativo) setHorariosEditar([]) })
      .finally(() => { if (ativo) setCarregandoHorariosEditar(false) })
    return () => { ativo = false }
  }, [modalEditar, edicao])

  const promocoesAplicaveis = useMemo(() => {
    const servicoAtual = Number(form.servicoId)
    if (!servicoAtual) return []
    const agora = new Date()
    return (promocoes || []).filter((cupom) => {
      if (cupom.status !== 'ATIVO') return false
      if (cupom.dataFim && new Date(cupom.dataFim) < agora) return false
      if (cupom.quantidadeLimite != null && (cupom.quantidadeUsada ?? 0) >= cupom.quantidadeLimite) return false
      if (cupom.aplicarTodosServicos) return true
      return Array.isArray(cupom.servicos) && cupom.servicos.some((servico) => Number(servico.id) === servicoAtual)
    })
  }, [form.servicoId, promocoes])

  function limparSelecao() {
    setSelecionando(false)
    setSelecionados([])
    setBulkModal(null)
  }

  function alternarSelecionado(id) {
    setSelecionados((current) => {
      if (current.includes(id)) return current.filter((item) => item !== id)
      if (current.length >= 10) {
        setErroAcao('Você pode selecionar no máximo 10 itens por vez.')
        return current
      }
      setErroAcao('')
      return [...current, id]
    })
  }

  function abrirBulk(ação) {
    if (!selectedCount) {
      setErroAcao('Selecione pelo menos um item.')
      return
    }
    const configs = {
      FINALIZAR: ['Finalizar agendamentos', 'Tem certeza que deseja finalizar os agendamentos selecionados?', 'Finalizar', false],
      CANCELAR: ['Cancelar agendamentos', 'Tem certeza que deseja cancelar os agendamentos selecionados?', 'Cancelar', false],
      PENDENTE: ['Marcar como pendentes', 'Tem certeza que deseja marcar os agendamentos selecionados como pendentes?', 'Marcar como pendente', false],
      EXCLUIR: ['Excluir agendamentos', 'Tem certeza que deseja excluir os agendamentos selecionados? Essa ação não poderá ser desfeita.', 'Excluir', true],
    }
    const cfg = configs[ação]
    setBulkModal({ acao: ação, titulo: cfg[0], descrição: cfg[1], confirmLabel: cfg[2], danger: cfg[3] })
  }

  async function executarBulk() {
    if (!bulkModal || bulkExecutando) return
    setBulkExecutando(true)
    setErroAcao('')
    try {
      await appApi.acaoEmMassaAgendamentos(selecionados, bulkModal.acao)
      await reload(true)
      limparSelecao()
    } catch (error) {
      setErroAcao(error.response?.data?.mensagem || 'Não foi possível executar a ação em massa.')
    } finally {
      setBulkExecutando(false)
    }
  }

  function montarAgendamento(payload) {
    const profissionalSelecionado = String(payload.profissionalId || '').trim()
    return {
      clienteId: Number(payload.clienteId),
      servicoId: Number(payload.servicoId),
      profissionalId: profissionalSelecionado && profissionalSelecionado !== PROFISSIONAL_AUTOMATICO_VALUE
        ? Number(profissionalSelecionado)
        : null,
      data: payload.data,
      horaInicio: payload.horaInicio,
      cupomCodigo: payload.cupomCodigo || '',
      status: payload.status,
      observações: payload.observações,
    }
  }

  function validarAgendamento(payload) {
    const clienteId = String(payload.clienteId ?? '').trim()
    const servicoId = String(payload.servicoId ?? '').trim()
    const profissionalId = String(payload.profissionalId ?? '').trim()
    const data = String(payload.data ?? '').trim()
    const horaInicio = String(payload.horaInicio ?? '').trim()

    if (!clienteId || !servicoId) return 'Cliente e serviço são obrigatórios.'
    if (temProfissionais && !profissionalId) return 'Cliente, serviço e profissional são obrigatórios.'
    if (temProfissionais && profissionalId !== PROFISSIONAL_AUTOMATICO_VALUE) {
      const profissional = profissionaisAtivosLista.find((item) => Number(item.id) === Number(profissionalId))
      if (!profissional || !trabalhaNaData(profissional, data)) return 'Este profissional não atende no dia selecionado.'
    }
    const hoje = todayIso()
    if (!data || data < hoje || data > limiteDataMaxima()) return 'Data deve estar dentro dos próximos 2 anos e não pode ser no passado.'
    if (!horaInicio || horaInicio < '00:00' || horaInicio > '23:59') return 'Horário inválido.'
    if (data === hoje) {
      const partesAgora = agoraNoFuso(AGENDA_TIMEZONE)
      const horaAtual = `${partesAgora.hour || '00'}:${partesAgora.minute || '00'}`
      if (horaInicio < horaAtual) return 'Não é possível criar agendamento em horário que já passou.'
    }
    if ((payload.observações || '').length > 300) return 'Observações deve ter até 300 caracteres.'
    return ''
  }

  function existeConflito(payload, ignorarId = null) {
    return (Array.isArray(data.agendamentos) ? data.agendamentos : []).some((item) => (
      item.id !== ignorarId &&
      payload.profissionalId &&
      item.profissionalId === Number(payload.profissionalId) &&
      item.data === payload.data &&
      item.horaInicio === payload.horaInicio &&
      item.status !== 'CANCELADO'
    ))
  }

  function abrirCriacao() {
    setForm({
      ...montarFormularioInicial(data),
      servicoId: primeiroId(servicosAtivos),
      profissionalId: temProfissionais ? primeiroId(profissionaisAtivosLista.filter((item) => trabalhaNaData(item, todayIso()))) || PROFISSIONAL_AUTOMATICO_VALUE : null,

    })
    setPromocoes([])
    setErroCriar('')
    setModalCriar(true)
  }

  async function recarregar() {
    if (recarregando) return
    setRecarregando(true)
    try {
      await reload(true)
    } finally {
      setRecarregando(false)
    }
  }

  async function obterProfissionalParaAgendamento(profissionalSelecionado) {
    if (profissionalSelecionado && profissionalSelecionado !== PROFISSIONAL_AUTOMATICO_VALUE) {
      return Number(profissionalSelecionado)
    }
    return null
  }

  async function criarAgendamento(event) {
    event.preventDefault()
    if (salvandoCriar) return
    setErroCriar('')
    setSalvandoCriar(true)
    try {
      const erro = validarAgendamento(form)
      if (erro) {
        setErroCriar(erro)
        return
      }
      const profissionalIdFinal = await obterProfissionalParaAgendamento(form.profissionalId)
      const agendamento = montarAgendamento({ ...form, profissionalId: profissionalIdFinal })
      if (existeConflito(agendamento)) {
        setErroCriar('Já existe agendamento para este profissional neste horário.')
        return
      }
      await appApi.criarAgendamento(agendamento)
      setModalCriar(false)
      setForm(novoFormulario)
      reload(true).catch((error) => {
        console.warn('[agenda-debug] falha ao recarregar agenda após criar')
      })
    } catch (error) {
      const mensagemErro = error.response?.data?.mensagem || error.response?.data?.message || ''
      const mensagemFormatada = mensagemErro.includes('Cliente não encontrado') || mensagemErro.includes('Cliente não encontrado')
        ? 'Você precisa cadastrar o cliente primeiro. Vá em Clientes → Novo cliente.'
        : mensagemErro || 'Não foi possível criar o agendamento.'
      setErroCriar(mensagemFormatada)
    } finally {
      setSalvandoCriar(false)
    }
  }

  function abrirEdicao(agendamento) {
    setEdicao({
      id: agendamento.id,
      clienteId: agendamento.clienteId,
      servicoId: agendamento.servicoId,
      profissionalId: agendamento.profissionalId,
      data: agendamento.data,
      horaInicio: agendamento.horaInicio,
      status: agendamento.status,
      observações: agendamento.observações || '',
    })
    setErroEditar('')
    setModalEditar(true)
  }

  async function salvarEdicao(event) {
    event.preventDefault()
    if (salvandoEditar) return
    setErroEditar('')
    setSalvandoEditar(true)
    try {
      const agendamentoAtualizado = montarAgendamento(edicao)
      const erro = validarAgendamento(agendamentoAtualizado)
      if (erro) {
        setErroEditar(erro)
        return
      }
      if (existeConflito(agendamentoAtualizado, edicao.id)) {
        setErroEditar('Já existe agendamento para este profissional neste horário.')
        return
      }
      await appApi.atualizarAgendamento(edicao.id, agendamentoAtualizado)
      setModalEditar(false)
      setEdicao(null)
      reload(true).catch((error) => {
        console.warn('[agenda-debug] falha ao recarregar agenda após editar')
      })
    } catch (error) {
      const mensagemErro = error.response?.data?.mensagem || error.response?.data?.message || ''
      const mensagemFormatada = mensagemErro.includes('Cliente não encontrado') || mensagemErro.includes('Cliente não encontrado')
        ? 'Você precisa cadastrar o cliente primeiro. Vá em Clientes → Novo cliente.'
        : mensagemErro || 'Não foi possível salvar o agendamento.'
      setErroEditar(mensagemFormatada)
    } finally {
      setSalvandoEditar(false)
    }
  }

  async function cancelarAgendamento(id) {
    if (acaoEmAndamento) return
    setAcaoEmAndamento({ id, tipo: 'cancelar' })
    setErroAcao('')
    try {
      await appApi.cancelarAgendamento(id)
      setAcaoEmAndamento(null)
      reload(true).catch((error) => {
        console.error('[agenda-debug] erro ao recarregar agenda')
        setErroAcao(error?.response?.data?.mensagem || 'Erro ao recarregar a agenda.')
      })
    } catch (error) {
      setAcaoEmAndamento(null)
      setErroAcao(error.response?.data?.mensagem || 'Não foi possível cancelar o agendamento.')
    }
  }

  async function confirmarAgendamento(id) {
    if (acaoEmAndamento) return
    setAcaoEmAndamento({ id, tipo: 'confirmar' })
    setErroAcao('')
    try {
      await appApi.confirmarAgendamento(id)
      setAcaoEmAndamento(null)
      reload(true).catch((error) => {
        console.error('[agenda-debug] erro ao recarregar agenda')
        setErroAcao(error?.response?.data?.mensagem || 'Erro ao recarregar a agenda.')
      })
    } catch (error) {
      setAcaoEmAndamento(null)
      setErroAcao(error.response?.data?.mensagem || 'Não foi possível confirmar o agendamento.')
    }
  }

  async function excluirAgendamento(id) {
    if (acaoEmAndamento || confirmandoAcao) return
    setAcaoEmAndamento({ id, tipo: 'excluir' })
    setErroAcao('')
    try {
      await appApi.excluirAgendamento(id)
      setAcaoEmAndamento(null)
      reload(true).catch((error) => {
        console.error('[agenda-debug] erro ao recarregar agenda')
        setErroAcao(error?.response?.data?.mensagem || 'Erro ao recarregar a agenda.')
      })
    } catch (error) {
      const mensagem = String(error.response?.data?.mensagem || error.response?.data?.message || error.message || '')
      const jaNaoExiste = error.response?.status === 404 || mensagem.toLowerCase().includes('agendamento não encontrado') || mensagem.toLowerCase().includes('agendamento não encontrado')
      if (!jaNaoExiste) {
        setAcaoEmAndamento(null)
        setErroAcao(mensagem || 'Não foi possível excluir o agendamento.')
        return
      }
      setAcaoEmAndamento(null)
      reload(true).catch((error) => {
        console.error('[agenda-debug] erro ao recarregar agenda')
        setErroAcao(error?.response?.data?.mensagem || 'Erro ao recarregar a agenda.')
      })
    } finally {
      setConfirmacao(null)
      setConfirmandoAcao(false)
    }
  }

  async function iniciarAtendimento(agendamento) {
    if (acaoEmAndamento) return
    setAcaoEmAndamento({ id: agendamento.id, tipo: 'iniciar' })
    setErroAcao('')
    try {
      await renovarAoRetomarAba({ ignorarThrottle: true })
      await appApi.iniciarAgendamento(agendamento.id)
      setAcaoEmAndamento(null)
      reload(true).catch((error) => {
        console.error('[agenda-debug] erro ao recarregar agenda')
        setErroAcao(error?.response?.data?.mensagem || 'Erro ao recarregar a agenda.')
      })
    } catch (error) {
      setAcaoEmAndamento(null)
      setErroAcao(error?.message || error.response?.data?.mensagem || error.response?.data?.message || 'Não foi possível iniciar o atendimento.')
    }
  }

  async function pausarAtendimento(agendamento) {
    if (acaoEmAndamento) return
    setAcaoEmAndamento({ id: agendamento.id, tipo: 'pausar' })
    setErroAcao('')
    try {
      await renovarAoRetomarAba({ ignorarThrottle: true })
      await appApi.pausarAgendamento(agendamento.id)
      setAcaoEmAndamento(null)
      reload(true).catch((error) => {
        console.error('[agenda-debug] erro ao recarregar agenda')
        setErroAcao(error?.response?.data?.mensagem || 'Erro ao recarregar a agenda.')
      })
    } catch (error) {
      setAcaoEmAndamento(null)
      setErroAcao(error?.message || error.response?.data?.mensagem || error.response?.data?.message || 'Não foi possível pausar o atendimento.')
    }
  }

  const metodosFinalizacao = [
    formasPagamento?.pixAtivo && { label: 'Pix', metodoPagamento: 'PIX' },
    formasPagamento?.debitoAtivo && { label: 'Débito', metodoPagamento: 'DEBITO' },
    formasPagamento?.creditoAtivo && { label: 'Crédito', metodoPagamento: 'CREDITO' },
    formasPagamento?.dinheiroAtivo && { label: 'Dinheiro', metodoPagamento: 'DINHEIRO' },
  ].filter(Boolean)

  function selecionarPagamentoFinalizacao(metodoPagamento) {
    if (metodoPagamento === 'CREDITO' && formasPagamento?.parceladoAtivo) {
      setParcelasCredito({ contexto: 'agenda', metodoPagamento })
      return
    }
    finalizarAtendimentoDireto(finalizacaoPagamento, true, { metodoPagamento, parcelas: metodoPagamento === 'CREDITO' ? 1 : null })
  }

  async function finalizarAtendimentoDireto(agendamento, pagamentoRealizado = true, pagamento = {}) {
    if (acaoEmAndamento) return
    setAcaoEmAndamento({ id: agendamento.id, tipo: 'finalizar' })
    setErroAcao('')
    emitirToast('loading', pagamentoRealizado ? 'Pagamento sendo efetuado, aguarde...' : 'Atendimento finalizando, aguarde...')
    try {
      await renovarAoRetomarAba({ ignorarThrottle: true })
      await appApi.finalizarAgendamento(agendamento.id, pagamentoRealizado, pagamento)
      setAcaoEmAndamento(null)
      setFinalizacaoPagamento(null)
      emitirToast('success', 'Agendamento finalizado com sucesso.')
      reload(true).catch((error) => {
        console.error('[agenda-debug] erro ao recarregar agenda')
        setErroAcao(error?.response?.data?.mensagem || 'Erro ao recarregar a agenda.')
      })
    } catch (error) {
      setAcaoEmAndamento(null)
      const mensagem = error?.message || error.response?.data?.mensagem || error.response?.data?.message || 'Não foi possível finalizar o atendimento.'
      setErroAcao(mensagem)
      emitirToast('error', mensagem)
    }
  }

  return (
    <section className="page">
      <div className="page-title row-title agenda-header">
        <div>
          <span className="section-kicker">Agenda operacional</span>
          <h1>Agenda</h1>
          <p>Lista por data com filtros, criação, edição e correção de atendimentos.</p>
        </div>
        <div className="table-actions">
          <div className="agenda-search">
            <label className="agenda-search-label" htmlFor="agenda-busca">Buscar</label>
            <div className="agenda-search-field">
              <input
                id="agenda-busca"
                maxLength={80}
                placeholder={buscaAgendaPlaceholder}
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
              />
            </div>
            <div className="agenda-search-helper">
              <span className={busca.length >= 80 ? 'field-hint limit-reached' : 'field-hint'}>
                {busca.length >= 80
                  ? 'Limite de caracteres atingido.'
                  : temProfissionais
                    ? 'Filtre cliente, serviço ou profissional rapidamente.'
                    : 'Filtre cliente ou serviço rapidamente.'}
              </span>
              <strong>{busca.length}/80</strong>
            </div>
          </div>
          <Button variant="secondary" icon={RefreshCw} className="agenda-action-btn agenda-action-reload" onClick={recarregar} loading={recarregando} loadingText="Recarregando...">
            Recarregar
          </Button>
          <Button icon={CalendarPlus} className="agenda-action-btn agenda-action-new" onClick={abrirCriacao}>Novo agendamento</Button>
        </div>
      </div>

      {loading ? (
        <div className="space-y-3">
          <div className="h-12 animate-pulse rounded bg-gray-700" />
          <div className="grid gap-3 md:grid-cols-2">
            <div className="h-40 animate-pulse rounded bg-gray-700" />
            <div className="h-40 animate-pulse rounded bg-gray-700" />
            <div className="h-40 animate-pulse rounded bg-gray-700" />
            <div className="h-40 animate-pulse rounded bg-gray-700" />
          </div>
        </div>
      ) : (
        <>
      <div className="filters">
        <label className="field agenda-date-filter">
          <span>Filtrar por dia</span>
          <input
            type="date"
            value={dataFiltro}
            onChange={(e) => {
              setDataFiltro(e.target.value)
              setPagina(1)
            }}
          />
        </label>
        <select value={profissionalId} onChange={(e) => setProfissionalId(e.target.value)}>
                  <option value="todos">Todos os profissionais</option>
          {profissionaisAtivosLista.map((item) => <option key={item.id} value={item.id}>{item.nome}</option>)}
        </select>
        <select value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="todos">Todos os status</option>
          <option value="PENDENTE">Pendente</option>
          <option value="EM_ATENDIMENTO">Em atendimento</option>
          <option value="PAUSADO">Pausado</option>
          <option value="CANCELADO">Cancelado</option>
          <option value="FINALIZADO">Finalizado</option>
        </select>
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => {
            setDataFiltro('')
            setPagina(1)
          }}
        >
          Todos os dias
        </button>
      </div>

      <div className="mass-action-toolbar mass-action-toolbar-agenda">
        <BulkActionsToolbar
          selectionMode={selecionando}
          selectedCount={selectedCount}
          onToggleSelection={() => setSelecionando(true)}
          onClearSelection={limparSelecao}
          actions={[
            { label: 'Finalizar', onClick: () => abrirBulk('FINALIZAR') },
            { label: 'Cancelar', onClick: () => abrirBulk('CANCELAR') },
            { label: 'Pendente', onClick: () => abrirBulk('PENDENTE') },
            { label: 'Excluir', danger: true, onClick: () => abrirBulk('EXCLUIR') },
          ]}
        />
      </div>

      <div className="agenda-card-grid">
        {erroAcao && <p className="form-error" style={{ gridColumn: '1 / -1' }}>{erroAcao}</p>}
        {!agendamentosOrdenados.length && (
          <div className="agenda-card-empty">
            <strong>Nenhum agendamento encontrado.</strong>
            <p>Ajuste os filtros ou a busca para visualizar os compromissos desta empresa.</p>
          </div>
        )}
        {agendamentosPaginados.map((agendamento) => (
          <AgendaCard
            key={agendamento.id}
            agendamento={agendamento}
            selectionMode={selecionando}
            selected={selecionados.includes(agendamento.id)}
            onToggleSelection={alternarSelecionado}
            selectionDisabled={!selecionados.includes(agendamento.id) && selectedCount >= 10}
            acaoCarregando={acaoEmAndamento}
            onIniciar={(ag) => setConfirmacao({
              titulo: 'Iniciar atendimento',
              descrição: 'Tem certeza que deseja iniciar este atendimento?',
              ação: () => iniciarAtendimento(ag),
              acaoLabel: 'Iniciar',
            })}
            onPausar={(ag) => setConfirmacao({
              titulo: 'Pausar atendimento',
              descrição: 'Tem certeza que deseja pausar este atendimento?',
              ação: () => pausarAtendimento(ag),
              acaoLabel: 'Pausar',
            })}
            onFinalizar={(ag) => setFinalizacaoPagamento(ag)}
            onEditar={(ag) => abrirEdicao(ag)}
            onCancelar={(ag) => setConfirmacao({
              titulo: 'Cancelar agendamento',
              descrição: 'Tem certeza que deseja cancelar este agendamento?',
              ação: () => cancelarAgendamento(ag.id),
              acaoLabel: 'Cancelar',
            })}
            onExcluir={(ag) => setConfirmacao({
              titulo: 'Excluir agendamento',
              descrição: 'Tem certeza que deseja excluir este agendamento? Essa ação é permanente.',
              ação: () => excluirAgendamento(ag.id),
              acaoLabel: 'Excluir',
            })}
          />
        ))}
      </div>
      <Pagination page={paginaAtual} totalPages={totalPaginas} totalItems={agendamentosOrdenados.length} pageSize={itensPorPagina} onPageChange={setPagina} />

      <Modal title="Criar agendamento" open={modalCriar} onClose={() => setModalCriar(false)}>
        <form className="form-grid" onSubmit={criarAgendamento}>
          <label className="field"><span>Cliente</span><select value={form.clienteId} onChange={(e) => setForm({ ...form, clienteId: Number(e.target.value) })}>{(Array.isArray(data.clientes) ? data.clientes : []).map((item) => <option key={item.id} value={item.id}>{item.nome}</option>)}</select></label>
          <label className="field"><span>Serviço</span><select value={form.servicoId} onChange={(e) => setForm({ ...form, servicoId: Number(e.target.value), cupomCodigo: '' })}>{servicosAtivos.map((item) => <option key={item.id} value={item.id}>{item.nome}</option>)}</select></label>
          {temProfissionais && (
            <label className="field">
              <span>Profissional</span>
              <select
                value={form.profissionalId}
                onChange={(e) => setForm({
                  ...form,
                  profissionalId: e.target.value === PROFISSIONAL_AUTOMATICO_VALUE ? PROFISSIONAL_AUTOMATICO_VALUE : Number(e.target.value),
                  cupomCodigo: '',
                })}
              >
                {profissionaisCriacaoDisponiveis.map((item) => <option key={item.id} value={item.id}>{item.nome}</option>)}
              </select>
            </label>
          )}
          <Input label="Data" helper="Escolha uma data dentro dos próximos 2 anos." type="date" min={todayIso()} max={limiteDataMaxima()} value={form.data} onChange={(e) => setForm({ ...form, data: e.target.value })} />
          {temProfissionais && profissionaisCriacaoDisponiveis.length === 0 && <p className="form-error field-wide">Nenhum profissional disponível nesta data. Escolha outro dia.</p>}

          {horariosCriar.length > 0 ? (
            <label className="field">
              <span>Horário</span>
              <select value={form.horaInicio} onChange={(e) => setForm({ ...form, horaInicio: e.target.value })}>
                {form.horaInicio && !horariosCriar.includes(form.horaInicio) && (
                  <option value={form.horaInicio}>{form.horaInicio} (indisponível)</option>
                )}
                {horariosCriar.map((horario) => (
                  <option key={horario} value={horario}>{horario}</option>
                ))}
              </select>
            </label>
          ) : (
            <Input label="Hora" helper="Escolha o horário do agendamento." type="time" min="00:00" max="23:59" value={form.horaInicio} onChange={(e) => setForm({ ...form, horaInicio: e.target.value })} />
          )}
          {carregandoHorariosCriar && <small className="field-hint">Carregando horários disponíveis...</small>}
          <label className="field">
            <span>Adicionar cupom</span>
            <select value={form.cupomCodigo || ''} onChange={(e) => setForm({ ...form, cupomCodigo: e.target.value })}>
              <option value="">Nenhum cupom</option>
              {promocoesAplicaveis.map((cupom) => (
                <option key={cupom.id} value={cupom.codigo}>
                  {cupom.codigo} - {cupom.descrição}
                </option>
              ))}
            </select>
          </label>
          <label className="field field-wide"><span>Observações</span><textarea maxLength={300} value={form.observações} onChange={(e) => setForm({ ...form, observações: e.target.value })} /><small className={form.observações.length >= 300 ? 'field-hint limit-reached' : 'field-hint'}>{form.observações.length >= 300 ? 'Limite de caracteres atingido.' : 'Use uma observação curta.'}<strong>{form.observações.length}/300</strong></small></label>
          {erroCriar && <p className="form-error field-wide">{erroCriar}</p>}
          <Button type="submit" loading={salvandoCriar} loadingText="Salvando...">Salvar</Button>
        </form>
      </Modal>

      <Modal title="Editar agendamento" open={modalEditar} onClose={() => setModalEditar(false)}>
        {edicao && (
          <form className="form-grid" onSubmit={salvarEdicao}>
            <label className="field"><span>Cliente</span><select value={edicao.clienteId} onChange={(e) => setEdicao({ ...edicao, clienteId: Number(e.target.value) })}>{(Array.isArray(data.clientes) ? data.clientes : []).map((item) => <option key={item.id} value={item.id}>{item.nome}</option>)}</select></label>
            <label className="field"><span>Serviço</span><select value={edicao.servicoId} onChange={(e) => setEdicao({ ...edicao, servicoId: Number(e.target.value) })}>{(Array.isArray(data.servicos) ? data.servicos : []).map((item) => <option key={item.id} value={item.id}>{item.nome}</option>)}</select></label>
            {temProfissionais && (
              <label className="field"><span>Profissional</span><select value={edicao.profissionalId} onChange={(e) => setEdicao({ ...edicao, profissionalId: Number(e.target.value) })}>{profissionaisEdicaoDisponiveis.map((item) => <option key={item.id} value={item.id}>{item.nome}</option>)}</select></label>
            )}
            <label className="field"><span>Status</span><select value={edicao.status} onChange={(e) => setEdicao({ ...edicao, status: e.target.value })}><option value="PENDENTE">Pendente</option><option value="CONFIRMADO">Confirmado</option><option value="EM_ATENDIMENTO">Em atendimento</option><option value="PAUSADO">Pausado</option><option value="CANCELADO">Cancelado</option><option value="FINALIZADO">Finalizado</option></select></label>
            <Input label="Data" helper="Escolha uma data dentro dos próximos 2 anos." type="date" min={todayIso()} max={limiteDataMaxima()} value={edicao.data} onChange={(e) => setEdicao({ ...edicao, data: e.target.value })} />
            {temProfissionais && profissionaisEdicaoDisponiveis.length === 0 && <p className="form-error field-wide">Nenhum profissional disponível nesta data. Escolha outro dia.</p>}
            {horariosEditar.length > 0 ? (
              <label className="field">
                <span>Horário</span>
                <select value={edicao.horaInicio} onChange={(e) => setEdicao({ ...edicao, horaInicio: e.target.value })}>
                  {edicao.horaInicio && !horariosEditar.includes(edicao.horaInicio) && (
                    <option value={edicao.horaInicio}>{edicao.horaInicio} (indisponível)</option>
                  )}
                  {horariosEditar.map((horario) => (
                    <option key={horario} value={horario}>{horario}</option>
                  ))}
                </select>
              </label>
            ) : (
              <Input label="Hora" helper="Escolha o horário do agendamento." type="time" min="00:00" max="23:59" value={edicao.horaInicio} onChange={(e) => setEdicao({ ...edicao, horaInicio: e.target.value })} />
            )}
            {carregandoHorariosEditar && <small className="field-hint">Carregando horários disponíveis...</small>}
            <label className="field field-wide"><span>Observações</span><textarea maxLength={300} value={edicao.observações} onChange={(e) => setEdicao({ ...edicao, observações: e.target.value })} /><small className={edicao.observações.length >= 300 ? 'field-hint limit-reached' : 'field-hint'}>{edicao.observações.length >= 300 ? 'Limite de caracteres atingido.' : 'Use uma observação curta.'}<strong>{edicao.observações.length}/300</strong></small></label>
            {erroEditar && <p className="form-error field-wide">{erroEditar}</p>}
            <Button type="submit" loading={salvandoEditar} loadingText="Salvando...">Salvar correções</Button>
          </form>
        )}
      </Modal>
      <Modal title="Finalizar atendimento" open={Boolean(finalizacaoPagamento)} onClose={() => { setFinalizacaoPagamento(null); setParcelasCredito(null) }}>
        <div className="form-grid single">
          <p className="panel-description">Como o cliente realizou o pagamento?</p>
          {!parcelasCredito ? (
            <div className="payment-methods">
              {metodosFinalizacao.map((metodo) => (
                <button
                  key={metodo.metodoPagamento}
                  type="button"
                  disabled={confirmandoAcao || acaoEmAndamento?.id === finalizacaoPagamento?.id}
                  onClick={() => selecionarPagamentoFinalizacao(metodo.metodoPagamento)}
                >
                  {metodo.label}
                </button>
              ))}
              <button
                type="button"
                disabled={confirmandoAcao || acaoEmAndamento?.id === finalizacaoPagamento?.id}
                onClick={() => finalizarAtendimentoDireto(finalizacaoPagamento, false)}
              >
                Não foi pago
              </button>
            </div>
          ) : (
            <div className="payment-methods">
              {Array.from({ length: formasPagamento?.maxParcelas || 12 }, (_, index) => index + 1).map((parcela) => (
                <button
                  key={parcela}
                  type="button"
                  disabled={confirmandoAcao || acaoEmAndamento?.id === finalizacaoPagamento?.id}
                  onClick={() => finalizarAtendimentoDireto(finalizacaoPagamento, true, { metodoPagamento: 'CREDITO', parcelas: parcela })}
                >
                  {parcela}x
                </button>
              ))}
            </div>
          )}
          <div className="table-actions" style={{ justifyContent: 'flex-end' }}>
            <Button variant="secondary" type="button" onClick={() => parcelasCredito ? setParcelasCredito(null) : setFinalizacaoPagamento(null)}>Voltar</Button>
          </div>
        </div>
      </Modal>
      <Modal title={confirmacao?.titulo || 'Confirmar ação'} open={Boolean(confirmacao)} onClose={() => { setConfirmacao(null); setConfirmandoAcao(false) }}>
        <div className="form-grid">
          <p className="panel-description">{confirmacao?.descrição}</p>
          <div className="table-actions" style={{ justifyContent: 'flex-end' }}>
            <Button variant="secondary" type="button" onClick={() => setConfirmacao(null)}>Cancelar</Button>
            <Button
              type="button"
              disabled={confirmandoAcao}
              onClick={async () => {
                if (confirmandoAcao) return
                setConfirmandoAcao(true)
                const ação = confirmacao?.ação
                setConfirmacao(null)
                try {
                  if (ação) await ação()
                } finally {
                  setConfirmandoAcao(false)
                }
              }}
            >
              {confirmacao?.acaoLabel || 'Confirmar'}
            </Button>
          </div>
        </div>
      </Modal>
      <BulkConfirmModal
        open={Boolean(bulkModal)}
        title={bulkModal?.titulo || 'Confirmar ação'}
        description={bulkModal?.descrição || ''}
        confirmLabel={bulkModal?.confirmLabel || 'Confirmar'}
        danger={Boolean(bulkModal?.danger)}
        loading={bulkExecutando}
        onCancel={() => setBulkModal(null)}
        onConfirm={executarBulk}
      />
        </>
      )}
    </section>
  )
}




