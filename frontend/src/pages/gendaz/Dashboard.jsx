import { CalendarDays, Gift, Sparkles, BellRing, Clock3 } from 'lucide-react'
import { useCliente } from '../../context/ClienteContext.jsx'

export default function Dashboard() {
  const { portal } = useCliente()

  return (
    <section className="gendaz-page gendaz-dashboard">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Dashboard</span>
        <h1>Bom te ver novamente, {portal.cliente.nome}.</h1>
        <p>Seu relacionamento com o estabelecimento, organizado em um único lugar.</p>
      </header>

      <div className="gendaz-grid">
        <article className="gendaz-card">
          <Clock3 size={18} />
          <span>Último atendimento</span>
          <strong>{portal.dashboard.ultimoAtendimento}</strong>
        </article>
        <article className="gendaz-card">
          <CalendarDays size={18} />
          <span>Próximo atendimento</span>
          <strong>{portal.dashboard.proximoAtendimento}</strong>
        </article>
        <article className="gendaz-card">
          <Gift size={18} />
          <span>Benefícios</span>
          <strong>{portal.dashboard.recompensas}</strong>
        </article>
        <article className="gendaz-card">
          <BellRing size={18} />
          <span>Notificações</span>
          <strong>{portal.dashboard.notificacoes[0]}</strong>
        </article>
      </div>

      <div className="gendaz-grid gendaz-grid--two">
        <article className="gendaz-panel">
          <div className="gendaz-panel__head">
            <Sparkles size={18} />
            <h2>Sugestões da IA</h2>
          </div>
          <ul className="gendaz-list">
            {portal.dashboard.sugestoes.map((item) => <li key={item}>{item}</li>)}
          </ul>
        </article>

        <article className="gendaz-panel">
          <div className="gendaz-panel__head">
            <BellRing size={18} />
            <h2>Últimas notificações</h2>
          </div>
          <ul className="gendaz-list">
            {portal.dashboard.notificacoes.map((item) => <li key={item}>{item}</li>)}
          </ul>
        </article>
      </div>
    </section>
  )
}
