import {
  ArrowRight,
  CalendarCheck,
  Check,
  CreditCard,
  FileText,
  Headphones,
  LifeBuoy,
  MessageCircle,
  ShieldCheck,
  Sparkles,
  UserPlus,
} from 'lucide-react'
import { motion } from 'framer-motion'
import { Link, useNavigate } from 'react-router-dom'
import ScrollReveal from '../components/ScrollReveal.jsx'
import logoWhite from '../assets/logos/gendaz-logo-preto.png'
import HeroAnimation from '../components/HeroAnimation.jsx'
import StorytellingSection from '../components/StorytellingSection.jsx'

// ⚠️ DESATIVADO — const WHATSAPP_LINK = 'https://wa.me/5565993360300'

const features = [
  ['01', MessageCircle, 'Assistente de IA no painel', 'Organize agenda, clientes e tarefas do dia com apoio inteligente para reduzir retrabalho e manter tudo em um só lugar.'],
  ['02', CalendarCheck, 'Agenda organizada', 'Acompanhe horários, confirmações, cancelamentos e remarcações.'],
  ['03', CreditCard, 'Pagamentos claros', 'Veja recebidos, pendências e acompanhamento financeiro em um só lugar.'],
  ['04', FileText, 'Gestão simples', 'Organize clientes, serviços e profissionais com uma rotina clara no dia a dia.'],
]

const plans = [
  {
    nome: 'Plano Básico',
    // ⚠️ DESATIVADO — subtitulo: 'WhatsApp e agenda simples',
    subtitulo: 'Agenda simples',
    preco: 'R$ 39,00/mês',
    extra: '7 dias grátis',
    descricao: 'Para organizar conversas, agenda, clientes e serviços no mesmo painel.',
    // ⚠️ DESATIVADO — beneficios: ['WhatsApp interno', 'Agenda organizada', 'Cadastro de clientes', 'Cadastro de serviços', 'Confirmação de consulta', 'Cancelamento e remarcação'],
    beneficios: ['Agenda organizada', 'Cadastro de clientes', 'Cadastro de serviços', 'Confirmação de consulta', 'Cancelamento e remarcação'],
    indicadoPara: ['Clínicas pequenas', 'Atendimento individual', 'Rotina de agenda e conversas'],
    naoInclui: ['Profissionais', 'Financeiro', 'Pagamentos', 'Relatórios'],
    cta: 'Começar no Básico',
  },
  {
    nome: 'Plano Pro',
    subtitulo: 'Gestão com financeiro simples',
    preco: 'R$ 89,00/mês',
    descricao: 'Para quem precisa acompanhar agenda, profissionais, pagamentos e indicadores em um só lugar.',
    beneficios: ['Tudo do Básico', 'Acesso para até 3 usuários', 'Profissionais', 'Financeiro mensal', 'Pagamentos pendentes', 'Agenda por profissional', 'Relatórios operacionais'],
    indicadoPara: ['Equipes de atendimento', 'Serviços com cobrança recorrente', 'Operação com acompanhamento diário'],
    cta: 'Escolher Pro',
    destaque: true,
  },
]

