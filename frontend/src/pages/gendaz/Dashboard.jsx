import { ArrowRight, BellRing, CalendarDays, Gift, Sparkles, Clock3 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useCliente } from '../../context/ClienteContext.jsx'

export default function Dashboard() {
  const { portal } = useCliente()

  return (
    <section className="gendaz-page gendaz-dashboard">
      <header className="gendaz-page__header gendaz-page__header--hero">
        <span className="gendaz-kicker">Dashboard</span>
        <h1>Bom te ver novamente, {portal.cliente.nome}.</h1>
        <p>Seu relacionamento com a empresa em um único espaço, com agenda, histórico, IA e benefícios.</p>
      </header>

      <div className="gendaz-grid">
        <article className="gendaz-card gendaz-card--highlight">
          <Clock3 size={18} />
          <span>Último atendimento</span>
          <strong>{portal.dashboard.ultimoAtendimento}</strong>
        </article>
        <article className="gendaz-card">
          <CalendarDays size={18} />
          <span>Próximo atendimento</span>
          <strong>{portal.dashboard.proximoAtendimento}</strong>
          <small>{portal.dashboard.proximoAtendimentoDetalhe}</small>
        </article>
        <article className="gendaz-card">
          <Gift size={18} />
          <span>Recompensa</span>
          <strong>{portal.dashboard.recompensas}</strong>
        </article>
        <article className="gendaz-card">
          <BellRing size={18} />
          <span>Notificações</span>
          <strong>{portal.dashboard.notificacoes.length}</strong>
          <small>Novidades ativas para seu perfil.</small>
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
            <Gift size={18} />
            <h2>Promoção ativa</h2>
          </div>
          <div className="gendaz-mini-card">
            <strong>{portal.dashboard.promoAtual.titulo}</strong>
            <span>{portal.dashboard.promoAtual.descricao}</span>
          </div>
          <Link to="/meu-gendaz/beneficios" className="gendaz-btn gendaz-btn--ghost">
            Ver benefícios <ArrowRight size={16} />
          </Link>
        </article>
      </div>

      <div className="gendaz-grid gendaz-grid--two">
        <article className="gendaz-panel">
          <div className="gendaz-panel__head">
            <BellRing size={18} />
            <h2>Últimas notificações</h2>
          </div>
          <ul className="gendaz-list">
            {portal.dashboard.notificacoes.map((item) => <li key={item}>{item}</li>)}
          </ul>
        </article>

        <article className="gendaz-panel">
          <div className="gendaz-panel__head">
            <CalendarDays size={18} />
            <h2>Agendar novamente</h2>
          </div>
          <p>Repetir a última visita é a forma mais rápida de voltar.</p>
          <Link to="/meu-gendaz/agenda" className="gendaz-btn gendaz-btn--primary">
            Agendar novamente
          </Link>
        </article>
      </div>
    </section>
  )
}
