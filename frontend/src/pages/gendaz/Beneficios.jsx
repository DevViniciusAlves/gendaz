import { BadgePercent, Ticket, Gift, Coins, Users } from 'lucide-react'
import { useCliente } from '../../context/ClienteContext.jsx'

const proximosBeneficios = [
  { icon: BadgePercent, titulo: 'Promoções', descricao: 'Ofertas cadastradas pelo estabelecimento aparecem automaticamente aqui.' },
  { icon: Ticket, titulo: 'Cupons', descricao: 'Use cupons ativos com um toque.' },
  { icon: Gift, titulo: 'Programa de fidelidade', descricao: 'Futuro módulo de pontos e recompensas.' },
  { icon: Coins, titulo: 'Cashback', descricao: 'Recurso futuro para valor de volta.' },
  { icon: Users, titulo: 'Indique um amigo', descricao: 'Área pronta para campanhas de indicação.' },
]

export default function Beneficios() {
  const { portal } = useCliente()

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Benefícios</span>
        <h1>Promoções e cupons</h1>
        <p>Área de fidelização com promoções do SaaS e espaço preparado para evolução futura.</p>
      </header>

      <div className="gendaz-grid gendaz-grid--two">
        <article className="gendaz-panel">
          <div className="gendaz-panel__head"><BadgePercent size={18} /><h2>Promoção ativa</h2></div>
          <div className="gendaz-mini-card">
            <strong>{portal.dashboard.promoAtual.titulo}</strong>
            <span>{portal.dashboard.promoAtual.descricao}</span>
          </div>
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
          <div className="gendaz-panel__head"><Ticket size={18} /><h2>Cupons disponíveis</h2></div>
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

      <div className="gendaz-grid gendaz-grid--two">
        {proximosBeneficios.map(({ icon: Icon, titulo, descricao }) => (
          <article className="gendaz-card" key={titulo}>
            <Icon size={18} />
            <strong>{titulo}</strong>
            <span>{descricao}</span>
          </article>
        ))}
      </div>
    </section>
  )
}
