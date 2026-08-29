import StatusBadge from './StatusBadge.jsx'
import { currency } from '../services/localStore.js'
import { exibirTelefone } from '../utils/phoneUtils.js'

export default function ClientPanel({ cliente, agendamentos }) {
  if (!cliente) return <aside className="client-panel">Selecione uma conversa.</aside>
  const historico = agendamentos.filter((item) => item.clienteId === cliente.id)
  return (
    <aside className="client-panel">
      <h2>{cliente.nome}</h2>
      <span>{exibirTelefone(cliente.telefone)}</span>
      <dl>
        <div><dt>Total gasto</dt><dd>{currency(cliente.totalGasto)}</dd></div>
        <div><dt>Último atendimento</dt><dd>{historico[0]?.data || 'Sem registro'}</dd></div>
      </dl>
      <h3>Histórico</h3>
      {historico.map((item) => (
        <div className="mini-row" key={item.id}>
          <span>{item.data} às {item.horaInicio}</span>
          <StatusBadge status={item.status} />
        </div>
      ))}
      <h3>Observações internas</h3>
      <p>{cliente.observações}</p>
    </aside>
  )
}
