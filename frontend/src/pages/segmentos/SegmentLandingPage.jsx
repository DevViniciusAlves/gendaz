import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft, UserPlus } from 'lucide-react'
import SEO, { SITE_BASE } from '../../components/SEO.jsx'
import logoWhite from '../../assets/logos/gendazpng.png'
import './segment-landing.css'

const FEATURES = [
  { title: 'Agenda', description: 'Horários, confirmações, cancelamentos e remarcações organizados.' },
  { title: 'Clientes', description: 'Histórico de atendimentos e dados organizados em um só lugar.' },
  { title: 'Financeiro', description: 'Recebidos, pendências e movimentação acompanhados no painel.' },
  { title: 'Pagamentos', description: 'Controle claro do que já entrou e do que ainda está pendente.' },
  { title: 'CRM', description: 'Acompanhe frequência e perceba quem está deixando de retornar.' },
  { title: 'GendazIA', description: 'Insights sobre o seu próprio negócio para decidir com mais clareza.' },
  { title: 'Serviços', description: 'Catálogo de serviços organizado junto com a agenda.' },
  { title: 'Profissionais', description: 'Horários e equipe organizados sem conflito.' },
]

const IA_EXAMPLES = [
  '“Quais clientes devo tentar recuperar esta semana?”',
  '“Como está meu financeiro este mês?”',
  '“Quais serviços estão com menos movimento?”',
]

function formatBRL(value) {
  if (!Number.isFinite(value)) return 'R$ 0'
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 2 })
}

function Simulator({ title, description, kind }) {
  const [clients, setClients] = useState(40)
  const [ticket, setTicket] = useState(120)
  const [lost, setLost] = useState(8)
  const [slotValue, setSlotValue] = useState(120)
  const [emptySlots, setEmptySlots] = useState(5)

  if (kind === 'slots') {
    const monthly = (Number(slotValue) || 0) * (Number(emptySlots) || 0) * 4
    return (
      <section className="seg-section" aria-label="Simulador de horários">
        <div className="seg-card">
          <span className="seg-kicker">Simulador</span>
          <h2>{title}</h2>
          <p className="seg-muted">{description}</p>
          <div className="seg-sim-grid">
            <label className="seg-field">
              <span>Valor médio da reserva (R$)</span>
              <input type="number" min="0" value={slotValue} onChange={(e) => setSlotValue(e.target.value)} />
            </label>
            <label className="seg-field">
              <span>Horários vazios por semana</span>
              <input type="number" min="0" value={emptySlots} onChange={(e) => setEmptySlots(e.target.value)} />
            </label>
          </div>
          <div className="seg-sim-result">
            <div>
              <small>Valor potencial associado aos horários informados</small>
              <strong>{formatBRL(monthly)}</strong>
            </div>
          </div>
          <p className="seg-disclaimer">
            Valor potencial associado aos horários informados (valor × horários × 4 semanas). Simulação baseada
            apenas nos valores informados por você. Sem promessa de resultado.
          </p>
        </div>
      </section>
    )
  }

  const monthly = (Number(lost) || 0) * (Number(ticket) || 0)
  const yearly = monthly * 12

  return (
    <section className="seg-section" aria-label="Simulador de oportunidade">
      <div className="seg-card">
        <span className="seg-kicker">Simulador</span>
        <h2>{title}</h2>
        <p className="seg-muted">{description}</p>
        <div className="seg-sim-grid">
          <label className="seg-field">
            <span>Clientes atendidos por mês</span>
            <input type="number" min="0" value={clients} onChange={(e) => setClients(e.target.value)} />
          </label>
          <label className="seg-field">
            <span>Ticket médio (R$)</span>
            <input type="number" min="0" value={ticket} onChange={(e) => setTicket(e.target.value)} />
          </label>
          <label className="seg-field">
            <span>Clientes que deixaram de retornar</span>
            <input type="number" min="0" value={lost} onChange={(e) => setLost(e.target.value)} />
          </label>
        </div>
        <div className="seg-sim-result">
          <div>
            <small>Valor mensal associado a esses clientes</small>
            <strong>{formatBRL(monthly)}</strong>
          </div>
          <div>
            <small>Valor anual associado a esses clientes</small>
            <strong>{formatBRL(yearly)}</strong>
          </div>
        </div>
        <p className="seg-disclaimer">
          Valor potencial associado aos atendimentos informados. Simulação baseada apenas nos valores informados
          por você. Não representa garantia de recuperação, faturamento ou resultado.
        </p>
      </div>
    </section>
  )
}

