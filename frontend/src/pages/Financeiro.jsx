import { Check, Download, RefreshCw, Trash, X } from 'lucide-react'
import { useContext, useEffect, useMemo, useState } from 'react'
import { useAuth } from '../contexts/AuthContext.jsx'
import { RefreshContext } from '../context/RefreshContext.jsx'
import { appApi } from '../api/appApi.js'
import ActionMenu from '../components/ActionMenu.jsx'
import BulkActionsToolbar from '../components/BulkActionsToolbar.jsx'
import BulkConfirmModal from '../components/BulkConfirmModal.jsx'
import Button from '../components/Button.jsx'
import DashboardCard from '../components/DashboardCard.jsx'
import ExportCsvModal from '../components/ExportCsvModal.jsx'
import Modal from '../components/Modal.jsx'
import Pagination from '../components/Pagination.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import Table from '../components/Table.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { usePendentes } from '../contexts/PendentesContext.jsx'
import { currency, todayIso } from '../services/localStore.js'
import { dataHojeDdMmAAAA, exportarCsv, formatarData, periodoParaArquivo, statusPagamentoLegivel } from '../utils/csvExport.js'

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

function ehCreditoParcelado(pagamento) {
  const metodo = pagamento?.metodoPagamento
  return ['CREDITO', 'CREDIT_CARD', 'CARTAO'].includes(metodo) && Number(pagamento?.parcelas || 0) > 1
}

function adicionarMeses(dataTexto, meses) {
  const [ano, mes, dia] = String(dataTexto || '').slice(0, 10).split('-').map(Number)
  if (!ano || !mes || !dia) return dataTexto
  const data = new Date(ano, mes - 1 + meses, dia, 12, 0, 0, 0)
  return data.toISOString().slice(0, 10)
}

