import StatusBadge from '../components/StatusBadge.jsx'
import Table from '../components/Table.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { currency } from '../services/localStore.js'

export default function Produtos() {
  const [data] = useLocalData('produtos')

  return (
    <section className="page">
      <div className="page-title">
        <span className="section-kicker">Catálogo</span>
        <h1>Produtos</h1>
        <p>Produtos eletrônicos de teste para validar pedidos e movimentações internas.</p>
      </div>
      <Table columns={[
        { key: 'nome', label: 'Produto' },
        { key: 'categoria', label: 'Categoria' },
        { key: 'sku', label: 'SKU' },
        { key: 'estoque', label: 'Estoque' },
        { key: 'valor', label: 'Valor', render: (row) => currency(row.valor) },
        { key: 'status', label: 'Status', render: (row) => <StatusBadge status={row.status} /> },
      ]} rows={data.produtos} />
    </section>
  )
}
