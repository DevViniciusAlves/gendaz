import { Download, FilePlus, Pencil, RefreshCw } from 'lucide-react'
import Button from '../components/Button.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import Table from '../components/Table.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { currency, nextId } from '../services/localStore.js'

export default function NotasFiscais() {
  const [data, setData] = useLocalData('notasFiscais')

  function emitirNota() {
    const cliente = data.clientes[0]
    setData((current) => ({
      ...current,
      notasFiscais: [...current.notasFiscais, {
        id: nextId(current.notasFiscais),
        clienteId: cliente.id,
        clienteNome: cliente.nome,
        pedido: `#${1050 + nextId(current.notasFiscais)}`,
        valor: 180,
        status: 'EMITIDA',
        numeroFake: `NF-e ${String(nextId(current.notasFiscais)).padStart(3, '0')}.${String(nextId(current.notasFiscais)).padStart(3, '0')}`,
        protocolo: `BR${Date.now().toString().slice(-8)}`,
        diagnostico: 'Nota emitida com sucesso.',
        dataEmissao: new Date().toISOString(),
      }],
    }))
  }

  function corrigirNota(id) {
    setData((current) => ({
      ...current,
      notasFiscais: current.notasFiscais.map((nota) => nota.id === id ? {
        ...nota,
        status: 'EMITIDA',
        numeroFake: nota.numeroFake === 'Aguardando número' ? `NF-e CORR.${String(id).padStart(3, '0')}` : nota.numeroFake,
        diagnostico: 'Cadastro corrigido e documento autorizado.',
      } : nota),
    }))
  }

  function baixarNota(nota) {
    const conteudo = [
      'NOTA FISCAL ELETRÔNICA',
      `Número: ${nota.numeroFake}`,
      `Cliente: ${nota.clienteNome}`,
      `Pedido: ${nota.pedido}`,
      `Valor: ${currency(nota.valor)}`,
      `Status: ${nota.status}`,
      `Diagnóstico: ${nota.diagnostico}`,
    ].join('\n')
    const blob = new Blob([conteudo], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${nota.numeroFake.replaceAll(' ', '-').replaceAll('.', '-')}.txt`
    link.click()
    URL.revokeObjectURL(url)
  }

  return (
    <section className="page">
      <div className="page-title row-title">
        <div>
          <span className="section-kicker">Documentos fiscais</span>
          <h1>Notas Fiscais</h1>
          <p>Fluxo de emissão, download e correção de nota com foco operacional.</p>
        </div>
        <Button icon={FilePlus} onClick={emitirNota}>Emitir nota</Button>
      </div>
      <section className="panel invoice-panel">
        <Table columns={[
          { key: 'numeroFake', label: 'Nota', render: (row) => <div className="stacked"><strong>{row.numeroFake}</strong><small>{row.protocolo}</small></div> },
          { key: 'clienteNome', label: 'Cliente', render: (row) => <div className="stacked"><strong>{row.clienteNome}</strong><small>Pedido {row.pedido}</small></div> },
          { key: 'status', label: 'Status', render: (row) => <StatusBadge status={row.status} /> },
          { key: 'valor', label: 'Valor', render: (row) => currency(row.valor) },
          { key: 'dataEmissao', label: 'Data', render: (row) => new Date(row.dataEmissao).toLocaleDateString('pt-BR') },
          { key: 'diagnostico', label: 'Diagnóstico', render: (row) => <span className={row.status === 'REPROVADA' ? 'danger-text' : ''}>{row.diagnostico}</span> },
          { key: 'acoes', label: 'Ações', render: (row) => <div className="table-actions"><button className="icon-btn" title="Baixar nota" aria-label={`Baixar nota ${row.numeroFake}`} onClick={() => baixarNota(row)}><Download size={16} /></button><button className="icon-btn" title="Corrigir nota" aria-label={`Corrigir nota ${row.numeroFake}`} onClick={() => corrigirNota(row.id)}><Pencil size={16} /></button><button className="icon-btn" title="Reemitir" aria-label={`Reemitir nota ${row.numeroFake}`} onClick={() => corrigirNota(row.id)}><RefreshCw size={16} /></button></div> },
        ]} rows={data.notasFiscais} />
      </section>
    </section>
  )
}
