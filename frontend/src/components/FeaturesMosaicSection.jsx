import { Brain, Users, CalendarDays, DollarSign, Star, Clock, TrendingUp, Check, FileSpreadsheet, Coins, ReceiptText, Mail, Gift, Bell, MessageSquare } from 'lucide-react'
import ScrollReveal from './ScrollReveal.jsx'

const cards = [
  {
    title: 'Insights Inteligentes',
    description: 'A GendazIA analisa sua empresa e identifica oportunidades para melhorar seus resultados.',
    icon: Brain,
    kind: 'insights',
  },
  {
    title: 'Fechamento mensal em dia.',
    description: 'Sem precisar bater cabeça, basta exportar seus dados em CSV.',
    icon: FileSpreadsheet,
    kind: 'flow',
  },
  {
    title: 'CRM Inteligente',
    description: 'Acompanhe seus clientes, identifique quem está ativo e recupere quem parou de agendar.',
    icon: Users,
    kind: 'crm',
  },
]

const orbitModules = [
  { icon: Users,       label: 'Clientes',        angle: 270 },
  { icon: CalendarDays,label: 'Agenda',           angle: 342 },
  { icon: DollarSign,  label: 'Financeiro',       angle: 54  },
  { icon: Star,        label: 'Serviços',         angle: 126 },
  { icon: Clock,       label: 'Comparecimento',   angle: 198 },
]

const ORBIT_R = 95
const CX = 145
const CY = 130

function toXY(angleDeg, r) {
  const rad = (angleDeg - 90) * (Math.PI / 180)
  return { x: CX + r * Math.cos(rad), y: CY + r * Math.sin(rad) }
}

