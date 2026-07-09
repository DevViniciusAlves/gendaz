import { useContext, useEffect, useMemo, useState } from 'react'
import { RefreshContext } from '../context/RefreshContext.jsx'
import { Check, RefreshCw, Trash, X } from 'lucide-react'
import { appApi } from '../api/appApi.js'
import Button from '../components/Button.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import Table from '../components/Table.jsx'
import ActionMenu from '../components/ActionMenu.jsx'
import Pagination from '../components/Pagination.jsx'
import BulkActionsToolbar from '../components/BulkActionsToolbar.jsx'
import BulkConfirmModal from '../components/BulkConfirmModal.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { usePagamentosPendentes } from '../hooks/usePagamentosPendentes.js'
import { currency } from '../services/localStore.js'

export default function Pagamentos() {
  const [data, , { reload }] = useLocalData('pagamentos')
  const { refreshTrigger } = useContext(RefreshContext)
  const { usuario } = useAuth()
  const { atualizarContagem } = usePagamentosPendentes()
  const [status, setStatus] = useState('todos')
  const [periodo, setPeriodo] = useState('')
  const [metodo, setMetodo] = useState('todos')
  /*
  â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—
  â•‘  âš ï¸  DESATIVADO - Pagamentos do Plano        â•‘
  â•‘  VariÃ¡veis comentadas para reutilizaÃ§Ã£o      â•‘
  â•‘  futura. Descomente para ativar.             â•‘
  â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
  */
  // const [statusPlano, setStatusPlano] = useState('todos')
  // const [periodoPlano, setPeriodoPlano] = useState('')
  // const [planoFiltro, setPlanoFiltro] = useState('todos')
  // const [gatewayFiltro, setGatewayFiltro] = useState('todos')
  // const [pagamentosPlano, setPagamentosPlano] = useState([])
  // const [carregandoPlano, setCarregandoPlano] = useState(false)
  const [recarregando, setRecarregando] = useState(false)
  // const [erroPlano, setErroPlano] = useState('')
  const [pagina, setPagina] = useState(1)
  const [selecionando, setSelecionando] = useState(false)
  const [selecionados, setSelecionados] = useState([])
  const [bulkModal, setBulkModal] = useState(null)
  const [bulkExecutando, setBulkExecutando] = useState(false)
  const itensPorPagina = 10

  useEffect(() => {
    reload(true)
  }, [refreshTrigger, reload])

  /*
  â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—
  â•‘  âš ï¸  DESATIVADO - Pagamentos do Plano        â•‘
  â•‘  useEffect comentado para reutilizaÃ§Ã£o       â•‘
  â•‘  futura. Descomente para ativar.             â•‘
  â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
  */
  // useEffect(() => {
  //   carregarPagamentosPlano()
  // }, [usuario?.empresaId])

  const pagamentos = useMemo(() => data.pagamentos.filter((item) => {
    const matchesStatus = status === 'todos' || item.status === status
    const dataBase = String(item.dataPagamento || item.data || item.dataCriacao || '')
    const matchesPeriodo = !periodo || dataBase.startsWith(periodo)
    const matchesMetodo = metodo === 'todos'
      || item.metodoPagamento === metodo
      || (metodo === 'PIX' && item.metodoPagamento === 'PIX_AUTO')
    return matchesStatus && matchesPeriodo && matchesMetodo
  }), [data.pagamentos, metodo, periodo, status])

  /*
  â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—
  â•‘  âš ï¸  DESATIVADO - Pagamentos do Plano        â•‘
  â•‘  Filtro de plano comentado para reutilizaÃ§Ã£o â•‘
  â•‘  futura. Descomente para ativar.             â•‘
  â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
  */
  // const pagamentosPlanoFiltrados = useMemo(() => pagamentosPlano.filter((item) => {
  //   const matchesStatus = statusPlano === 'todos' || item.status === statusPlano
  //   const matchesPeriodo = !periodoPlano || String(item.dataCriacao || item.dataPagamento || '').startsWith(periodoPlano)
  //   const matchesPlano = planoFiltro === 'todos' || item.planoNome === planoFiltro || item.plano === planoFiltro
  //   const gateway = item.gateway || item.provider || item.metodoPagamento
  //   const matchesGateway = gatewayFiltro === 'todos' || gateway === gatewayFiltro
  //   return matchesStatus && matchesPeriodo && matchesPlano && matchesGateway
  // }), [gatewayFiltro, pagamentosPlano, periodoPlano, planoFiltro, statusPlano])

  const totalPaginas = Math.max(1, Math.ceil(pagamentos.length / itensPorPagina))
  const paginaAtual = Math.min(pagina, totalPaginas)
  const pagamentosPaginados = useMemo(() => pagamentos.slice((paginaAtual - 1) * itensPorPagina, paginaAtual * itensPorPagina), [pagamentos, paginaAtual])
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
        setErroPlano('VocÃª pode selecionar no mÃ¡ximo 10 itens por vez.')
        return current
      }
      return [...current, id]
    })
  }

  function abrirBulk(acao) {
    if (!selectedCount) return
    const configs = {
      MARCAR_COMO_PAGO: ['Marcar pagamentos como pagos', 'Tem certeza que deseja marcar os pagamentos selecionados como pagos?', 'Marcar como pago', false],
      MARCAR_COMO_PENDENTE: ['Marcar pagamentos como pendentes', 'Tem certeza que deseja marcar os pagamentos selecionados como pendentes?', 'Marcar como pendente', false],
      EXCLUIR: ['Excluir pagamentos', 'Tem certeza que deseja excluir os pagamentos selecionados? Essa aÃ§Ã£o nÃ£o poderÃ¡ ser desfeita.', 'Excluir', true],
    }
    const cfg = configs[acao]
    setBulkModal({ acao, titulo: cfg[0], descricao: cfg[1], confirmLabel: cfg[2], danger: cfg[3] })
  }

  async function executarBulk() {
    if (!bulkModal || bulkExecutando) return
    setBulkExecutando(true)
    setErroPlano('')
    try {
      await appApi.acaoEmMassaPagamentos(selecionados, bulkModal.acao)
      if (bulkModal.acao === 'MARCAR_COMO_PAGO') {
        atualizarContagem()
      }
      await reload(true)
      limparSelecao()
    } catch (error) {
      setErroPlano(error.response?.data?.mensagem || 'NÃ£o foi possÃ­vel executar a aÃ§Ã£o em massa.')
    } finally {
      setBulkExecutando(false)
    }
  }

  async function alterarStatus(id, novoStatus) {
    if (novoStatus === 'PAGO') {
      await appApi.marcarPagamentoPago(id)
      atualizarContagem()
    } else {
      await appApi.atualizarStatusPagamento(id, novoStatus)
    }
    await reload(true)
  }

  function excluirPagamento() {
    alert('ExclusÃ£o em desenvolvimento.')
  }

  /*
  â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—
  â•‘  âš ï¸  DESATIVADO - Pagamentos do Plano        â•‘
  â•‘  FunÃ§Ãµes comentadas para reutilizaÃ§Ã£o        â•‘
  â•‘  futura. Descomente para ativar.             â•‘
  â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
  */
  // async function carregarPagamentosPlano() {
  //   if (!usuario?.empresaId) return
  //   setErroPlano('')
  //   try {
  //     const pagamentos = await appApi.listarPagamentosPlano(usuario.empresaId)
  //     setPagamentosPlano(pagamentos || [])
  //   } catch (error) {
  //     setErroPlano(error.response?.data?.mensagem || 'Nao foi possivel carregar pagamentos do plano.')
  //   }
  // }

  async function recarregarPagamentos() {
    if (recarregando) return
    setRecarregando(true)
    try {
      await reload(true)
      // await carregarPagamentosPlano()
    } finally {
      setRecarregando(false)
    }
  }

  // async function tentarNovamentePlano() {
  //   if (!usuario?.empresaId) return
  //   setCarregandoPlano(true)
  //   setErroPlano('')
  //   try {
  //     const pagamento = await appApi.iniciarPagamentoPro({
  //       empresaId: usuario.empresaId,
  //       metodoPagamento: 'PIX_AUTO',
  //       plano: 'PRO',
  //       customerName: usuario.nome,
  //       customerEmail: usuario.email,
  //       customerPhone: usuario.telefone,
  //       customerDocType: usuario.documento ? 'cpf' : '',
  //       customerDocNumber: usuario.documento || '',
  //       antifraudProfilingAttemptReference: usuario.id ? `agendeasy-${usuario.id}` : '',
  //     })
  //     setPagamentosPlano((current) => [pagamento, ...current])
  //   } catch (error) {
  //     setErroPlano(error.response?.data?.mensagem || 'Nao foi possivel criar novo pagamento.')
  //   } finally {
  //     setCarregandoPlano(false)
  //   }
  // }

  function statusSimples(statusAtual) {
    return ['PAGO', 'PAYMENT_APPROVED'].includes(statusAtual) ? 'APROVADO' : 'PENDENTE'
  }

  function temDadosPix(row) {
    return ['PIX', 'PIX_AUTO'].includes(row.metodoPagamento) && (row.pixCopiaECola || row.pixQrCodeBase64)
  }

  function metodoLegivel(metodoPagamento) {
    if (metodoPagamento === 'PIX_AUTO') return 'PIX automÃ¡tico'
    if (metodoPagamento === 'CREDIT_CARD' || metodoPagamento === 'CARTAO') return 'CartÃ£o'
    return metodoPagamento || '-'
  }

  return (
    <section className="page">
      <div className="page-title">
        <span className="section-kicker">Financeiro</span>
        <h1>Pagamentos</h1>
        <p>Controle de pendentes, pagos e cancelados com filtros de status, periodo e metodo.</p>
      </div>

      <div className="filters filters-inline">
        <select value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="todos">Todos os status</option>
          <option value="PENDENTE">Pendente</option>
          <option value="PAGO">Pago</option>
          <option value="CANCELADO">Cancelado</option>
        </select>
        <input type="month" value={periodo} onChange={(e) => setPeriodo(e.target.value)} aria-label="Periodo do pagamento" />
        <select value={metodo} onChange={(e) => setMetodo(e.target.value)}>
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
          selectionMode={selecionando}
          selectedCount={selectedCount}
          onToggleSelection={() => setSelecionando(true)}
          onClearSelection={limparSelecao}
          actions={[
            { label: 'Marcar como pago', onClick: () => abrirBulk('MARCAR_COMO_PAGO') },
            { label: 'Marcar como pendente', onClick: () => abrirBulk('MARCAR_COMO_PENDENTE') },
            { label: 'Excluir', danger: true, onClick: () => abrirBulk('EXCLUIR') },
          ]}
        />
        <Button
          variant="secondary"
          icon={RefreshCw}
          className="mass-action-icon-button"
          onClick={recarregarPagamentos}
          disabled={recarregando}
          aria-label="Recarregar pagamentos"
        >
          {recarregando ? '...' : ''}
        </Button>
      </div>

      <Table
        columns={[
          ...(selecionando ? [{
            key: '__selecionar',
            label: '',
            render: (row) => (
              <input
                type="checkbox"
                checked={selecionados.includes(row.id)}
                onChange={() => alternarSelecionado(row.id)}
                disabled={!selecionados.includes(row.id) && selectedCount >= 10}
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
          { key: 'telefone', label: 'TELEFONE', render: (row) => data.clientes.find((item) => item.id === row.clienteId)?.telefone || '-' },
          { key: 'valor', label: 'VALOR', render: (row) => currency(row.valor) },
          { key: 'metodoPagamento', label: 'MÃ‰TODO', render: (row) => metodoLegivel(row.metodoPagamento) },
          { key: 'status', label: 'STATUS', render: (row) => <StatusBadge status={statusSimples(row.status)} /> },
          {
            key: 'acao',
            label: 'AÃ‡Ã•ES',
            render: (row) => (
              <ActionMenu
                actions={[
                  { label: 'Marcar como Pago', icon: Check, onClick: () => alterarStatus(row.id, 'PAGO') },
                  { label: 'Cancelar Pagamento', icon: X, onClick: () => alterarStatus(row.id, 'CANCELADO') },
                  { label: 'Excluir', icon: Trash, danger: true, onClick: () => excluirPagamento(row.id) },
                ]}
              />
            ),
          },
        ]}
        rows={pagamentosPaginados}
      />
      <Pagination page={paginaAtual} totalPages={totalPaginas} totalItems={pagamentos.length} pageSize={itensPorPagina} onPageChange={setPagina} />
      <BulkConfirmModal
        open={Boolean(bulkModal)}
        title={bulkModal?.titulo || 'Confirmar aÃ§Ã£o'}
        description={bulkModal?.descricao || ''}
        confirmLabel={bulkModal?.confirmLabel || 'Confirmar'}
        danger={Boolean(bulkModal?.danger)}
        loading={bulkExecutando}
        onCancel={() => setBulkModal(null)}
        onConfirm={executarBulk}
      />

      {/*
      â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—
      â•‘  âš ï¸  DESATIVADO - Pagamentos do Plano        â•‘
      â•‘  SeÃ§Ã£o comentada para reutilizaÃ§Ã£o futura.   â•‘
      â•‘  Descomente para ativar.                     â•‘
      â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
      */}
      {/*
      <section className="panel payments-plan-panel">
        <div className="panel-head">
          <div>
            <span className="section-kicker">Plano Pro</span>
            <h2>Pagamentos do plano</h2>
          </div>
        </div>
        <div className="filters filters-inline">
          <select value={statusPlano} onChange={(e) => setStatusPlano(e.target.value)}>
            <option value="todos">Todos os status</option>
            <option value="PAYMENT_PENDING">Pendente</option>
            <option value="PAYMENT_APPROVED">Aprovado</option>
          </select>
          <select value={planoFiltro} onChange={(e) => setPlanoFiltro(e.target.value)}>
            <option value="todos">Todos os planos</option>
            <option value="BASICO">Basico</option>
            <option value="PRO">Pro</option>
          </select>
          <select value={gatewayFiltro} onChange={(e) => setGatewayFiltro(e.target.value)}>
            <option value="todos">Todos os gateways</option>
            <option value="CAKTO">Cakto</option>
            <option value="MERCADO_PAGO">Mercado Pago</option>
            <option value="PIX">PIX</option>
            <option value="PIX_AUTO">PIX automÃ¡tico</option>
            <option value="CREDIT_CARD">Cartao</option>
          </select>
          <input type="month" value={periodoPlano} onChange={(e) => setPeriodoPlano(e.target.value)} aria-label="Periodo do pagamento do plano" />
        </div>
        {erroPlano && <p className="form-error">{erroPlano}</p>}
        <Table
          columns={[
            { key: 'planoNome', label: 'PLANO' },
            { key: 'valor', label: 'VALOR', render: (row) => currency(row.valor) },
            { key: 'metodoPagamento', label: 'MÃ‰TODO', render: (row) => metodoLegivel(row.metodoPagamento) },
            { key: 'status', label: 'STATUS', render: (row) => <StatusBadge status={statusSimples(row.status)} /> },
            { key: 'dataCriacao', label: 'DATA', render: (row) => row.dataCriacao ? new Date(row.dataCriacao).toLocaleString('pt-BR') : '-' },
            {
              key: 'acao',
              label: 'AÃ‡Ã•ES',
              render: (row) => (
                <div className="table-actions">
                  {row.checkoutUrl && statusSimples(row.status) === 'PENDENTE' && <a className="btn btn-secondary" href={row.checkoutUrl} target="_blank" rel="noreferrer">Checkout</a>}
                  {temDadosPix(row) && <Button variant="ghost" onClick={() => navigator.clipboard.writeText(row.pixCopiaECola || '')} disabled={!row.pixCopiaECola}>Copiar PIX</Button>}
                </div>
              ),
            },
          ]}
          rows={pagamentosPlanoFiltrados}
          empty="Nenhum pagamento de plano encontrado."
        />
      </section>
      */}
    </section>
  )
}