function expandirParcelasPagamento(pagamento) {
  if (!ehCreditoParcelado(pagamento)) return [pagamento]
  const parcelas = Number(pagamento.parcelas)
  const valorTotal = Number(pagamento.valor || 0)
  const valorParcela = Number((valorTotal / parcelas).toFixed(2))
  const dataBase = valorTextoPagamento(pagamento).slice(0, 10)
  return Array.from({ length: parcelas }, (_, index) => ({
    ...pagamento,
    id: `${pagamento.id}-parcela-${index + 1}`,
    pagamentoId: pagamento.id,
    parcelaAtual: index + 1,
    valor: index === parcelas - 1 ? Number((valorTotal - (valorParcela * (parcelas - 1))).toFixed(2)) : valorParcela,
    dataPagamento: adicionarMeses(dataBase, index),
  }))
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

function statusClienteValor(row) {
  return row.statusCliente || row.cliente?.status || 'ATIVO'
}

function metodoLegivel(metodoAtual, parcelas, parcelaAtual) {
  if (!metodoAtual) return '—'
  if (metodoAtual === 'PIX_AUTO') return 'PIX automático'
  if (metodoAtual === 'CREDIT_CARD' || metodoAtual === 'CARTAO' || metodoAtual === 'CREDITO') {
    if (parcelas && parcelaAtual) return `Crédito · ${parcelaAtual}/${parcelas}`
    return `Crédito${parcelas ? ` · ${parcelas}x` : ''}`
  }
  if (metodoAtual === 'DEBITO') return 'Débito'
  return metodoAtual || '-'
}

const FORMAS_PAGAMENTO = [
  ['pixAtivo', 'Pix'],
  ['debitoAtivo', 'Débito'],
  ['creditoAtivo', 'Crédito'],
  ['parceladoAtivo', 'Parcelado'],
  ['dinheiroAtivo', 'Dinheiro'],
]

const styles = `
  .caixa-despesas-actions {
    display: flex;
    gap: 8px;
    margin-top: 8px;
  }
  .caixa-despesas-actions .btn {
    flex: 1;
    min-width: 0;
    padding: 6px 8px;
    font-size: 12px;
  }
`

export default function Financeiro() {
  const [data, , { reload }] = useLocalData('financeiro')

  return (
    <>
      <style>{styles}</style>
      <section className="page financeiro-page">
  const { refreshTrigger } = useContext(RefreshContext)
  const { atualizarContagem } = usePendentes()
  const [mes, setMes] = useState(mesReferenciaAtual())
  const [recarregando, setRecarregando] = useState(false)
  const [statusPagamento, setStatusPagamento] = useState('mes_atual')
  const [periodoPagamento, setPeriodoPagamento] = useState('')
  const [metodoPagamento, setMetodoPagamento] = useState('todos')
  const [protocoloPagamento, setProtocoloPagamento] = useState('')
  const [paginaPagamento, setPaginaPagamento] = useState(1)
  const [selecionandoPagamentos, setSelecionandoPagamentos] = useState(false)
  const [pagamentosSelecionados, setPagamentosSelecionados] = useState([])
  const [bulkModal, setBulkModal] = useState(null)
  const [bulkExecutando, setBulkExecutando] = useState(false)
  const [erroPagamentos, setErroPagamentos] = useState('')
  const [exportModal, setExportModal] = useState(false)
  const [formasPagamento, setFormasPagamento] = useState(null)
  const [salvandoFormas, setSalvandoFormas] = useState(false)
  const [pagamentoManual, setPagamentoManual] = useState(null)
  const itensPorPaginaPagamentos = 10

  const { usuario } = useAuth()
  const planoAtual = String(usuario?.plano || '').toUpperCase()
  const isPlanoPro = planoAtual === 'PRO'

  const [caixaDespesas, setCaixaDespesas] = useState(null)
  const [modalAdicionar, setModalAdicionar] = useState(null)
  const [modalRemover, setModalRemover] = useState(null)
  const [valorModal, setValorModal] = useState('')
  const [motivoModal, setMotivoModal] = useState('')
  const [obsModal, setObsModal] = useState('')
  const [erroModal, setErroModal] = useState('')
  const [salvandoModal, setSalvandoModal] = useState(false)
  const [modalHistorico, setModalHistorico] = useState(false)
  const [historico, setHistorico] = useState(null)
  const [paginaHistorico, setPaginaHistorico] = useState(1)
  const [carregandoHistorico, setCarregandoHistorico] = useState(false)

  const agendamentoMap = useMemo(() => {
    const agendamentos = Array.isArray(data.agendamentos) ? data.agendamentos : []
    const map = new Map()
    agendamentos.forEach((a) => map.set(a.id, a))
    return map
  }, [data.agendamentos])

  useEffect(() => {
    reload(true)
  }, [refreshTrigger, reload])

  useEffect(() => {
    appApi.buscarFormasPagamento()
      .then(setFormasPagamento)
      .catch(() => setFormasPagamento({ pixAtivo: true, debitoAtivo: true, creditoAtivo: true, parceladoAtivo: false, dinheiroAtivo: true, maxParcelas: 12 }))
  }, [])

  useEffect(() => {
    if (!isPlanoPro) return
    carregarTotaisCaixaDespesas()
  }, [isPlanoPro])

  async function carregarTotaisCaixaDespesas() {
    try {
      const totais = await appApi.buscarTotaisCaixaDespesas()
      setCaixaDespesas(totais)
    } catch {
      setCaixaDespesas({ caixaTotal: 0, despesasTotal: 0 })
    }
  }

  function abrirModalAdicionar(tipo) {
    setModalAdicionar(tipo)
    setValorModal('')
    setObsModal('')
    setErroModal('')
  }

  function fecharModalAdicionar() {
    setModalAdicionar(null)
    setErroModal('')
  }

  function abrirModalRemover(tipo) {
    setModalRemover(tipo)
    setValorModal('')
    setMotivoModal('')
    setErroModal('')
  }

  function fecharModalRemover() {
    setModalRemover(null)
    setErroModal('')
  }

  async function confirmarAdicionar() {
    const valor = Number(valorModal)
    if (!valorModal || Number.isNaN(valor) || valor <= 0) {
      setErroModal('Informe um valor válido e maior que zero.')
      return
    }
    setSalvandoModal(true)
    setErroModal('')
    try {
      const tipo = modalAdicionar
      const totais = tipo === 'CAIXA'
        ? await appApi.adicionarCaixa(valor, obsModal.trim())
        : await appApi.adicionarDespesas(valor, obsModal.trim())
      setCaixaDespesas(totais)
      fecharModalAdicionar()
    } catch {
      /* erro já exibido via toast */
    } finally {
      setSalvandoModal(false)
    }
  }

  async function confirmarRemover() {
    const valor = Number(valorModal)
    if (!valorModal || Number.isNaN(valor) || valor <= 0) {
      setErroModal('Informe um valor válido e maior que zero.')
      return
    }
    if (modalRemover === 'CAIXA' && valor > (caixaDespesas?.caixaTotal || 0)) {
      setErroModal('O valor não pode ser maior que o total do caixa.')
      return
    }
    if (modalRemover === 'DESPESAS' && valor > (caixaDespesas?.despesasTotal || 0)) {
      setErroModal('O valor não pode ser maior que o total de despesas.')
      return
    }
    setSalvandoModal(true)
    setErroModal('')
    try {
      const tipo = modalRemover
      const descricao = tipo === 'CAIXA'
        ? `Usuário removeu do Caixa${motivoModal ? ` (Motivo = ${motivoModal})` : ''}`
        : `Usuário removeu Despesas${motivoModal ? ` (Motivo = ${motivoModal})` : ''}`
      const totais = tipo === 'CAIXA'
        ? await appApi.removerCaixa(valor, descricao)
        : await appApi.removerDespesas(valor, descricao)
      setCaixaDespesas(totais)
      fecharModalRemover()
    } catch {
      /* erro já exibido via toast */
    } finally {
      setSalvandoModal(false)
    }
  }

  async function removerRegistro(tipo, logId) {
    try {
      const totais = tipo === 'CAIXA'
        ? await appApi.removerCaixa(logId)
        : await appApi.removerDespesas(logId)
      setCaixaDespesas(totais)
      await carregarHistorico(paginaHistorico)
    } catch {
      /* erro via toast */
    }
  }

  async function carregarHistorico(pagina = 1) {
    setCarregandoHistorico(true)
    try {
      const resultado = await appApi.buscarHistoricoCaixaDespesas(pagina, 10)
      setHistorico(resultado)
      setPaginaHistorico(pagina)
    } catch {
      setHistorico({ itens: [], total: 0, pagina, totalPaginas: 1, tamanhoPagina: 10 })
    } finally {
      setCarregandoHistorico(false)
    }
  }

  function abrirHistorico() {
    setModalHistorico(true)
    carregarHistorico(1)
  }

  function trocarPaginaHistorico(pagina) {
    window.scrollTo({ top: 0 })
    carregarHistorico(pagina)
  }

  const pagamentosExpandidos = useMemo(() => {
    const pagamentos = Array.isArray(data.pagamentos) ? data.pagamentos : []
    return pagamentos.flatMap(expandirParcelasPagamento)
  }, [data.pagamentos])

  const pagamentosDoMes = useMemo(() => pagamentosExpandidos
    .filter((item) => pertenceAoMes(item, mes))
    .sort(ordenarMaisRecente), [pagamentosExpandidos, mes])

  const pagamentosFiltrados = useMemo(() => pagamentosExpandidos
      .filter((item) => {
        const matchesStatus = statusPagamento === 'todos' || statusPagamento === 'mes_atual' || item.status === statusPagamento
        const matchesMesAtual = statusPagamento !== 'mes_atual' || valorTextoPagamento(item).startsWith(mesReferenciaAtual())
        const dataBase = String(item.dataPagamento || item.data || item.dataCriacao || '')
        const matchesPeriodo = !periodoPagamento || dataBase.startsWith(periodoPagamento)
        const matchesMetodo = metodoPagamento === 'todos'
          || item.metodoPagamento === metodoPagamento
          || (metodoPagamento === 'PIX' && item.metodoPagamento === 'PIX_AUTO')
          || (metodoPagamento === 'CREDITO' && ['CREDIT_CARD', 'CARTAO'].includes(item.metodoPagamento))
        const textoProtocolo = String(item.protocolo || item.agendamento?.protocolo || '').toLowerCase()
        const matchesProtocolo = !protocoloPagamento.trim()
          || textoProtocolo.includes(protocoloPagamento.trim().toLowerCase())
        return matchesStatus && matchesMesAtual && matchesPeriodo && matchesMetodo && matchesProtocolo
      })
      .sort(ordenarMaisRecente), [metodoPagamento, pagamentosExpandidos, periodoPagamento, protocoloPagamento, statusPagamento])

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

  const pendente = pagamentosExpandidos
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

  async function exportarFinanceiro({ modo, dataInicial, dataFinal }) {
    const registros = pagamentosExpandidos.filter((item) => {
      if (modo !== 'periodo') return true
      const dataBase = String(
        item.dataPagamento || item.pagoEm || item.createdAt || item.data || item.dataCriacao || item.updatedAt || item.agendamento?.data || '',
      ).slice(0, 10)
      return dataBase >= dataInicial && dataBase <= dataFinal
    })
    if (!registros.length) throw new Error('Nenhum registro encontrado para exportação.')
    exportarCsv({
      fileName: modo === 'periodo'
        ? `financeiro-gendaz-${periodoParaArquivo(dataInicial, dataFinal)}.csv`
        : `financeiro-gendaz-todos-${dataHojeDdMmAAAA()}.csv`,
      columns: [
        'ID', 'Protocolo', 'Cliente', 'Serviço', 'Profissional', 'Valor',
        'Método de pagamento', 'Status', 'Data do agendamento', 'Data do pagamento',
        'Data de criação', 'Observações',
      ],
      rows: registros.map((item) => [
        item.id,
        item.protocolo || item.agendamento?.protocolo || '',
        item.clienteNome || item.cliente?.nome || '',
        item.servicoNome || item.servico?.nome || '',
        item.profissionalNome || item.agendamento?.profissionalNome || 'Sem preferência',
        currency(item.valor || 0),
        metodoLegivel(item.metodoPagamento, item.parcelas, item.parcelaAtual),
        statusPagamentoLegivel(item.status),
        item.agendamento?.data ? formatarData(item.agendamento.data) : (item.data ? formatarData(item.data) : ''),
        item.dataPagamento ? formatarData(item.dataPagamento) : '',
        '',
        item.observacoes || item.agendamento?.observacoes || '',
      ]),
    })
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
      if (bulkModal.acao === 'MARCAR_COMO_PAGO') {
        setPagamentoManual({ tipo: 'bulk' })
        setBulkModal(null)
        return
      }
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

  async function salvarFormasPagamento(chave, valor) {
    if (!formasPagamento || salvandoFormas) return
    const proximo = { ...formasPagamento, [chave]: valor }
    if (chave === 'creditoAtivo' && !valor) proximo.parceladoAtivo = false
    setFormasPagamento(proximo)
    setSalvandoFormas(true)
    try {
      const salvo = await appApi.atualizarFormasPagamento(proximo)
      setFormasPagamento(salvo)
    } catch (error) {
      setFormasPagamento(formasPagamento)
    } finally {
      setSalvandoFormas(false)
    }
  }

  function metodosAtivos() {
    return [
      formasPagamento?.pixAtivo && { label: 'Pix', metodoPagamento: 'PIX' },
      formasPagamento?.debitoAtivo && { label: 'Débito', metodoPagamento: 'DEBITO' },
      formasPagamento?.creditoAtivo && { label: 'Crédito', metodoPagamento: 'CREDITO' },
      formasPagamento?.dinheiroAtivo && { label: 'Dinheiro', metodoPagamento: 'DINHEIRO' },
    ].filter(Boolean)
  }

  async function confirmarPagamentoManual(metodoPagamento, parcelas = null) {
    if (!pagamentoManual) return
    const payload = { metodoPagamento, parcelas: metodoPagamento === 'CREDITO' ? (parcelas || 1) : null }
    if (pagamentoManual.tipo === 'bulk') {
      await appApi.acaoEmMassaPagamentos(pagamentosSelecionados, 'MARCAR_COMO_PAGO', payload)
      limparSelecaoPagamentos()
    } else {
      await appApi.marcarPagamentoPago(pagamentoManual.id, payload)
    }
    setPagamentoManual(null)
    atualizarContagem()
    await reload(true)
  }

  async function alterarStatusPagamento(id, novoStatus) {
    if (novoStatus === 'PAGO') {
      setPagamentoManual({ tipo: 'single', id })
      return
    }
    await appApi.atualizarStatusPagamento(id, novoStatus)
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
          <p>Resumo mensal e pendências.</p>
        </div>
      </div>

      {!isPlanoPro && (
        <div className="metric-grid compact financeiro-metrics">
          <DashboardCard title="Total recebido" value={currency(recebido)} />
          <DashboardCard title="Total pendente" value={currency(pendente)} />
        </div>
      )}

      {isPlanoPro && (
        <div className="metric-grid compact financeiro-metrics caixa-despesas-grid">
           <article className="metric-card caixa-card">
             <div>
               <span>CAIXA</span>
               <strong>{currency(caixaDespesas?.caixaTotal || 0)}</strong>
               <div className="caixa-despesas-actions">
                 <button type="button" className="btn btn-primary caixa-card-btn" onClick={() => abrirModalAdicionar('CAIXA')}>
                   ADICIONAR
                 </button>
                 <button type="button" className="btn btn-danger caixa-card-btn" onClick={() => abrirModalRemover('CAIXA')}>
                   REMOVER
                 </button>
               </div>
             </div>
           </article>

           <article className="metric-card despesas-card">
             <div>
               <span>DESPESAS</span>
               <strong>{currency(caixaDespesas?.despesasTotal || 0)}</strong>
               <div className="caixa-despesas-actions">
                 <button type="button" className="btn btn-primary caixa-card-btn" onClick={() => abrirModalAdicionar('DESPESAS')}>
                   ADICIONAR
                 </button>
                 <button type="button" className="btn btn-danger caixa-card-btn" onClick={() => abrirModalRemover('DESPESAS')}>
                   REMOVER
                 </button>
               </div>
             </div>
           </article>

          <article
            className="metric-card historico-card"
            style={{ borderTop: '3px solid var(--primary)', cursor: 'pointer' }}
            role="button"
            tabIndex={0}
            onClick={abrirHistorico}
            onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && abrirHistorico()}
          >
            <div>
              <span>HISTÓRICO</span>
              <strong>Ver registros</strong>
            </div>
          </article>
        </div>
      )}

      <div className="financeiro-top-row">
        <div className="financeiro-payment-grid">
          {FORMAS_PAGAMENTO.map(([chave, label]) => (
            <label key={chave} className="financeiro-payment-toggle">
              <span>{label}</span>
              <input
                type="checkbox"
                checked={Boolean(formasPagamento?.[chave])}
                disabled={salvandoFormas || (chave === 'parceladoAtivo' && !formasPagamento?.creditoAtivo)}
                onChange={(e) => salvarFormasPagamento(chave, e.target.checked)}
              />
            </label>
          ))}
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
            icon={Download}
            onClick={() => setExportModal(true)}
            className="financeiro-refresh-btn"
          >
            Exportar CSV
          </Button>

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

      <section className="panel financeiro-panel">
        <h2>Pagamentos recentes</h2>
        <div className="filters filters-inline">
          <select value={statusPagamento} onChange={(e) => setStatusPagamento(e.target.value)}>
            <option value="mes_atual">Mês atual</option>
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
            <option value="DEBITO">Débito</option>
            <option value="CREDITO">Crédito</option>
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
                  checked={pagamentosSelecionados.includes(row.pagamentoId || row.id)}
                  onChange={() => alternarPagamentoSelecionado(row.pagamentoId || row.id)}
                  disabled={!pagamentosSelecionados.includes(row.pagamentoId || row.id) && totalSelecionadosPagamentos >= 10}
                  aria-label={`Selecionar pagamento ${row.pagamentoId || row.id}`}
                />
              ),
            }] : []),
            {
              key: 'clienteNome',
              label: 'CLIENTE',
              render: (row) => (
                <div className="financeiro-center-cell">
                  <div className="name-cell financeiro-center-name">
                    <div className="avatar">{(row.clienteNome || 'CL').substring(0, 2).toUpperCase()}</div>
                    <div className="name-cell-info">
                      <strong>{row.clienteNome}</strong>
                    </div>
                  </div>
                </div>
              ),
            },
            { key: 'statusCliente', label: 'CADASTRO', render: (row) => <span className="financeiro-center-cell"><StatusBadge status={statusClienteValor(row)} /></span> },
            { key: 'servicoNome', label: 'SERVIÇO', render: (row) => <span className="financeiro-center-cell">{row.servicoNome || row.servico?.nome || '-'}</span> },
            { key: 'protocolo', label: 'PROTOCOLO', render: (row) => <span className="financeiro-center-cell">{row.protocolo || row.agendamento?.protocolo || '-'}</span> },
            { key: 'valor', label: 'VALOR', render: (row) => {
              const ag = agendamentoMap.get(row.agendamentoId || row.pagamentoId || row.id) || row.agendamento || {}
              const cupomCodigo = ag.cupomCodigo || row.cupomCodigo
              const desconto = ag.valorDesconto ?? row.valorDesconto
              const valorOriginal = ag.valorOriginal ?? row.valorOriginal
              const temCupom = cupomCodigo && desconto != null && Number(desconto) > 0
              if (temCupom) {
                return (
                  <div className="financeiro-center-cell financeiro-valor-desconto">
                    <span className="financeiro-valor-original">{currency(valorOriginal ?? row.valor)}</span>
                    <span className="financeiro-valor-final">{currency(row.valor)}</span>
                  </div>
                )
              }
              return <span className="financeiro-center-cell">{currency(row.valor)}</span>
            }},
            { key: 'cupomCodigo', label: 'CUPOM', render: (row) => {
              const ag = agendamentoMap.get(row.agendamentoId || row.pagamentoId || row.id) || row.agendamento || {}
              const cupom = ag.cupomCodigo || row.cupomCodigo
              return cupom
                ? <span className="financeiro-center-cell"><span className="financeiro-cupom-tag">{cupom}</span></span>
                : <span className="financeiro-center-cell"><span className="financeiro-cupom-vazio">SEM CUPOM</span></span>
            }},
            { key: 'metodoPagamento', label: 'FORMA', render: (row) => <span className="financeiro-center-cell">{metodoLegivel(row.metodoPagamento, row.parcelas, row.parcelaAtual)}</span> },
            { key: 'status', label: 'STATUS', render: (row) => <span className="financeiro-center-cell"><StatusBadge status={statusSimples(row.status)} /></span> },
            {
              key: 'acao',
              label: 'AÇÕES',
              render: (row) => (
                <span className="financeiro-center-cell financeiro-center-actions">
                  <ActionMenu
                    actions={[
                      { label: 'Marcar como Pago', icon: Check, onClick: () => alterarStatusPagamento(row.pagamentoId || row.id, 'PAGO') },
                      { label: 'Cancelar Pagamento', icon: X, onClick: () => alterarStatusPagamento(row.pagamentoId || row.id, 'CANCELADO') },
                      { label: 'Excluir', icon: Trash, danger: true, onClick: () => excluirPagamento(row.pagamentoId || row.id) },
                    ]}
                  />
                </span>
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

      <Modal title="Marcar como pago" open={Boolean(pagamentoManual)} onClose={() => setPagamentoManual(null)}>
        <div className="form-grid single">
          <p className="panel-description">Selecione a forma de pagamento utilizada.</p>
          {!pagamentoManual?.creditoParcelado ? (
            <div className="payment-methods">
              {metodosAtivos().map((metodo) => (
                <button
                  key={metodo.metodoPagamento}
                  type="button"
                  onClick={() => metodo.metodoPagamento === 'CREDITO' && formasPagamento?.parceladoAtivo
                    ? setPagamentoManual({ ...pagamentoManual, creditoParcelado: true })
                    : confirmarPagamentoManual(metodo.metodoPagamento)}
                >
                  {metodo.label}
                </button>
              ))}
            </div>
          ) : (
            <div className="payment-methods">
              {Array.from({ length: formasPagamento?.maxParcelas || 12 }, (_, index) => index + 1).map((parcela) => (
                <button key={parcela} type="button" onClick={() => confirmarPagamentoManual('CREDITO', parcela)}>
                  {parcela}x
                </button>
              ))}
            </div>
          )}
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
        onConfirm={executarBulkPagamentos}
      />

      <ExportCsvModal
        open={exportModal}
        title="Exportar financeiro"
        onClose={() => setExportModal(false)}
        onConfirm={exportarFinanceiro}
      />

      <Modal
        title={modalAdicionar === 'DESPESAS' ? 'Adicionar Despesa' : 'Adicionar ao Caixa'}
        open={Boolean(modalAdicionar)}
        onClose={fecharModalAdicionar}
      >
        <div className="form-grid single">
          {erroModal && <p className="form-error">{erroModal}</p>}
          <label className="field">
            <span>Valor (R$)</span>
            <input
              type="number"
              min="0.01"
              step="0.01"
              value={valorModal}
              onChange={(e) => setValorModal(e.target.value)}
              placeholder="0,00"
              aria-label="Valor"
            />
          </label>
          <label className="field">
            <span>Obs (opcional)</span>
            <input
              type="text"
              value={obsModal}
              onChange={(e) => setObsModal(e.target.value)}
              placeholder="Observação"
              maxLength={500}
              aria-label="Observação"
            />
          </label>
          <div className="modal-actions">
            <Button variant="secondary" onClick={fecharModalAdicionar}>Cancelar</Button>
            <Button variant="primary" loading={salvandoModal} onClick={confirmarAdicionar}>Salvar</Button>
          </div>
        </div>
      </Modal>

      <Modal
        title={modalRemover === 'DESPESAS' ? 'Remover Despesa' : 'Remover do Caixa'}
        open={Boolean(modalRemover)}
        onClose={fecharModalRemover}
      >
        <div className="form-grid single">
          {erroModal && <p className="form-error">{erroModal}</p>}
          <label className="field">
            <span>Valor (R$)</span>
            <input
              type="number"
              min="0.01"
              step="0.01"
              value={valorModal}
              onChange={(e) => setValorModal(e.target.value)}
              placeholder="0,00"
              aria-label="Valor"
            />
          </label>
          <label className="field">
            <span>Motivo (opcional)</span>
            <input
              type="text"
              value={motivoModal}
              onChange={(e) => setMotivoModal(e.target.value)}
              placeholder="Motivo da remoção"
              maxLength={500}
              aria-label="Motivo"
            />
          </label>
          <div className="modal-actions">
            <Button variant="secondary" onClick={fecharModalRemover}>Cancelar</Button>
            <Button variant="danger" loading={salvandoModal} onClick={confirmarRemover}>Remover</Button>
          </div>
        </div>
      </Modal>

      <Modal title="Histórico" open={modalHistorico} onClose={() => setModalHistorico(false)}>
        <div className="historico-modal">
          {carregandoHistorico ? (
            <p className="panel-description">Carregando histórico...</p>
          ) : (
            <>
              <table className="table historico-table">
                <thead>
                  <tr>
                    <th>Descrição</th>
                    <th>Valor</th>
                    <th>Data</th>
                  </tr>
                </thead>
                <tbody>
                  {historico?.itens?.length ? (
                    historico.itens.map((item) => (
                      <tr key={item.id}>
                        <td>
                          {item.descricao}
                          {item.obs ? ` (${item.obs})` : ''}
                        </td>
                        <td className={item.positivo ? 'success-text' : 'danger-text'}>
                          {item.positivo ? '' : '-'}{currency(item.valor)}
                        </td>
                        <td>{formatarData(item.data)}</td>
                      </tr>
                    ))
                  ) : (
                    <tr><td colSpan={3} className="historico-vazio">Nenhum registro encontrado.</td></tr>
                  )}
                </tbody>
              </table>
              <Pagination
                page={paginaHistorico}
                totalPages={historico?.totalPaginas || 1}
                totalItems={historico?.total || 0}
                pageSize={historico?.tamanhoPagina || 10}
                onPageChange={trocarPaginaHistorico}
              />
            </>
          )}
        </div>
      </Modal>
    </section>
  )
}