function FeatureCard({ title, description, icon: Icon, kind }) {
  return (
    <article className="feature-spotlight-card">
      <div className="feature-spotlight-card__header">
        <h3>{title}</h3>
        <p>{description}</p>
      </div>

      <div className="feature-spotlight-card__visual" aria-hidden="true">

        {/* ── INSIGHTS CARD ─────────────────────────────── */}
        {kind === 'insights' && (
          <div className="feature-spotlight-visual feature-spotlight-visual--insights">

            {/* Orbit illustration */}
            <div className="insights-orbit-wrap">
              <svg className="insights-orbit-svg" viewBox="0 0 290 260" fill="none">
                {/* orbit circle */}
                <circle cx={CX} cy={CY} r={ORBIT_R} stroke="rgba(255,255,255,0.06)" strokeWidth="1" strokeDasharray="4 4" />

                {/* connection lines from each module to center */}
                {orbitModules.map((m) => {
                  const { x, y } = toXY(m.angle, ORBIT_R - 2)
                  return (
                    <line
                      key={m.label}
                      x1={x} y1={y}
                      x2={CX} y2={CY}
                      stroke="rgba(255,255,255,0.10)"
                      strokeWidth="1"
                    />
                  )
                })}

                {/* animated dots flowing toward center */}
                {orbitModules.map((m, i) => {
                  const { x: sx, y: sy } = toXY(m.angle, ORBIT_R - 2)
                  return (
                    <circle key={`dot-${i}`} r="2" fill="rgba(255,255,255,0.55)">
                      <animateMotion
                        dur={`${2.2 + i * 0.35}s`}
                        repeatCount="indefinite"
                        path={`M ${sx} ${sy} L ${CX} ${CY}`}
                      />
                    </circle>
                  )
                })}
              </svg>

              {/* Center — GendazIA brain node */}
              <div className="insights-center-node">
                <Brain size={18} className="insights-brain-icon" />
                <span className="insights-center-label">GendazIA</span>
              </div>

              {/* Orbit module nodes */}
              {orbitModules.map((m) => {
                const { x, y } = toXY(m.angle, ORBIT_R)
                const OrbIcon = m.icon
                return (
                  <div
                    key={m.label}
                    className="insights-orbit-node"
                    style={{
                      left: `calc(${(x / 290) * 100}% - 28px)`,
                      top:  `calc(${(y / 260) * 100}% - 28px)`,
                    }}
                  >
                    <OrbIcon size={13} />
                    <span>{m.label}</span>
                  </div>
                )
              })}
            </div>

            {/* Smart Summary card */}
            <div className="insights-summary-card">
              <span className="insights-summary-title">Resumo Inteligente</span>
              <ul className="insights-summary-list">
                <li><TrendingUp size={11} /><span>Receita crescendo</span></li>
                <li><CalendarDays size={11} /><span>Quinta-feira é seu melhor dia</span></li>
                <li><Users size={11} /><span>Apenas 3 clientes estão em risco</span></li>
                <li><Star size={11} /><span>Barba Premium é o mais lucrativo</span></li>
              </ul>
            </div>

            {/* Health indicator */}
            <div className="insights-health">
              <div className="insights-health-text">
                <span className="insights-health-label">Saúde da Empresa</span>
                <span className="insights-health-tag">Excelente</span>
              </div>
              <div className="insights-health-row">
                <div className="insights-health-bar">
                  <div className="insights-health-fill" />
                </div>
                <strong className="insights-health-pct">94%</strong>
              </div>
            </div>

          </div>
        )}

        {/* ── FECHAMENTO CARD ───────────────────────────── */}
        {kind === 'flow' && (
          <div className="feature-spotlight-visual feature-spotlight-visual--closing">
            <div className="closing-illustration">
              <svg className="closing-flow-lines" viewBox="0 0 200 160" fill="none">
                <path d="M 30 40 Q 60 40 85 75" stroke="rgba(255, 255, 255, 0.12)" strokeWidth="1.5" strokeDasharray="3 3" fill="none" />
                <path d="M 170 40 Q 140 40 115 75" stroke="rgba(255, 255, 255, 0.12)" strokeWidth="1.5" strokeDasharray="3 3" fill="none" />
                <path d="M 100 95 L 100 125" stroke="rgba(255, 255, 255, 0.12)" strokeWidth="1.5" strokeDasharray="3 3" fill="none" />
                <circle r="3" fill="#ffffff">
                  <animateMotion dur="3s" repeatCount="indefinite" path="M 30 40 Q 60 40 85 75" />
                </circle>
                <circle r="3" fill="#ffffff">
                  <animateMotion dur="2.4s" repeatCount="indefinite" path="M 170 40 Q 140 40 115 75" />
                </circle>
                <circle r="3" fill="#ffffff">
                  <animateMotion dur="1.8s" repeatCount="indefinite" path="M 100 95 L 100 125" />
                </circle>
              </svg>

              <div className="closing-node closing-node--left">
                <ReceiptText size={16} />
                <span className="closing-node-tag">R$ 150</span>
              </div>

              <div className="closing-node closing-node--right">
                <Coins size={16} />
                <span className="closing-node-tag">Pago</span>
              </div>

              <div className="closing-node closing-node--center">
                <div className="closing-node-inner closing-node-inner--excel" aria-hidden="true">
                  <FileSpreadsheet size={16} />
                </div>
              </div>

              <div className="closing-node closing-node--bottom">
                <span className="closing-node-tag closing-node-tag--csv">EXCEL.CSV</span>
              </div>
            </div>

            <p className="closing-support-text">Suporte completo para o financeiro do seu negócio.</p>

            <div className="closing-footer-section">
              <ul className="closing-checklist">
                <li><span className="closing-check-indicator"><Check size={9} strokeWidth={3} /></span><span>Pagamentos</span></li>
                <li><span className="closing-check-indicator"><Check size={9} strokeWidth={3} /></span><span>Cancelamentos</span></li>
                <li><span className="closing-check-indicator"><Check size={9} strokeWidth={3} /></span><span>Reagendamentos</span></li>
                <li><span className="closing-check-indicator"><Check size={9} strokeWidth={3} /></span><span>Agendamentos</span></li>
              </ul>
              <div className="closing-automation-badge">
                <strong>Tudo automatizado.</strong>
              </div>
            </div>
          </div>
        )}

        {/* ── CRM CARD ──────────────────────────────────── */}
        {kind === 'crm' && (
          <div className="feature-spotlight-visual feature-spotlight-visual--crm">
            <div className="crm-illustration">
              <svg className="crm-flow-lines" viewBox="0 0 300 150" fill="none">
                {/* left */}
                <path d="M 150 38 C 100 38, 55 38, 55 105" stroke="rgba(255, 255, 255, 0.10)" strokeWidth="1.5" fill="none" />
                {/* center */}
                <path d="M 150 38 L 150 105" stroke="rgba(255, 255, 255, 0.10)" strokeWidth="1.5" fill="none" />
                {/* right */}
                <path d="M 150 38 C 200 38, 245 38, 245 105" stroke="rgba(255, 255, 255, 0.10)" strokeWidth="1.5" fill="none" />
              </svg>

              {/* Center — CRM hub */}
              <div className="crm-node crm-node--center">
                <Users size={22} className="crm-core-icon" />
              </div>

              {/* Novos */}
              <div className="crm-node crm-node--tri crm-node--tri-left">
                <span className="crm-status-dot crm-status-dot--new" />
                <div className="crm-node-info">
                  <span className="crm-node-title">Novos</span>
                  <span className="crm-node-pct">4</span>
                </div>
              </div>

              {/* Regulares */}
              <div className="crm-node crm-node--tri crm-node--tri-center">
                <span className="crm-status-dot crm-status-dot--regular" />
                <div className="crm-node-info">
                  <span className="crm-node-title">Regulares</span>
                  <span className="crm-node-pct">15</span>
                </div>
              </div>

              {/* Em risco */}
              <div className="crm-node crm-node--tri crm-node--tri-right">
                <span className="crm-status-dot crm-status-dot--risk" />
                <div className="crm-node-info">
                  <span className="crm-node-title">Em risco</span>
                  <span className="crm-node-pct">4</span>
                </div>
              </div>
            </div>

            <div className="crm-actions-bar">
              <div className="crm-action-item">
                <Mail size={14} />
                <span>Recuperação por e-mail</span>
              </div>
              <div className="crm-action-item">
                <Gift size={14} />
                <span>Promoções</span>
              </div>
              <div className="crm-action-item">
                <Bell size={14} />
                <span>Lembretes automáticos</span>
              </div>
              <div className="crm-action-item">
                <MessageSquare size={14} />
                <span>Reengajamento</span>
              </div>
            </div>

            <p className="crm-bottom-phrase">
              Transforme clientes <span className="crm-word-risk">inativos</span> em clientes <span className="crm-word-success">recorrentes</span>.
            </p>
          </div>
        )}

      </div>
    </article>
  )
}

export default function FeaturesMosaicSection() {
  return (
    <section className="marketing-features marketing-features--spotlight" aria-label="Funcionalidades do Gendaz">
      <ScrollReveal className="marketing-features__head marketing-features__head--center" delay={0}>
        <span className="section-kicker">Funcionalidades</span>
        <h2>Tecnologia para economizar o seu tempo</h2>
        <p>Da agenda ao financeiro, acompanhe toda a operação em um só lugar.</p>
      </ScrollReveal>

      <div className="marketing-features__spotlight-grid">
        {cards.map((card, index) => (
          <ScrollReveal key={card.title} delay={index * 80} className="marketing-features__spotlight-item">
            <FeatureCard {...card} />
          </ScrollReveal>
        ))}
      </div>
    </section>
  )
}
