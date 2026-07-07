import './hero-animation.css'

const agendaRows = [['10:00 - 11:45', 'Roberto Silva', 'Corte + Barba', 'Finalizado']]

const overviewCards = [
  ['Agendamentos', '98'],
  ['Confirmados', '89'],
  ['Clientes', '12'],
  ['Faturamento', 'R$ 6.000,00'],
]

const financeRows = [
  ['Pedro Silva', 'R$ 120,00', 'Cartão', 'Pago'],
  ['João Santos', 'R$ 45,00', 'PIX', 'Pendente'],
]

export default function HeroAnimation() {
  return (
    <div className="hero-product-visual hero-animation" aria-hidden="true">
      <article className="hero-mockup-card hero-mockup-card-agenda">
        <div className="hero-window-dots">
          <span />
          <span />
          <span />
        </div>
        <div className="hero-mockup-head">
          <div>
            <span className="mockup-label">visão rápida</span>
            <strong className="mockup-title">Agenda</strong>
            <span className="mockup-small">Conferindo os compromissos do dia</span>
          </div>
          <div className="mockup-chip-row">
            <span>Buscar</span>
            <span>Filtros</span>
            <span>Nova</span>
          </div>
        </div>
        <div className="hero-agenda-filters">
          <span>Hoje</span>
          <span>Profissional</span>
          <span>Status</span>
          <span>Todos</span>
        </div>
        <div className="hero-agenda-list">
          {agendaRows.map((row) => (
            <div key={row[0]} className="hero-agenda-row">
              <div className="hero-agenda-time">
                <strong>{row[0]}</strong>
                <small>30/07/2034</small>
              </div>
              <span>{row[1]}</span>
              <span>{row[2]}</span>
              <small className="hero-status-pill is-finalizado">{row[3]}</small>
            </div>
          ))}
        </div>
      </article>

      <article className="hero-mockup-card hero-mockup-card-overview">
        <div className="hero-window-dots">
          <span />
          <span />
          <span />
        </div>
        <div className="hero-mockup-head">
          <div>
            <span className="mockup-label">dashboard</span>
            <strong className="mockup-title">Visão geral</strong>
            <span className="mockup-small">Resumo do negócio neste mês</span>
          </div>
          <div className="mockup-chip-row">
            <span>Mês atual</span>
            <span>Exportar</span>
          </div>
        </div>
        <div className="hero-overview-metrics">
          {overviewCards.map((item) => (
            <article key={item[0]} className="hero-metric-card">
              <span>{item[0]}</span>
              <strong>{item[1]}</strong>
            </article>
          ))}
        </div>
        <div className="hero-overview-bottom">
          <div className="hero-overview-revenue">
            <span>Faturamento</span>
            <strong>R$ 6.000,00</strong>
          </div>
          <div className="hero-overview-chart">
            <div className="hero-chart-bars">
              <span />
              <span />
              <span />
              <span />
              <span />
            </div>
            <small>Resumo diário</small>
          </div>
        </div>
      </article>

      <article className="hero-mockup-card hero-mockup-card-finance">
        <div className="hero-window-dots">
          <span />
          <span />
          <span />
        </div>
        <div className="hero-mockup-head">
          <div>
            <span className="mockup-label">financeiro</span>
            <strong className="mockup-title">Financeiro</strong>
            <span className="mockup-small">Pagamentos e recebimentos</span>
          </div>
          <div className="mockup-chip-row">
            <span>Junho</span>
            <span>Filtros</span>
          </div>
        </div>
        <div className="hero-finance-summary">
          <article className="hero-finance-box">
            <span>Recebido</span>
            <strong>R$ 4.315,00</strong>
          </article>
          <article className="hero-finance-box">
            <span>Pendências</span>
            <strong>2</strong>
          </article>
        </div>
        <div className="hero-float-table">
          <div className="hero-float-table-head">
            <span>Cliente</span>
            <span>Valor</span>
            <span>Status</span>
          </div>
          {financeRows.map((row) => (
            <div key={row[0]} className="hero-float-table-row">
              <strong>{row[0]}</strong>
              <span>{row[1]}</span>
              <small className={`hero-status-pill ${row[3] === 'Pago' ? 'is-pago' : 'is-pendente'}`}>{row[3]}</small>
            </div>
          ))}
        </div>
      </article>
    </div>
  )
}