export default function Home() {
  const navigate = useNavigate()

  function handlePlanClick(plano) {
    navigate(`/criar-conta?plano=${encodeURIComponent(plano.nome)}&preco=${encodeURIComponent(plano.preco)}`)
  }

  return (
    <main className="marketing-page">

      {/* ── Navbar ─────────────────────────────────────── */}
      <header className="marketing-nav">
        <div className="marketing-nav-shell">
          <Link to="/" className="marketing-brand">
            <img src={logoWhite} alt="gendaz" className="nav-logo" />
          </Link>
          <div className="marketing-nav-panel">
          <nav className="marketing-nav-links">
          <a href="#sobre">Sobre</a>
          <a href="#solucoes">Soluções</a>
          <a href="#planos">Planos</a>
          <a href="#suporte">Suporte</a>
          <a href="#contato">Contato</a>
          </nav>
          <div className="marketing-actions">
            <Link to="/login" className="secondary-link nav-login-link">Entrar</Link>
            <Link to="/criar-conta" className="primary-link nav-signup-link"><UserPlus size={16} />Criar conta</Link>
          </div>
          </div>
        </div>
      </header>

      {/* ── Hero ───────────────────────────────────────── */}
      <section className="hero-section-new">
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.6 }}
          className="hero-new-inner"
        >
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.1 }}
          >
            <span className="hero-new-badge">
              <Sparkles size={14} />
              {/* ⚠️ DESATIVADO — WhatsApp + */} agenda + gestão
            </span>
          </motion.div>

          <motion.h1
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.2 }}
            className="hero-new-title"
          >
            Organize sua agenda com uma Assistente de IA em um só{' '}
            <span className="hero-new-gradient">PAINEL</span>.
          </motion.h1>

          <motion.p
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.3 }}
            className="hero-new-desc"
          >
            Centralize conversas, horários, clientes e pagamentos em uma plataforma simples para o dia a dia.
          </motion.p>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.4 }}
            className="hero-new-buttons"
          >
            <Link to="/criar-conta" className="primary-link">
              Criar conta grátis <ArrowRight size={17} />
            </Link>
            <a href="#planos" className="secondary-link">Ver planos</a>
          </motion.div>

          {/* ── Stats ──────────────────────────────────── */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.5 }}
            className="hero-new-stats"
          >
            <div>
              <strong>+500</strong>
              <span>atendimentos</span>
            </div>
            <div className="hero-new-stats-divider" />
            <div>
              <strong>98%</strong>
              <span>confirmações no prazo</span>
            </div>
            <div className="hero-new-stats-divider" />
            <div>
              <strong>+120</strong>
              <span>clientes</span>
            </div>
          </motion.div>
        </motion.div>
      </section>

      {/* ── Storytelling ───────────────────────────────── */}
      <StorytellingSection />

      {/* ── Sobre ──────────────────────────────────────── */}
      <ScrollReveal id="sobre" className="marketing-split" delay={80}>
        <div>
          <span className="section-kicker">Sobre o gendaz</span>
          <h2>Qualidade operacional com uma rotina que respeita o seu tempo.</h2>
        </div>
        <div>
          <p>O gendaz não é uma tela para o cliente final. Ele é o painel interno da sua empresa para controlar agenda, clientes, serviços, pagamentos e atendimentos com mais clareza.

