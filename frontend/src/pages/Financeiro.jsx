import { RefreshCw } from 'lucide-react'
import { useMemo, useState } from 'react'
import Button from '../components/Button.jsx'
import DashboardCard from '../components/DashboardCard.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import Table from '../components/Table.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
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

export default function Financeiro() {
  const [data, , { reload }] = useLocalData('financeiro')
  const [mes, setMes] = useState(mesReferenciaAtual())
  const [recarregando, setRecarregando] = useState(false)

  const pagamentosDoMes = useMemo(() => {
    const pagamentos = Array.isArray(data.pagamentos) ? data.pagamentos : []
    return pagamentos
      .filter((item) => pertenceAoMes(item, mes))
      .sort(ordenarMaisRecente)
  }, [data.pagamentos, mes])

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
        <Table
          columns={[
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
            { key: 'valor', label: 'VALOR', render: (row) => currency(row.valor) },
            { key: 'metodoPagamento', label: 'METODO', render: (row) => row.metodoPagamento || row.metodo || '-' },
            { key: 'status', label: 'STATUS', render: (row) => <StatusBadge status={row.status} /> },
          ]}
          rows={pagamentosDoMes}
        />
      </section>
    </section>
  )
}
