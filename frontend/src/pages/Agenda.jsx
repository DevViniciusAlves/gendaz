import { CalendarPlus, Check, Pencil, Power, RefreshCw, Trash } from 'lucide-react'
import { useMemo, useState } from 'react'
import { appApi } from '../api/appApi.js'
import Button from '../components/Button.jsx'
import Input from '../components/Input.jsx'
import Modal from '../components/Modal.jsx'
import ScheduleCard from '../components/ScheduleCard.jsx'
import ActionMenu from '../components/ActionMenu.jsx'
import Pagination from '../components/Pagination.jsx'
import BulkActionsToolbar from '../components/BulkActionsToolbar.jsx'
import BulkConfirmModal from '../components/BulkConfirmModal.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { todayIso } from '../services/localStore.js'

const PROFISSIONAL_AUTOMATICO_VALUE = 'atendimento-principal'
const AGENDA_TIMEZONE = 'America/Cuiaba'

const novoFormulario = {
  clienteId: '',
  servicoId: '',
  profissionalId: PROFISSIONAL_AUTOMATICO_VALUE,
  data: todayIso(),
  horaInicio: '11:00',
  status: 'PENDENTE',
  observacoes: 'Criado pelo painel.',
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

function inicioSemanaIso(iso) {
  const data = criarDataLocal(iso)
  if (!data) return iso
  const diaSemana = data.getDay() || 7
  data.setDate(data.getDate() - diaSemana + 1)
  return formatarIsoLocal(data)
}

function fimSemanaIso(iso) {
  const data = criarDataLocal(iso)
  if (!data) return iso
  const diaSemana = data.getDay() || 7
  data.setDate(data.getDate() + (7 - diaSemana))
  return formatarIsoLocal(data)
}

function inicioMesIso(iso) {
  const data = criarDataLocal(iso)
  if (!data) return iso
  data.setDate(1)
  return formatarIsoLocal(data)
}

function fimMesIso(iso) {
  const data = criarDataLocal(iso)
  if (!data) return iso
  data.setMonth(data.getMonth() + 1, 0)
  return formatarIsoLocal(data)
}

function adicionarDiasIso(iso, dias) {
  const data = criarDataLocal(iso)
  if (!data) return iso
  data.setDate(data.getDate() + dias)
  return formatarIsoLocal(data)
}

function primeiroId(lista) {
  return lista[0]?.id || ''
}

function montarFormularioInicial(dados) {
  return {
    ...novoFormulario,
    clienteId: primeiroId(dados.clientes),
    servicoId: primeiroId(dados.servicos),
    profissionalId: primeiroId(dados.profissionais) || PROFISSIONAL_AUTOMATICO_VALUE,
    data: todayIso(),
  }
}

export default function Agenda() {
  const [data, , { loading, reload }] = useLocalData('agenda')
  const { usuario } = useAuth()
  const servicosAtivos = data.servicos.filter((item) => item.status !== 'INATIVO')
  const planoEhPro = usuario?.plano === 'PRO'
  const temProfissionais = planoEhPro && data.profissionais.length > 0
  const buscaAgendaPlaceholder = temProfissionais ? 'Cliente, serviço ou profissional' : 'Cliente ou serviço'
  const hoje = todayIso()
  const [filtroPeriodo, setFiltroPeriodo] = useState('TODOS')
  const [dataFiltro, setDataFiltro] = useState(hoje)
  const [profissionalId, setProfissionalId] = useState('todos')
  const [status, setStatus] = useState('todos')
  const [busca, setBusca] = useState('')
  const [modalCriar, setModalCriar] = useState(false)
  const [modalEditar, setModalEditar] = useState(false)
  const [form, setForm] = useState(novoFormulario)
  const [edicao, setEdicao] = useState(null)
  const [erroCriar, setErroCriar] = useState('')
  const [erroEditar, setErroEditar] = useState('')
  const [erroAcao, setErroAcao] = useState('')
  const [salvandoCriar, setSalvandoCriar] = useState(false)
  const [salvandoEditar, setSalvandoEditar] = useState(false)
  const [acaoId, setAcaoId] = useState(null)
  const [confirmacao, setConfirmacao] = useState(null)
  const [confirmandoAcao, setConfirmandoAcao] = useState(false)
  const [selecionando, setSelecionando] = useState(false)
  const [selecionados, setSelecionados] = useState([])
  const [bulkModal, setBulkModal] = useState(null)
  const [bulkExecutando, setBulkExecutando] = useState(false)
  const [recarregando, setRecarregando] = useState(false)
  const [pagina, setPagina] = useState(1)
  const itensPorPagina = 12

  const termoBusca = busca.trim().toLowerCase()

  const filtrosData = useMemo(() => {
    if (filtroPeriodo === 'HOJE') {
      return { inicio: hoje, fim: hoje }
    }
    if (filtroPeriodo === 'AMANHA') {
      const amanha = adicionarDiasIso(hoje, 1)
      return { inicio: amanha, fim: amanha }
    }
    if (filtroPeriodo === 'SEMANA') {
      return { inicio: inicioSemanaIso(hoje), fim: fimSemanaIso(hoje) }
    }
    if (filtroPeriodo === 'MES') {
      return { inicio: inicioMesIso(hoje), fim: fimMesIso(hoje) }
    }
    if (filtroPeriodo === 'DATA') {
      return { inicio: dataFiltro, fim: dataFiltro }
    }
    return { inicio: null, fim: null }
  }, [dataFiltro, filtroPeriodo, hoje])

  const filtrados = useMemo(() => data.agendamentos.filter((item) => {
    const matchesPeriodo = !filtrosData.inicio || !filtrosData.fim
      ? true
      : item.data >= filtrosData.inicio && item.data <= filtrosData.fim
    const matchesProfissional = profissionalId === 'todos' || item.profissionalId === Number(profissionalId)
    const matchesStatus = status === 'todos' || item.status === status
    const textoBusca = `${item.clienteNome || ''} ${item.servicoNome || ''} ${item.profissionalNome || ''}`.toLowerCase()
    const matchesBusca = !termoBusca || textoBusca.includes(termoBusca)
    return matchesPeriodo && matchesProfissional && matchesStatus && matchesBusca
  }), [data.agendamentos, filtrosData, profissionalId, status, termoBusca])

  const clientesPorId = useMemo(() => new Map(data.clientes.map((cliente) => [cliente.id, cliente])), [data.clientes])
  const servicosPorId = useMemo(() => new Map(data.servicos.map((servico) => [servico.id, servico])), [data.servicos])
  const profissionaisPorId = useMemo(() => new Map(data.profissionais.map((profissional) => [profissional.id, profissional])), [data.profissionais])

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

  function abrirBulk(acao) {
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
    const cfg = configs[acao]
    setBulkModal({ acao, titulo: cfg[0], descricao: cfg[1], confirmLabel: cfg[2], danger: cfg[3] })
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

  console.log('[agenda-debug] agendamentos carregados', data.agendamentos)

  function montarAgendamento(payload) {
    return {
      clienteId: Number(payload.clienteId),
      servicoId: Number(payload.servicoId),
      profissionalId: payload.profissionalId ? Number(payload.profissionalId) : null,
      data: payload.data,
      horaInicio: payload.horaInicio,
      status: payload.status,
      observacoes: payload.observacoes,
    }
  }

  function validarAgendamento(payload) {
    if (!payload.clienteId || !payload.servicoId) return 'Cliente e serviço são obrigatórios.'
    if (temProfissionais && !payload.profissionalId) return 'Cliente, serviço e profissional são obrigatórios.'
    const hoje = todayIso()
    if (!payload.data || payload.data < hoje || payload.data > limiteDataMaxima()) return 'Data deve estar dentro dos próximos 2 anos e não pode ser no passado.'
    if (!payload.horaInicio || payload.horaInicio < '06:00' || payload.horaInicio > '22:59') return 'Horário deve estar entre 06:00 e 23:00.'
    if (payload.data === hoje) {
      const partesAgora = agoraNoFuso(AGENDA_TIMEZONE)
      const horaAtual = `${partesAgora.hour || '00'}:${partesAgora.minute || '00'}`
      if (payload.horaInicio < horaAtual) return 'Não é possível criar agendamento em horário que já passou.'
    }
    if ((payload.observacoes || '').length > 300) return 'Observações deve ter até 300 caracteres.'
    return ''
  }

  function existeConflito(payload, ignorarId = null) {
    return data.agendamentos.some((item) => (
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
      profissionalId: temProfissionais ? primeiroId(data.profissionais) || PROFISSIONAL_AUTOMATICO_VALUE : null,
    })
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
      await reload(true)
      setModalCriar(false)
      setForm(novoFormulario)
    } catch (error) {
      setErroCriar(error.response?.data?.mensagem || 'Não foi possível criar o agendamento.')
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
      observacoes: agendamento.observacoes || '',
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
      await reload(true)
      setModalEditar(false)
      setEdicao(null)
    } catch (error) {
      setErroEditar(error.response?.data?.mensagem || 'Não foi possível salvar o agendamento.')
    } finally {
      setSalvandoEditar(false)
    }
  }

  async function finalizarAtendimento(id) {
    if (acaoId) return
    setAcaoId(id)
    setErroAcao('')
    try {
      await appApi.finalizarAgendamento(id)
      await reload(true)
    } catch (error) {
      setErroAcao(error.response?.data?.mensagem || 'Não foi possível finalizar o atendimento.')
    } finally {
      setAcaoId(null)
    }
  }

  async function cancelarAgendamento(id) {
    if (acaoId) return
    setAcaoId(id)
    setErroAcao('')
    try {
      await appApi.cancelarAgendamento(id)
      await reload(true)
    } catch (error) {
      setErroAcao(error.response?.data?.mensagem || 'Não foi possível cancelar o agendamento.')
    } finally {
      setAcaoId(null)
    }
  }

  async function confirmarAgendamento(id) {
    if (acaoId) return
    setAcaoId(id)
    setErroAcao('')
    try {
      await appApi.confirmarAgendamento(id)
      await reload(true)
    } catch (error) {
      setErroAcao(error.response?.data?.mensagem || 'Não foi possível confirmar o agendamento.')
    } finally {
      setAcaoId(null)
    }
  }

  async function excluirAgendamento(id) {
    if (acaoId || confirmandoAcao) return
    setAcaoId(id)
    setErroAcao('')
    try {
      await appApi.excluirAgendamento(id)
    } catch (error) {
      const mensagem = String(error.response?.data?.mensagem || error.response?.data?.message || error.message || '')
      const jaNaoExiste = error.response?.status === 404 || mensagem.toLowerCase().includes('agendamento nao encontrado') || mensagem.toLowerCase().includes('agendamento não encontrado')
      if (!jaNaoExiste) {
        setErroAcao(mensagem || 'Não foi possível excluir o agendamento.')
        return
      }
    } finally {
      await reload(true)
      setConfirmacao(null)
      setAcaoId(null)
      setConfirmandoAcao(false)
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
          <Button variant="secondary" icon={RefreshCw} className="agenda-action-btn agenda-action-reload" onClick={recarregar} disabled={recarregando}>
            {recarregando ? 'Recarregando...' : 'Recarregar'}
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
        <select
          value={filtroPeriodo}
          onChange={(e) => {
            const valor = e.target.value
            setFiltroPeriodo(valor)
            if (valor === 'DATA') {
              setDataFiltro(hoje)
            }
          }}
        >
          <option value="TODOS">Todos os agendamentos</option>
          <option value="HOJE">Hoje</option>
          <option value="AMANHA">Amanhã</option>
          <option value="SEMANA">Esta semana</option>
          <option value="MES">Este mês</option>
          <option value="DATA">Data específica</option>
        </select>
        {filtroPeriodo === 'DATA' && (
          <input type="date" min={todayIso()} max={limiteDataMaxima()} value={dataFiltro} onChange={(e) => setDataFiltro(e.target.value)} />
        )}
        <select value={profissionalId} onChange={(e) => setProfissionalId(e.target.value)}>
          <option value="todos">Todos os profissionais</option>
          {data.profissionais.map((item) => <option key={item.id} value={item.id}>{item.nome}</option>)}
        </select>
        <select value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="todos">Todos os status</option>
          <option value="PENDENTE">Pendente</option>
          <option value="CONFIRMADO">Confirmado</option>
          <option value="CANCELADO">Cancelado</option>
          <option value="FINALIZADO">Finalizado</option>
        </select>
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => {
            setFiltroPeriodo('TODOS')
            setDataFiltro(hoje)
          }}
        >
          Todos
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

      <div className="schedule-grid">
        {erroAcao && <p className="form-error field-wide">{erroAcao}</p>}
        {!agendamentosOrdenados.length && (
          <div className="empty-state field-wide agenda-empty-state">
            <strong>Nenhum agendamento encontrado.</strong>
            <p>Ajuste os filtros ou a busca para visualizar os compromissos desta empresa.</p>
          </div>
        )}
        {agendamentosPaginados.map((agendamento) => (
          <ScheduleCard
            key={agendamento.id}
            agendamento={agendamento}
            leadingControl={selecionando ? (
              <input
                type="checkbox"
                checked={selecionados.includes(agendamento.id)}
                onChange={() => alternarSelecionado(agendamento.id)}
                disabled={!selecionados.includes(agendamento.id) && selectedCount >= 10}
                aria-label={`Selecionar agendamento ${agendamento.id}`}
              />
            ) : null}
          >
            <button
              className="btn btn-secondary btn-action-card"
              onClick={() => finalizarAtendimento(agendamento.id)}
              disabled={agendamento.status === 'FINALIZADO' || agendamento.status === 'CANCELADO'}
            >
              <Check size={14} />
              Finalizar
            </button>
            <ActionMenu
              actions={[
                { label: 'Editar', icon: Pencil, onClick: () => abrirEdicao(agendamento) },
                {
                  label: agendamento.status === 'CANCELADO' ? 'Ativar' : 'Desativar',
                  icon: Power,
                  onClick: () => setConfirmacao({
                    titulo: agendamento.status === 'CANCELADO' ? 'Ativar agendamento' : 'Cancelar agendamento',
                    descricao: agendamento.status === 'CANCELADO'
                      ? 'Deseja reativar este agendamento?'
                      : 'Tem certeza que deseja cancelar este agendamento?',
                    acao: () => agendamento.status === 'CANCELADO'
                      ? confirmarAgendamento(agendamento.id)
                      : cancelarAgendamento(agendamento.id),
                    acaoLabel: agendamento.status === 'CANCELADO' ? 'Ativar' : 'Cancelar',
                  }),
                  disabled: agendamento.status === 'FINALIZADO'
                },
                { label: 'Excluir', icon: Trash, danger: true, onClick: () => setConfirmacao({
                  titulo: 'Excluir agendamento',
                  descricao: 'Tem certeza que deseja excluir este agendamento? Essa ação é permanente e não terá como retornar.',
                  acao: () => excluirAgendamento(agendamento.id),
                  acaoLabel: 'Excluir',
                }) },
              ]}
            />
          </ScheduleCard>
        ))}
      </div>
      <Pagination page={paginaAtual} totalPages={totalPaginas} totalItems={agendamentosOrdenados.length} pageSize={itensPorPagina} onPageChange={setPagina} />

      <Modal title="Criar agendamento" open={modalCriar} onClose={() => setModalCriar(false)}>
        <form className="form-grid" onSubmit={criarAgendamento}>
          <label className="field"><span>Cliente</span><select value={form.clienteId} onChange={(e) => setForm({ ...form, clienteId: Number(e.target.value) })}>{data.clientes.map((item) => <option key={item.id} value={item.id}>{item.nome}</option>)}</select></label>
          <label className="field"><span>Serviço</span><select value={form.servicoId} onChange={(e) => setForm({ ...form, servicoId: Number(e.target.value) })}>{servicosAtivos.map((item) => <option key={item.id} value={item.id}>{item.nome}</option>)}</select></label>
          {temProfissionais && (
            <label className="field">
              <span>Profissional</span>
              <select
                value={form.profissionalId}
                onChange={(e) => setForm({
                  ...form,
                  profissionalId: e.target.value === PROFISSIONAL_AUTOMATICO_VALUE ? PROFISSIONAL_AUTOMATICO_VALUE : Number(e.target.value),
                })}
              >
                {data.profissionais.map((item) => <option key={item.id} value={item.id}>{item.nome}</option>)}
              </select>
            </label>
          )}
          <Input label="Data" helper="Escolha uma data dentro dos próximos 2 anos." type="date" min={todayIso()} max={limiteDataMaxima()} value={form.data} onChange={(e) => setForm({ ...form, data: e.target.value })} />
          <Input label="Hora" type="time" min="06:00" max="22:59" value={form.horaInicio} onChange={(e) => setForm({ ...form, horaInicio: e.target.value })} />
          <label className="field field-wide"><span>Observações</span><textarea maxLength={300} value={form.observacoes} onChange={(e) => setForm({ ...form, observacoes: e.target.value })} /><small className={form.observacoes.length >= 300 ? 'field-hint limit-reached' : 'field-hint'}>{form.observacoes.length >= 300 ? 'Limite de caracteres atingido.' : 'Use uma observação curta.'}<strong>{form.observacoes.length}/300</strong></small></label>
          {erroCriar && <p className="form-error field-wide">{erroCriar}</p>}
          <Button type="submit" disabled={salvandoCriar}>{salvandoCriar ? 'Salvando...' : 'Salvar'}</Button>
        </form>
      </Modal>

      <Modal title="Editar agendamento" open={modalEditar} onClose={() => setModalEditar(false)}>
        {edicao && (
          <form className="form-grid" onSubmit={salvarEdicao}>
            <label className="field"><span>Cliente</span><select value={edicao.clienteId} onChange={(e) => setEdicao({ ...edicao, clienteId: Number(e.target.value) })}>{data.clientes.map((item) => <option key={item.id} value={item.id}>{item.nome}</option>)}</select></label>
            <label className="field"><span>Serviço</span><select value={edicao.servicoId} onChange={(e) => setEdicao({ ...edicao, servicoId: Number(e.target.value) })}>{data.servicos.map((item) => <option key={item.id} value={item.id}>{item.nome}</option>)}</select></label>
            {temProfissionais && (
              <label className="field"><span>Profissional</span><select value={edicao.profissionalId} onChange={(e) => setEdicao({ ...edicao, profissionalId: Number(e.target.value) })}>{data.profissionais.map((item) => <option key={item.id} value={item.id}>{item.nome}</option>)}</select></label>
            )}
            <label className="field"><span>Status</span><select value={edicao.status} onChange={(e) => setEdicao({ ...edicao, status: e.target.value })}><option value="PENDENTE">Pendente</option><option value="CONFIRMADO">Confirmado</option><option value="CANCELADO">Cancelado</option><option value="FINALIZADO">Finalizado</option></select></label>
            <Input label="Data" helper="Escolha uma data dentro dos próximos 2 anos." type="date" min={todayIso()} max={limiteDataMaxima()} value={edicao.data} onChange={(e) => setEdicao({ ...edicao, data: e.target.value })} />
            <Input label="Hora" helper="Horário permitido: 06:00 a 23:00." type="time" min="06:00" max="22:59" value={edicao.horaInicio} onChange={(e) => setEdicao({ ...edicao, horaInicio: e.target.value })} />
            <label className="field field-wide"><span>Observações</span><textarea maxLength={300} value={edicao.observacoes} onChange={(e) => setEdicao({ ...edicao, observacoes: e.target.value })} /><small className={edicao.observacoes.length >= 300 ? 'field-hint limit-reached' : 'field-hint'}>{edicao.observacoes.length >= 300 ? 'Limite de caracteres atingido.' : 'Use uma observação curta.'}<strong>{edicao.observacoes.length}/300</strong></small></label>
            {erroEditar && <p className="form-error field-wide">{erroEditar}</p>}
            <Button type="submit" disabled={salvandoEditar}>{salvandoEditar ? 'Salvando...' : 'Salvar correções'}</Button>
          </form>
        )}
      </Modal>
      <Modal title={confirmacao?.titulo || 'Confirmar ação'} open={Boolean(confirmacao)} onClose={() => { setConfirmacao(null); setConfirmandoAcao(false) }}>
        <div className="form-grid">
          <p className="panel-description">{confirmacao?.descricao}</p>
          <div className="table-actions" style={{ justifyContent: 'flex-end' }}>
            <Button variant="secondary" type="button" onClick={() => setConfirmacao(null)}>Cancelar</Button>
            <Button
              type="button"
              disabled={confirmandoAcao}
              onClick={async () => {
                if (confirmandoAcao) return
                setConfirmandoAcao(true)
                const acao = confirmacao?.acao
                setConfirmacao(null)
                if (acao) await acao()
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
        description={bulkModal?.descricao || ''}
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
