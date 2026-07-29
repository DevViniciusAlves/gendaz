import { Check, RefreshCw, Trash, X } from 'lucide-react'
import { useContext, useEffect, useMemo, useState } from 'react'
import { RefreshContext } from '../context/RefreshContext.jsx'
import { appApi } from '../api/appApi.js'
import ActionMenu from '../components/ActionMenu.jsx'
import BulkActionsToolbar from '../components/BulkActionsToolbar.jsx'
import BulkConfirmModal from '../components/BulkConfirmModal.jsx'
import Button from '../components/Button.jsx'
import DashboardCard from '../components/DashboardCard.jsx'
import Pagination from '../components/Pagination.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import Table from '../components/Table.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { usePendentes } from '../hooks/usePendentes.js'
import { currency, todayIso } from '../services/localStore.js'

const STATUS_CONFIRMADO = new Set(['PAGO', 'PAGA', 'CONFIRMADO', 'CONFIRMADA', 'APROVADO', 'APPROVED', 'PAID', 'PAYMENT_APPROVED', 'PURCHASE_APPROVED'])

function mesReferenciaAtual() {
  return todayIso().slice(0, 7)
}

function valorTextoPagamento(pagamento) {
  return String(
    pagamento?.dataPagamento
    || pagamento?.pagoEm
    || pagamento?.createdAt
    || pagamento?.data
    || pagamento?.updatedAt
    || '',
  )
}

function pertenceAoMes(pagamento, mes) {
  return valorTextoPagamento(pagamento).startsWith(mes)
}

function ordenarMaisRecente(a, b) {
  const dataA = valorTextoPagamento(a)
  const dataB = valorTextoPagamento(b)
  if (dataA !== dataB) return dataB.localeCompare(dataA)
  return Number(b?.id || 0) - Number(a?.id || 0)
}

function statusSimples(statusAtual) {
  return ['PAGO', 'PAYMENT_APPROVED'].includes(statusAtual) ? 'APROVADO' : 'PENDENTE'
}

function metodoLegivel(metodoAtual) {
  if (metodoAtual === 'PIX_AUTO') return 'PIX automático'
  if (metodoAtual === 'CREDIT_CARD' || metodoAtual === 'CARTAO') return 'Cartão'
  return metodoAtual || '-'
}

