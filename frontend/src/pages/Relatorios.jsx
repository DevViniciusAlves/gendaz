import { Download, RefreshCw } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import Button from '../components/Button.jsx'
import ExportCsvModal from '../components/ExportCsvModal.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import Table from '../components/Table.jsx'
import Pagination from '../components/Pagination.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { currency, todayIso } from '../services/localStore.js'
import { dataHojeDdMmAAAA, exportarCsv, formatarData, periodoParaArquivo } from '../utils/csvExport.js'

export default function Relatorios() {
  const [data, , { reload }] = useLocalData('relatorios')
  const [mes, setMes] = useState(todayIso().slice(0, 7))
  const [cliente, setCliente] = useState('')
  const [servico, setServico] = useState('')
  const [recarregando, setRecarregando] = useState(false)
  const [exportModal, setExportModal] = useState(null)
  const [paginaConsultas, setPaginaConsultas] = useState(1)
  const [paginaCancelados, setPaginaCancelados] = useState(1)
  const itensPorPagina = 10

  useEffect(() => {
    reload(true)
  }, [])

  const consultas = useMemo(() => data.agendamentos.filter((item) => {
    const matchesMes = !mes || item.data.startsWith(mes)
    const matchesCliente = !cliente.trim() || item.clienteNome.toLowerCase().includes(cliente.trim().toLowerCase())
    const matchesServico = !servico.trim() || item.servicoNome.toLowerCase().includes(servico.trim().toLowerCase())
    return item.status !== 'CANCELADO' && matchesMes && matchesCliente && matchesServico
  }), [cliente, data.agendamentos, mes, servico])

  const cancelados = useMemo(() => data.agendamentos.filter((item) => {
    const matchesMes = !mes || item.data.startsWith(mes)
    const matchesCliente = !cliente.trim() || item.clienteNome.toLowerCase().includes(cliente.trim().toLowerCase())
    const matchesServico = !servico.trim() || item.servicoNome.toLowerCase().includes(servico.trim().toLowerCase())
    return item.status === 'CANCELADO' && matchesMes && matchesCliente && matchesServico
  }), [cliente, data.agendamentos, mes, servico])
  const totalPaginasConsultas = Math.max(1, Math.ceil(consultas.length / itensPorPagina))
  const totalPaginasCancelados = Math.max(1, Math.ceil(cancelados.length / itensPorPagina))
  const paginaConsultasAtual = Math.min(paginaConsultas, totalPaginasConsultas)
  const paginaCanceladosAtual = Math.min(paginaCancelados, totalPaginasCancelados)
  const consultasOrdenadas = useMemo(() => [...consultas].sort((a, b) => {
    const chaveA = `${a.data || ''} ${a.horaInicio || ''}`
    const chaveB = `${b.data || ''} ${b.horaInicio || ''}`
    return chaveB.localeCompare(chaveA)
  }), [consultas])
  const canceladosOrdenados = useMemo(() => [...cancelados].sort((a, b) => {
    const chaveA = `${a.data || ''} ${a.horaInicio || ''}`
    const chaveB = `${b.data || ''} ${b.horaInicio || ''}`
    return chaveB.localeCompare(chaveA)
  }), [cancelados])
  const consultasPaginadas = useMemo(() => consultasOrdenadas.slice((paginaConsultasAtual - 1) * itensPorPagina, paginaConsultasAtual * itensPorPagina), [consultasOrdenadas, paginaConsultasAtual])
  const canceladosPaginados = useMemo(() => canceladosOrdenados.slice((paginaCanceladosAtual - 1) * itensPorPagina, paginaCanceladosAtual * itensPorPagina), [canceladosOrdenados, paginaCanceladosAtual])

  async function recarregar() {
    if (recarregando) return
    setRecarregando(true)
    try {
      await reload(true)
    } finally {
      setRecarregando(false)
    }
  }

  function dentroDoPeriodo(item, dataInicial, dataFinal) {
    const dataBase = String(item.data || item.dataAgendamento || '')
    if (!dataBase) return false
    return dataBase >= dataInicial && dataBase <= dataFinal
  }

  function montarLinhaConsultas(item) {
    return [
      item.id,
      item.protocolo || '',
      item.clienteNome || '',
      item.servicoNome || '',
      item.profissionalNome || 'Sem preferência',
      currency(item.valor || 0),
      formatarData(item.data),
      item.horaInicio || '',
      item.horaFim || '',
      item.status || '',
      item.observacoes || '',
    ]
  }

  function montarLinhaCancelados(item) {
    return [
      item.id,
      item.protocolo || '',
      item.clienteNome || '',
      item.servicoNome || '',
      item.profissionalNome || 'Sem preferência',
      currency(item.valor || 0),
      formatarData(item.data),
      item.horaInicio || '',
      item.observacoes || '',
    ]
  }

  async function exportarConsultas({ modo, dataInicial, dataFinal }) {
    const base = Array.isArray(data.agendamentos) ? data.agendamentos : []
    const registros = base
      .filter((item) => item.status !== 'CANCELADO')
      .filter((item) => modo !== 'periodo' || dentroDoPeriodo(item, dataInicial, dataFinal))
    if (!registros.length) throw new Error('Nenhum registro encontrado para exportação.')
    exportarCsv({
      fileName: modo === 'periodo'
        ? `relatorio-consultas-gendaz-${periodoParaArquivo(dataInicial, dataFinal)}.csv`
        : `relatorio-consultas-gendaz-${dataHojeDdMmAAAA()}.csv`,
      columns: [
        'ID', 'Protocolo', 'Cliente', 'Serviço', 'Profissional', 'Valor',
        'Data', 'Hora início', 'Hora fim', 'Status', 'Observações',
      ],
      rows: registros.map(montarLinhaConsultas),
    })
  }

  async function exportarCancelados({ modo, dataInicial, dataFinal }) {
    const base = Array.isArray(data.agendamentos) ? data.agendamentos : []
    const registros = base
      .filter((item) => item.status === 'CANCELADO')
      .filter((item) => modo !== 'periodo' || dentroDoPeriodo(item, dataInicial, dataFinal))
    if (!registros.length) throw new Error('Nenhum registro encontrado para exportação.')
    exportarCsv({
      fileName: modo === 'periodo'
        ? `relatorio-cancelados-gendaz-${periodoParaArquivo(dataInicial, dataFinal)}.csv`
        : `relatorio-cancelados-gendaz-${dataHojeDdMmAAAA()}.csv`,
      columns: [
        'ID', 'Protocolo', 'Cliente', 'Serviço', 'Profissional', 'Valor',
        'Data', 'Hora início', 'Observações',
      ],
      rows: registros.map(montarLinhaCancelados),
    })
  }

  return (
    <section className="page">
      <div className="page-title">
        <span className="section-kicker">Relatórios</span>
        <h1>Histórico operacional</h1>
        <p>Relatórios simples para acompanhar agendamentos, clientes e desempenho financeiro do período.</p>
      </div>


      <div className="panel report-filters">
        <label className="field report-filter-field report-filter-month">
          <span>Mês</span>
          <input type="month" value={mes} onChange={(e) => setMes(e.target.value)} />
          <small className="field-hint">&nbsp;</small>
        </label>
        <label className="field report-filter-field">
          <span>Cliente</span>
          <input maxLength={80} placeholder="Buscar por nome do cliente" value={cliente} onChange={(e) => setCliente(e.target.value)} />
          <small className={cliente.length >= 80 ? 'field-hint limit-reached' : 'field-hint'}>
            {cliente.length >= 80 ? 'Limite de caracteres atingido.' : 'Digite apenas o nome do cliente.'}
            <strong>{cliente.length}/80</strong>
          </small>
        </label>
        <label className="field report-filter-field">
          <span>Serviço</span>
          <input maxLength={80} placeholder="Buscar por nome do serviço" value={servico} onChange={(e) => setServico(e.target.value)} />
          <small className={servico.length >= 80 ? 'field-hint limit-reached' : 'field-hint'}>
            {servico.length >= 80 ? 'Limite de caracteres atingido.' : 'Digite apenas o nome do serviço.'}
            <strong>{servico.length}/80</strong>
          </small>
        </label>
        <div className="table-actions report-actions">
          <Button variant="secondary" icon={RefreshCw} onClick={recarregar} disabled={recarregando}>
            {recarregando ? 'Recarregando...' : 'Recarregar'}
          </Button>
        </div>
      </div>

      <section className="panel">
        <div className="panel-head">
          <h2>Histórico de consultas</h2>
          <Button variant="secondary" icon={Download} onClick={() => setExportModal('consultas')}>
            Exportar CSV
          </Button>
        </div>
        <Table columns={[
          { key: 'clienteNome', label: 'CLIENTE', render: (row) => (
            <div className="name-cell">
              <div className="avatar">{(row.clienteNome || 'CL').substring(0, 2).toUpperCase()}</div>
              <div className="name-cell-info">
                <strong>{row.clienteNome}</strong>
              </div>
            </div>
          ) },
          { key: 'servicoNome', label: 'SERVIÇO' },
          { key: 'data', label: 'DATA' },
          { key: 'horaInicio', label: 'HORA' },
          { key: 'status', label: 'STATUS', render: (row) => <StatusBadge status={row.status} /> },
        ]} rows={consultasPaginadas} empty="Nenhum atendimento encontrado para o filtro atual." />
        <Pagination page={paginaConsultasAtual} totalPages={totalPaginasConsultas} totalItems={consultas.length} pageSize={itensPorPagina} onPageChange={setPaginaConsultas} />
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Histórico de cancelados</h2>
          <Button variant="secondary" icon={Download} onClick={() => setExportModal('cancelados')}>
            Exportar CSV
          </Button>
        </div>
        <Table columns={[
          { key: 'clienteNome', label: 'CLIENTE', render: (row) => (
            <div className="name-cell">
              <div className="avatar">{(row.clienteNome || 'CL').substring(0, 2).toUpperCase()}</div>
              <div className="name-cell-info">
                <strong>{row.clienteNome}</strong>
              </div>
            </div>
          ) },
          { key: 'servicoNome', label: 'SERVIÇO' },
          { key: 'data', label: 'DATA' },
          { key: 'observacoes', label: 'OBSERVAÇÃO' },
        ]} rows={canceladosPaginados} empty="Nenhum cancelamento no período." />
        <Pagination page={paginaCanceladosAtual} totalPages={totalPaginasCancelados} totalItems={cancelados.length} pageSize={itensPorPagina} onPageChange={setPaginaCancelados} />
      </section>

      <ExportCsvModal
        open={exportModal === 'consultas'}
        title="Exportar consultas"
        onClose={() => setExportModal(null)}
        onConfirm={exportarConsultas}
      />
      <ExportCsvModal
        open={exportModal === 'cancelados'}
        title="Exportar cancelados"
        onClose={() => setExportModal(null)}
        onConfirm={exportarCancelados}
      />
    </section>
  )
}
