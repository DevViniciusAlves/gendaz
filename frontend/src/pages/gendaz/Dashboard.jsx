import { useContext, useState, useEffect } from 'react'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'
import { Calendar, Clock, Gift, AlertCircle, ArrowRight, Sparkles, BellRing } from 'lucide-react'
import { Link } from 'react-router-dom'

export default function Dashboard() {
  const { cliente, dashboard, agendamentos, carregando, erro } = useContext(ClienteGendazContext)

  if (carregando) return <div className="gendaz-loading">Carregando dashboard...</div>
  if (erro) return <div className="gendaz-erro">{erro}</div>

  const nome = cliente?.nome || 'cliente'
  const proximo = dashboard?.proximoAgendamento || (agendamentos && agendamentos.length > 0 ? agendamentos[0] : null)
  const ultimos = dashboard?.ultimosAtendimentos || []
  const promos = dashboard?.promocoes || []
  const notifs = dashboard?.notificacoes || []

  return (
    <section className="gendaz-page gendaz-dashboard">
      <header className="gendaz-page__header gendaz-page__header--hero">
        <span className="gendaz-kicker">Dashboard</span>
        <h1>Bom te ver novamente, {nome}. 👋</h1>
        <p>Seu relacionamento com a empresa em um único espaço, com agenda, histórico, IA e benefícios.</p>
      </header>

      <div className="gendaz-grid gendaz-grid--four">
        {proximo && (
          <article className="gendaz-card card-proxima gendaz-card--highlight">
            <Calendar size={18} />
            <span>Próximo agendamento</span>
            <strong>{proximo.data ? new Date(`${proximo.data}T12:00:00`).toLocaleDateString('pt-BR') : '—'} às {proximo.hora || '—'}</strong>
            <small>{proximo.servico || proximo.servicoNome || 'Serviço'} • {proximo.profissional || proximo.profissionalNome || 'Profissional'}</small>
            <span className={`gendaz-status gendaz-status--${(proximo.status || '').toLowerCase()}`}>{proximo.status || 'Confirmado'}</span>
          </article>
        )}

        <article className="gendaz-card">
          <Clock size={18} />
          <span>Último atendimento</span>
          {ultimos.length > 0 ? (
            <>
              <strong>{ultimos[0].servico || ultimos[0].servicoNome || 'Serviço'}</strong>
              <small>{ultimos[0].data ? new Date(`${ultimos[0].data}T12:00:00`).toLocaleDateString('pt-BR') : '—'} • {ultimos[0].profissional || ultimos[0].profissionalNome || 'Profissional'}</small>
            </>
          ) : (
            <>
              <strong>—</strong>
              <small>Nenhum atendimento registrado</small>
            </>
          )}
        </article>

        {notifs.length > 0 && (
          <article className="gendaz-card">
            <BellRing size={18} />
            <span>Notificações</span>
            <strong>{notifs.filter((n) => !n.lida).length} não lidas</strong>
            <small>{notifs[0]?.mensagem || 'Sem notificações'}</small>
          </article>
        )}

        {promos.length > 0 && (
          <article className="gendaz-card card-promocoes">
            <Gift size={18} />
            <span>Promoção ativa</span>
            <strong>{promos[0].titulo}</strong>
            <small>{promos[0].desconto}% OFF • Válido até {promos[0].validade}</small>
          </article>
        )}

        {!proximo && notifs.length === 0 && promos.length === 0 && (
          <article className="gendaz-card">
            <AlertCircle size={18} />
            <span>Bem-vindo</span>
            <strong>{nome}</strong>
            <small>Agende seu primeiro serviço!</small>
          </article>
        )}
      </div>

      {ultimos.length > 0 && (
        <div className="gendaz-grid gendaz-grid--two">
          <article className="gendaz-panel card-historico-rapido">
            <div className="gendaz-panel__head">
              <Clock size={18} />
              <h2>Últimos atendimentos</h2>
            </div>
            {ultimos.map((item, idx) => (
              <div key={idx} className="atendimento-item gendaz-mini-card">
                <strong>{item.servico || item.servicoNome || 'Serviço'}</strong>
                <small>{item.profissional || item.profissionalNome || 'Profissional'} em {item.data ? new Date(`${item.data}T12:00:00`).toLocaleDateString('pt-BR') : '—'}</small>
                {item.valor != null && <span>R$ {Number(item.valor).toFixed(2)}</span>}
              </div>
            ))}
          </article>

          <article className="gendaz-panel">
            <div className="gendaz-panel__head">
              <Calendar size={18} />
              <h2>Agendar novamente</h2>
            </div>
            <p>Repetir a última visita é a forma mais rápida de voltar.</p>
            <Link to="/meu-gendaz/agenda" className="gendaz-btn gendaz-btn--primary">
              Agendar novamente <ArrowRight size={16} />
            </Link>
          </article>
        </div>
      )}

      {promos.length > 0 && (
        <div className="gendaz-grid gendaz-grid--two">
          <article className="gendaz-panel">
            <div className="gendaz-panel__head">
              <Gift size={18} />
              <h2>Promoções disponíveis</h2>
            </div>
            <div className="gendaz-stack">
              {promos.map((promo) => (
                <div key={promo.id} className="gendaz-mini-card">
                  <div className="gendaz-mini-card__header">
                    <strong>{promo.titulo}</strong>
                    <span className="gendaz-desconto">{promo.desconto}% OFF</span>
                  </div>
                  <span>{promo.descricao}</span>
                  {promo.cupom && <small>Cupom: {promo.cupom}</small>}
                </div>
              ))}
            </div>
            <Link to="/meu-gendaz/beneficios" className="gendaz-btn gendaz-btn--ghost">
              Ver todos os benefícios <ArrowRight size={16} />
            </Link>
          </article>

          <article className="gendaz-panel">
            <div className="gendaz-panel__head">
              <Sparkles size={18} />
              <h2>Assistente IA</h2>
            </div>
            <p>Precisa de ajuda? Converse com a IA sobre agendamentos, preços e serviços.</p>
            <Link to="/meu-gendaz/ia" className="gendaz-btn gendaz-btn--primary">
              Abrir assistente <ArrowRight size={16} />
            </Link>
          </article>
        </div>
      )}

      {notifs.length > 0 && (
        <article className="gendaz-panel">
          <div className="gendaz-panel__head">
            <BellRing size={18} />
            <h2>Notificações recentes</h2>
          </div>
          <ul className="gendaz-list">
            {notifs.slice(0, 5).map((notif) => (
              <li key={notif.id} className={notif.lida ? 'gendaz-list__item--lida' : ''}>
                {notif.mensagem} <small>— {notif.data}</small>
              </li>
            ))}
          </ul>
        </article>
      )}
    </section>
  )
}