export default function SegmentLandingPage({ segment }) {
  const jsonLd = useMemo(() => {
    if (!segment) return null
    const url = `${SITE_BASE}${segment.path}`
    return [
      {
        '@context': 'https://schema.org',
        '@type': 'SoftwareApplication',
        name: 'Gendaz',
        applicationCategory: 'BusinessApplication',
        operatingSystem: 'Web',
        url,
      },
      {
        '@context': 'https://schema.org',
        '@type': 'BreadcrumbList',
        itemListElement: [
          { '@type': 'ListItem', position: 1, name: 'Início', item: `${SITE_BASE}/` },
          { '@type': 'ListItem', position: 2, name: segment.eyebrow, item: url },
        ],
      },
    ]
  }, [segment])

  useEffect(() => {
    window.scrollTo({
      top: 0,
      left: 0,
      behavior: 'auto',
    })
  }, [segment?.path])

  if (!segment) return null
  const canonical = `${SITE_BASE}${segment.path}`

  return (
    <main className="marketing-page seg-page">
      <SEO
        title={segment.seoTitle}
        description={segment.seoDescription}
        canonical={canonical}
        ogTitle={segment.seoTitle}
        ogDescription={segment.seoDescription}
        ogUrl={canonical}
        jsonLd={jsonLd}
      />

      {/* 1. Navbar — mesmo padrão visual da Home */}
      <header className="marketing-nav-gendo">
        <div className="marketing-nav-gendo-shell">
          <Link to="/" className="marketing-brand-gendo" aria-label="Gendaz — início">
            <img src={logoWhite} alt="gendaz" className="nav-logo-gendo" />
          </Link>
          <nav className="marketing-nav-links-gendo" aria-label="Navegação principal">
            <Link to="/">Início</Link>
            <a href="/#sobre">Sobre</a>
            <a href="/#segmentos">Segmentos</a>
            <a href="/#planos">Planos</a>
            <a href="/#suporte">Suporte</a>
            <a href="/#contato">Contato</a>
          </nav>
          <div className="marketing-actions-gendo">
            <Link to="/login" className="secondary-link-gendo">Entrar</Link>
            <Link to="/criar-conta" className="primary-link-gendo"><UserPlus size={16} />Criar conta</Link>
          </div>
        </div>
      </header>

      {/* 2. Hero */}
      <section className="seg-hero">
        <div className="seg-hero-inner">
          <div>
            <Link to="/" className="seg-back-home">
              <ArrowLeft size={16} />
              Voltar ao início
            </Link>
            <span className="seg-kicker">{segment.eyebrow}</span>
            <h1>{segment.h1}</h1>
            <p className="seg-lead">{segment.heroDescription}</p>
            <div className="seg-cta-row">
              <Link to="/criar-conta" className="seg-btn-primary">Criar conta grátis</Link>
              <a href="/#planos" className="seg-btn-secondary">Ver planos</a>
            </div>
          </div>
          {/* 3. Preview visual da agenda */}
          <div className="seg-agenda-mock" aria-label={`Exemplo de agenda — ${segment.eyebrow}`}>
            <div className="seg-agenda-head">
              <small>AGENDA DE HOJE</small>
              <strong>Gendaz</strong>
            </div>
            <ul>
              {segment.agenda.map((item) => (
                <li key={`${item.time}-${item.service}`}>
                  <span className="seg-time">{item.time}</span>
                  <span className="seg-service">{item.service}</span>
                  <span className="seg-status">{item.status}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </section>

      {/* 4. Problemas */}
      <section className="seg-section" aria-label="Problemas comuns">
        <h2>{segment.problemsTitle}</h2>
        <div className="seg-grid-3">
          {segment.problems.map((p, i) => (
            <article key={p.title} className="seg-card seg-problem">
              <span className="seg-num">{String(i + 1).padStart(2, '0')}</span>
              <h3>{p.title}</h3>
              <p className="seg-muted">{p.description}</p>
            </article>
          ))}
        </div>
      </section>

      {/* 5. Simulador */}
      {segment.simulator && (
        <Simulator title={segment.simulatorTitle} description={segment.simulatorDescription} kind={segment.simulatorKind} />
      )}

      {/* 6. Como o Gendaz ajuda */}
      <section className="seg-section" aria-label="Como o Gendaz ajuda">
        <h2>{segment.helpTitle}</h2>
        <div className="seg-grid-3">
          {segment.helpItems.map((item) => (
            <article key={item.title} className="seg-card">
              <h3>{item.title}</h3>
              <p className="seg-muted">{item.description}</p>
            </article>
          ))}
        </div>
      </section>

      {/* 7. CRM */}
      <section className="seg-section" aria-label="Acompanhamento de clientes">
        <div className="seg-card seg-crm">
          <span className="seg-kicker">Clientes e recorrência</span>
          <h2>Perceba quem está voltando — e quem está sumindo.</h2>
          <p className="seg-muted">
            O Gendaz ajuda você a acompanhar seus clientes e perceber quem está reduzindo a frequência ou
            deixando de retornar.
          </p>
          <div className="seg-crm-tags">
            <span>Novos</span>
            <span>Regulares</span>
            <span>Em risco</span>
          </div>
        </div>
      </section>

      {/* 8. GendazIA */}
      <section className="seg-section" aria-label="GendazIA">
        <div className="seg-card">
          <span className="seg-kicker">GendazIA</span>
          <h2>Pergunte ao seu próprio negócio.</h2>
          <p className="seg-muted">
            A GendazIA analisa as informações do seu próprio negócio e apresenta insights para ajudar na rotina.
          </p>
          <ul className="seg-ia-list">
            {IA_EXAMPLES.map((q) => (
              <li key={q}>{q}</li>
            ))}
          </ul>
        </div>
      </section>

      {/* 9. Funcionalidades */}
      <section className="seg-section" aria-label="Funcionalidades principais">
        <h2>Funcionalidades principais</h2>
        <div className="seg-grid-4">
          {FEATURES.map((f) => (
            <article key={f.title} className="seg-card seg-feature">
              <h3>{f.title}</h3>
              <p className="seg-muted">{f.description}</p>
            </article>
          ))}
        </div>
      </section>

      {/* 10. FAQ */}
      <section className="seg-section" aria-label="Perguntas frequentes">
        <h2>Perguntas frequentes</h2>
        <div className="seg-faq">
          {segment.faq.map((item) => (
            <details key={item.q} className="seg-faq-item">
              <summary>{item.q}</summary>
              <p className="seg-muted">{item.a}</p>
            </details>
          ))}
        </div>
      </section>

      {/* 11. CTA final */}
      <section className="seg-section" aria-label="Começar agora">
        <div className="seg-card seg-cta-final">
          <h2>Organize seu negócio sem aumentar a bagunça.</h2>
          <p className="seg-muted">Crie sua conta e organize agenda, clientes e pagamentos em um só lugar.</p>
          <Link to="/criar-conta" className="seg-btn-primary">Criar conta grátis</Link>
        </div>
      </section>

      {/* 12. Veja também — linkagem interna */}
      {Array.isArray(segment.related) && segment.related.length > 0 && (
        <section className="seg-section" aria-label="Veja também">
          <h2>Veja também</h2>
          <div className="seg-related">
            {segment.related.map((r) => (
              <Link key={r.path} to={r.path} className="seg-related-link">{r.label}</Link>
            ))}
          </div>
        </section>
      )}

      {/* 13. Footer */}
      <footer className="marketing-footer">
        <small>gendaz</small>
        <div>
          <Link to="/termos-de-uso">Termos de Uso</Link>
          <Link to="/politica-de-privacidade">Política de Privacidade</Link>
        </div>
      </footer>
    </main>
  )
}
