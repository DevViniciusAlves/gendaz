import { Ticket, BadgePercent } from 'lucide-react'
import { useCliente } from '../../context/ClienteContext.jsx'

export default function Beneficios() {
  const { portal } = useCliente()

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Benefícios</span>
        <h1>Promoções e cupons</h1>
        <p>Ofertas cadastradas pelo estabelecimento aparecem automaticamente aqui.</p>
      </header>

      <div className="gendaz-grid gendaz-grid--two">
        <article className="gendaz-panel">
          <div className="gendaz-panel__head"><BadgePercent size={18} /><h2>Promoções</h2></div>
          <div className="gendaz-stack">
            {portal.beneficios.promocoes.map((item) => (
              <div key={item.id} className="gendaz-mini-card">
                <strong>{item.titulo}</strong>
                <span>{item.descricao}</span>
              </div>
            ))}
          </div>
        </article>

        <article className="gendaz-panel">
          <div className="gendaz-panel__head"><Ticket size={18} /><h2>Cupons</h2></div>
          <div className="gendaz-stack">
            {portal.beneficios.cupons.map((item) => (
              <div key={item.id} className="gendaz-mini-card">
                <strong>{item.codigo}</strong>
                <span>{item.descricao}</span>
              </div>
            ))}
          </div>
        </article>
      </div>
    </section>
  )
}
