import { useMemo, useState } from 'react'
import StatusBadge from '../components/StatusBadge.jsx'
import Table from '../components/Table.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { currency } from '../services/localStore.js'

const statusOptions = ['PENDENTE', 'ENTREGA', 'FINALIZADO', 'REPROVADO']

export default function Pedidos() {
  const [data, setData] = useLocalData('pedidos')
  const [buscaProtocolo, setBuscaProtocolo] = useState('')
  const [buscaCliente, setBuscaCliente] = useState('')

  const pedidos = useMemo(() => (Array.isArray(data.pedidos) ? data.pedidos : []).filter((pedido) => {
    const matchesProtocolo = String(pedido.protocolo || '').toLowerCase().includes(buscaProtocolo.toLowerCase())
    const matchesCliente = String(pedido.clienteNome || '').toLowerCase().includes(buscaCliente.toLowerCase())
    return matchesProtocolo && matchesCliente
  }), [data.pedidos, buscaProtocolo, buscaCliente])

  function alterarStatus(id, status) {
    setData((current) => ({
      ...current,
      pedidos: current.pedidos.map((pedido) => pedido.id === id ? { ...pedido, status } : pedido),
    }))
  }

  return (
    <section className="page">
      <div className="page-title">
        <span className="section-kicker">Operação</span>
        <h1>Pedidos</h1>
        <p>Pedidos com busca por protocolo e cliente, além de status editável.</p>
      </div>
      <div className="delivery-search">
        <div className="search-shell">
          <input maxLength={40} placeholder="Protocolo" value={buscaProtocolo} onChange={(e) => setBuscaProtocolo(e.target.value)} />
          <small className={buscaProtocolo.length >= 40 ? 'field-hint limit-reached' : 'field-hint'}>{buscaProtocolo.length >= 40 ? 'Limite de caracteres atingido.' : 'Digite o protocolo.'}<strong>{buscaProtocolo.length}/40</strong></small>
        </div>
        <div className="search-shell">
          <input maxLength={80} placeholder="Nome do cliente" value={buscaCliente} onChange={(e) => setBuscaCliente(e.target.value)} />
          <small className={buscaCliente.length >= 80 ? 'field-hint limit-reached' : 'field-hint'}>{buscaCliente.length >= 80 ? 'Limite de caracteres atingido.' : 'Digite o nome do cliente.'}<strong>{buscaCliente.length}/80</strong></small>
        </div>
      </div>
      <Table columns={[
        { key: 'protocolo', label: 'Pedido' },
        { key: 'clienteNome', label: 'Cliente' },
        { key: 'produto', label: 'Produto' },
        { key: 'valor', label: 'Valor', render: (row) => currency(row.valor) },
        { key: 'status', label: 'Status', render: (row) => <StatusBadge status={row.status} /> },
        { key: 'acao', label: 'Alterar status', render: (row) => <select value={row.status} onChange={(event) => alterarStatus(row.id, event.target.value)}>{statusOptions.map((status) => <option key={status} value={status}>{status}</option>)}</select> },
      ]} rows={pedidos} />
    </section>
  )
}
