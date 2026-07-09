import { useCliente } from '../../context/ClienteContext.jsx'

export default function Historico() {
  const { portal } = useCliente()

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Histórico</span>
        <h1>Atendimentos passados</h1>
        <p>Serviços, profissionais, valores pagos e observações.</p>
      </header>

      <div className="gendaz-table">
        {portal.historico.map((item) => (
          <article key={item.id} className="gendaz-table__row">
            <strong>{item.servico}</strong>
            <span>{item.profissional}</span>
            <span>{new Date(`${item.data}T12:00:00`).toLocaleDateString('pt-BR')}</span>
            <span>{item.valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</span>
            <small>{item.observacao}</small>
          </article>
        ))}
      </div>
    </section>
  )
}