Com uma Assistente de IA, o sistema ajuda a reduzir tarefas repetitivas, organizar informações e deixar sua operação mais simples no dia a dia.</p>
          {/* ⚠️ DESATIVADO — <p>O foco é reduzir retrabalho no WhatsApp, dar previsibilidade para os atendimentos e organizar a empresa com clareza no dia a dia.</p> */}
          <p>O foco é dar previsibilidade para os atendimentos e organizar a empresa com clareza no dia a dia.</p>
        </div>
      </ScrollReveal>

      {/* ── Soluções ───────────────────────────────────── */}
      <section id="solucoes" className="marketing-solutions solutions-section">
        <ScrollReveal className="solutions-head solutions-section-head" delay={0}>
          <div>
            <span className="section-kicker">Soluções</span>
            <h2 className="solutions-section-title">Tudo o que o atendimento precisa.</h2>
          </div>
          <p className="solutions-section-text">Uma experiência simples para conversar, agendar, acompanhar clientes e receber sem sair do mesmo painel.</p>
        </ScrollReveal>
        <div className="solution-grid solutions-section-grid">
          {features.map(([number, Icon, title, text], index) => (
            <ScrollReveal className="solution-card solutions-section-card premium-border" delay={index * 90} key={title}>
              <Icon size={24} />
              <strong>{number}</strong>
              <h3>{title}</h3>
              <p>{text}</p>
            </ScrollReveal>
          ))}
        </div>
      </section>

      {/* ── Planos ─────────────────────────────────────── */}
      <section id="planos" className="marketing-plans marketing-plans-sale pricing-section">
        <ScrollReveal className="plans-page-title" delay={0}>
          <span className="section-kicker">Planos</span>
          <h2 className="pricing-title">Planos do atendimento</h2>
          <p className="pricing-subtitle">Escolha o plano que melhor se encaixa na rotina do seu atendimento.</p>
        </ScrollReveal>

        <ScrollReveal className="pricing-grid-reveal" delay={40} threshold={0.03} rootMargin="0px 0px -2% 0px">
          <div className="plans-grid detailed plans-centered-grid pricing-grid">
            {plans.map((plano, index) => (
            <article
              className={plano.destaque ? 'plan-card highlight plan-card-sale pricing-card pricing-card-pro premium-border' : 'plan-card plan-card-sale pricing-card pricing-card-basic premium-border'}
              style={{ '--pricing-stagger': `${index * 40}ms` }}
              key={plano.nome}
            >
              {plano.destaque && <span className="recommended-badge">Mais recomendado</span>}
              <div className="plan-card-body pricing-card-body">
                  <div className="plan-head">
                    <div>
                      <h2>{plano.nome}</h2>
                      <p className="plan-subtitle">{plano.subtitulo}</p>
                    </div>
                  {plano.destaque && <ShieldCheck size={20} />}
                </div>

                <div className="plan-price-block">
                  {plano.extra && <span className="plan-price-extra">{plano.extra}</span>}
                  <strong className="plan-price">{plano.preco}</strong>
                </div>

                <p className="plan-description">{plano.descricao}</p>

                <div className="plan-section">
                  <h3>Benefícios</h3>
                  <div className="plan-list">
                    {plano.beneficios.map((item) => (
                      <strong key={item} className={item === 'Tudo do Básico' ? 'plan-list-tudo-basico' : ''}>
                        <Check size={16} />{item}
                      </strong>
                    ))}
                  </div>
                </div>

                <div className="plan-section">
                  <h3>Indicado para</h3>
                  <div className="plan-list plan-list-muted">
                    {plano.indicadoPara.map((item) => <small key={item}>{item}</small>)}
                  </div>
                </div>

                {plano.naoInclui && (
                  <div className="plan-unavailable">
                    <span>O que não inclui</span>
                    {plano.naoInclui.map((item) => <small key={item}>{item}</small>)}
                  </div>
                )}
              </div>

              <button
                type="button"
                onClick={() => handlePlanClick(plano)}
                className={plano.destaque ? 'btn btn-primary plan-action-link pricing-cta pricing-cta-pro' : 'btn btn-secondary plan-action-link pricing-cta pricing-cta-basic'}
              >
                {plano.cta}
              </button>
            </article>
          ))}
          </div>
        </ScrollReveal>
      </section>

      {/* ── Suporte ────────────────────────────────────── */}
      <section id="suporte" className="marketing-support">
        <ScrollReveal className="solutions-head" delay={0}>
          <div>
            <span className="section-kicker">Suporte</span>
            <h2>Ajuda rápida para começar sem travar.</h2>
          </div>
          <p>Fale com a equipe, tire dúvidas sobre o painel e entenda qual plano faz mais sentido para o seu atendimento.</p>
        </ScrollReveal>
        <div className="support-grid marketing-support-grid">
          <ScrollReveal className="panel support-card premium-border" delay={0}>
            <LifeBuoy size={26} />
            <h2>Base de ajuda</h2>
            {/* ⚠️ DESATIVADO — <p>Orientações rápidas para WhatsApp, agenda, clientes, pagamentos e configurações.</p> */}
            <p>Orientações rápidas para agenda, clientes, pagamentos e configurações.</p>
          </ScrollReveal>
          <ScrollReveal className="panel support-card premium-border" delay={80}>
            <MessageCircle size={26} />
            {/* ⚠️ DESATIVADO — <h2>Atendimento no WhatsApp</h2> */}
            <h2>Atendimento por e-mail</h2>
            <p>Converse com a equipe para entender o produto, preços e ativação da conta.</p>
          </ScrollReveal>
          <ScrollReveal className="panel support-card premium-border" delay={160}>
            <Headphones size={26} />
            <h2>Suporte comercial</h2>
            <p>Ajuda para escolher o plano certo e começar o teste gratuito com clareza.</p>
          </ScrollReveal>
        </div>
      </section>

      {/* ── Contato / CTA ──────────────────────────────── */}
      <ScrollReveal id="contato" className="contact-band premium-border" delay={0}>
        <div>
          <span className="section-kicker">Contato</span>
          <h2>Fale com a equipe e veja como o gendaz funciona na prática.</h2>
        </div>
        {/* ⚠️ DESATIVADO — <a href={WHATSAPP_LINK} className="primary-link" target="_blank" rel="noreferrer">Entrar em contato</a> */}
        <Link to="/criar-conta" className="primary-link">Criar conta grátis</Link>
      </ScrollReveal>

      {/* ── Footer ─────────────────────────────────────── */}
      <footer className="marketing-footer">
        <small>gendaz · PloyDev</small>
        <div>
          <Link to="/termos-de-uso">Termos de Uso</Link>
          <Link to="/politica-de-privacidade">Política de Privacidade</Link>
        </div>
      </footer>
    </main>
  )
}