export default function Financeiro() {
  const [data, , { reload }] = useLocalData('financeiro')
  const { refreshTrigger } = useContext(RefreshContext)
  const { atualizarContagem } = usePendentes()
  const [mes, setMes] = useState(mesReferenciaAtual())
  const [recarregando, setRecarregando] = useState(false)
  const [statusPagamento, setStatusPagamento] = useState('todos')
  const [periodoPagamento, setPeriodoPagamento] = useState('')
  const [metodoPagamento, setMetodoPagamento] = useState('todos')
  const [protocoloPagamento, setProtocoloPagamento] = useState('')
  const [paginaPagamento, setPaginaPagamento] = useState(1)
  const [selecionandoPagamentos, setSelecionandoPagamentos] = useState(false)
  const [pagamentosSelecionados, setPagamentosSelecionados] = useState([])
  const [bulkModal, setBulkModal] = useState(null)
  const [bulkExecutando, setBulkExecutando] = useState(false)
  const [erroPagamentos, setErroPagamentos] = useState('')
  const itensPorPaginaPagamentos = 10

  useEffect(() => {
    reload(true)
  }, [refreshTrigger, reload])

  const pagamentosDoMes = useMemo(() => {
    const pagamentos = Array.isArray(data.pagamentos) ? data.pagamentos : []
    return pagamentos
      .filter((item) => pertenceAoMes(item, mes))
      .sort(ordenarMaisRecente)
  }, [data.pagamentos, mes])

  const pagamentosFiltrados = useMemo(() => {
    const pagamentos = Array.isArray(data.pagamentos) ? data.pagamentos : []
    return pagamentos
      .filter((item) => {
        const matchesStatus = statusPagamento === 'todos' || item.status === statusPagamento
        const dataBase = String(item.dataPagamento || item.data || item.dataCriacao || '')
        const matchesPeriodo = !periodoPagamento || dataBase.startsWith(periodoPagamento)
        const matchesMetodo = metodoPagamento === 'todos'
          || item.metodoPagamento === metodoPagamento
          || (metodoPagamento === 'PIX' && item.metodoPagamento === 'PIX_AUTO')
        const textoProtocolo = String(item.protocolo || item.agendamento?.protocolo || '').toLowerCase()
        const matchesProtocolo = !protocoloPagamento.trim()
          || textoProtocolo.includes(protocoloPagamento.trim().toLowerCase())
        return matchesStatus && matchesPeriodo && matchesMetodo && matchesProtocolo
      })
      .sort(ordenarMaisRecente)
  }, [data.pagamentos, metodoPagamento, periodoPagamento, protocoloPagamento, statusPagamento])

  const totalPaginasPagamentos = Math.max(1, Math.ceil(pagamentosFiltrados.length / itensPorPaginaPagamentos))
  const paginaAtualPagamentos = Math.min(paginaPagamento, totalPaginasPagamentos)
  const pagamentosPaginados = useMemo(() => (
    pagamentosFiltrados.slice(
      (paginaAtualPagamentos - 1) * itensPorPaginaPagamentos,
      paginaAtualPagamentos * itensPorPaginaPagamentos,
    )
  ), [pagamentosFiltrados, paginaAtualPagamentos])
  const totalSelecionadosPagamentos = pagamentosSelecionados.length

  const agendamentosDoMes = useMemo(() => {
    const agendamentos = Array.isArray(data.agendamentos) ? data.agendamentos : []
    return agendamentos.filter((item) => String(item?.data || item?.dataAgendamento || '').startsWith(mes))
  }, [data.agendamentos, mes])

  const recebido = pagamentosDoMes
    .filter((item) => STATUS_CONFIRMADO.has(String(item.status || '').toUpperCase()))
    .reduce((sum, item) => sum + Number(item.valor || 0), 0)

  const pendente = pagamentosDoMes
    .filter((item) => String(item.status || '').toUpperCase() === 'PENDENTE')
    .reduce((sum, item) => sum + Number(item.valor || 0), 0)

  const realizadas = agendamentosDoMes.filter((item) => String(item.status || '').toUpperCase() === 'FINALIZADO').length

  const clienteTop = useMemo(() => {
    const ranking = new Map()
    agendamentosDoMes.forEach((agendamento) => {
      const chave = agendamento?.clienteId || agendamento?.clienteNome
      if (!chave) return
      const atual = ranking.get(chave) || { nome: agendamento.clienteNome || '', total: 0 }
      ranking.set(chave, { nome: atual.nome || agendamento.clienteNome || '', total: atual.total + 1 })
    })
    return [...ranking.values()].sort((a, b) => b.total - a.total)[0] || null
  }, [agendamentosDoMes])

  const servicoTop = useMemo(() => {
    const ranking = new Map()
    agendamentosDoMes.forEach((agendamento) => {
      const chave = agendamento?.servicoId || agendamento?.servicoNome
      if (!chave) return
      const atual = ranking.get(chave) || { nome: agendamento.servicoNome || '', total: 0 }
      ranking.set(chave, { nome: atual.nome || agendamento.servicoNome || '', total: atual.total + 1 })
    })
    return [...ranking.values()].sort((a, b) => b.total - a.total)[0] || null
  }, [agendamentosDoMes])

  async function recarregar() {
    if (recarregando) return
    setRecarregando(true)
    try {
      await reload(true)
    } finally {
      setRecarregando(false)
    }
  }

  function limparSelecaoPagamentos() {
    setSelecionandoPagamentos(false)
    setPagamentosSelecionados([])
    setBulkModal(null)
  }

  function alternarPagamentoSelecionado(id) {
    setPagamentosSelecionados((current) => {
      if (current.includes(id)) return current.filter((item) => item !== id)
      if (current.length >= 10) {
        setErroPagamentos('Você pode selecionar no máximo 10 itens por vez.')
        return current
      }
      return [...current, id]
    })
  }

  function abrirBulkPagamentos(acao) {
    if (!totalSelecionadosPagamentos) return
    const configs = {
      MARCAR_COMO_PAGO: ['Marcar pagamentos como pagos', 'Tem certeza que deseja marcar os pagamentos selecionados como pagos?', 'Marcar como pago', false],
      MARCAR_COMO_PENDENTE: ['Marcar pagamentos como pendentes', 'Tem certeza que deseja marcar os pagamentos selecionados como pendentes?', 'Marcar como pendente', false],
      EXCLUIR: ['Excluir pagamentos', 'Tem certeza que deseja excluir os pagamentos selecionados? Essa ação não poderá ser desfeita.', 'Excluir', true],
    }
    const cfg = configs[acao]
    setBulkModal({ acao, titulo: cfg[0], descricao: cfg[1], confirmLabel: cfg[2], danger: cfg[3] })
  }

  async function executarBulkPagamentos() {
    if (!bulkModal || bulkExecutando) return
    setBulkExecutando(true)
    setErroPagamentos('')
    try {
      await appApi.acaoEmMassaPagamentos(pagamentosSelecionados, bulkModal.acao)
      if (bulkModal.acao === 'MARCAR_COMO_PAGO') {
        atualizarContagem()
      }
      await reload(true)
      limparSelecaoPagamentos()
    } catch (error) {
      setErroPagamentos(error.response?.data?.mensagem || 'Não foi possível executar a ação em massa.')
    } finally {
      setBulkExecutando(false)
    }
  }

  async function alterarStatusPagamento(id, novoStatus) {
    if (novoStatus === 'PAGO') {
      await appApi.marcarPagamentoPago(id)
      atualizarContagem()
    } else {
      await appApi.atualizarStatusPagamento(id, novoStatus)
    }
    await reload(true)
  }

  function excluirPagamento() {
    alert('Exclusão em desenvolvimento.')
  }

  return (
    <section className="page financeiro-page">
      <div className="page-title financeiro-header">
        <div className="financeiro-title-block">
          <span className="section-kicker">Financeiro</span>
          <h1>Financeiro</h1>
          <p>Resumo mensal, pendências e rankings operacionais.</p>
        </div>

        <div className="financeiro-controls">
          <label className="field compact-field financeiro-month-field">
            <span>Mês</span>
            <input
              type="month"
              value={mes}
              onChange={(e) => setMes(e.target.value)}
              aria-label="Filtrar financeiro por mês"
            />
          </label>

          <Button
            variant="secondary"
            icon={RefreshCw}
            onClick={recarregar}
            disabled={recarregando}
            className="financeiro-refresh-btn"
          >
            {recarregando ? 'Recarregando...' : 'Recarregar'}
          </Button>
        </div>
      </div>

      <div className="metric-grid compact financeiro-metrics">
        <DashboardCard title="Total recebido" value={currency(recebido)} />
        <DashboardCard title="Total pendente" value={currency(pendente)} />
        <DashboardCard title="Consultas realizadas" value={realizadas} />
        <DashboardCard title="Cliente com mais consultas" value={clienteTop?.nome || '-'} />
        <DashboardCard title="Servico mais vendido" value={servicoTop?.nome || '-'} />
      </div>

      <section className="panel financeiro-panel">
        <h2>Pagamentos recentes</h2>
        <div className="filters filters-inline">
          <select value={statusPagamento} onChange={(e) => setStatusPagamento(e.target.value)}>
            <option value="todos">Todos os status</option>
            <option value="PENDENTE">Pendente</option>
            <option value="PAGO">Pago</option>
            <option value="CANCELADO">Cancelado</option>
          </select>
          <input
            type="text"
            value={protocoloPagamento}
            onChange={(e) => setProtocoloPagamento(e.target.value)}
            placeholder="Protocolo"
            aria-label="Filtrar por protocolo"
            maxLength={20}
          />
          <input
            type="month"
            value={periodoPagamento}
            onChange={(e) => setPeriodoPagamento(e.target.value)}
            aria-label="Periodo do pagamento"
          />
          <select value={metodoPagamento} onChange={(e) => setMetodoPagamento(e.target.value)}>
            <option value="todos">Todos os metodos</option>
            <option value="PIX">PIX</option>
            <option value="CARTAO">Cartao</option>
            <option value="DINHEIRO">Dinheiro</option>
            <option value="BOLETO">Boleto</option>
            <option value="OUTRO">Outro</option>
          </select>
        </div>

        <div className="mass-action-toolbar mass-action-toolbar-inline">
          <BulkActionsToolbar
            selectionMode={selecionandoPagamentos}
            selectedCount={totalSelecionadosPagamentos}
            onToggleSelection={() => setSelecionandoPagamentos(true)}
            onClearSelection={limparSelecaoPagamentos}
            actions={[
              { label: 'Marcar como pago', onClick: () => abrirBulkPagamentos('MARCAR_COMO_PAGO') },
              { label: 'Marcar como pendente', onClick: () => abrirBulkPagamentos('MARCAR_COMO_PENDENTE') },
              { label: 'Excluir', danger: true, onClick: () => abrirBulkPagamentos('EXCLUIR') },
            ]}
          />
          <Button
            variant="secondary"
            icon={RefreshCw}
            className="mass-action-icon-button"
            onClick={recarregar}
            disabled={recarregando}
            aria-label="Recarregar pagamentos"
          >
            {recarregando ? '...' : ''}
          </Button>
        </div>

        {erroPagamentos && <p className="form-error">{erroPagamentos}</p>}

        <Table
          columns={[
            ...(selecionandoPagamentos ? [{
              key: '__selecionar',
              label: '',
              render: (row) => (
                <input
                  type="checkbox"
                  checked={pagamentosSelecionados.includes(row.id)}
                  onChange={() => alternarPagamentoSelecionado(row.id)}
                  disabled={!pagamentosSelecionados.includes(row.id) && totalSelecionadosPagamentos >= 10}
                  aria-label={`Selecionar pagamento ${row.id}`}
                />
              ),
            }] : []),
            {
              key: 'clienteNome',
              label: 'CLIENTE',
              render: (row) => (
                <div className="name-cell">
                  <div className="avatar">{(row.clienteNome || 'CL').substring(0, 2).toUpperCase()}</div>
                  <div className="name-cell-info">
                    <strong>{row.clienteNome}</strong>
                  </div>
                </div>
              ),
            },
            { key: 'servicoNome', label: 'SERVIÇO', render: (row) => row.servicoNome || row.servico?.nome || '-' },
            { key: 'protocolo', label: 'PROTOCOLO', render: (row) => row.protocolo || row.agendamento?.protocolo || '-' },
            { key: 'valor', label: 'VALOR', render: (row) => currency(row.valor) },
            { key: 'status', label: 'STATUS', render: (row) => <StatusBadge status={statusSimples(row.status)} /> },
            {
              key: 'acao',
              label: 'AÇÕES',
              render: (row) => (
                <ActionMenu
                  actions={[
                    { label: 'Marcar como Pago', icon: Check, onClick: () => alterarStatusPagamento(row.id, 'PAGO') },
                    { label: 'Cancelar Pagamento', icon: X, onClick: () => alterarStatusPagamento(row.id, 'CANCELADO') },
                    { label: 'Excluir', icon: Trash, danger: true, onClick: () => excluirPagamento(row.id) },
                  ]}
                />
              ),
            },
          ]}
          rows={pagamentosPaginados}
        />
        <Pagination
          page={paginaAtualPagamentos}
          totalPages={totalPaginasPagamentos}
          totalItems={pagamentosFiltrados.length}
          pageSize={itensPorPaginaPagamentos}
          onPageChange={setPaginaPagamento}
        />
      </section>

      <BulkConfirmModal
        open={Boolean(bulkModal)}
        title={bulkModal?.titulo || 'Confirmar ação'}
        description={bulkModal?.descricao || ''}
        confirmLabel={bulkModal?.confirmLabel || 'Confirmar'}
        danger={Boolean(bulkModal?.danger)}
        loading={bulkExecutando}
        onCancel={() => setBulkModal(null)}
        onConfirm={executarBulkPagamentos}
      />
    </section>
  )
}
