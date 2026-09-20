import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { UserPlus } from 'lucide-react'
import SEO, { SITE_BASE } from '../components/SEO.jsx'
import ScrollReveal from '../components/ScrollReveal.jsx'
import GendazIAChatPreview from '../components/GendazIAChatPreview.jsx'
import logoWhite from '../assets/logos/gendazpng.png'
import '../styles/gendazia-marketing.css'

const ASK_GROUPS = [
  {
    title: 'Clientes',
    examples: ['Quais clientes devo tentar recuperar?', 'Quem reduziu a frequência?'],
  },
  {
    title: 'Serviços',
    examples: ['Qual serviço teve menos movimento?', 'Quais serviços tiveram mais agendamentos?'],
  },
  {
    title: 'Financeiro',
    examples: ['Como está meu financeiro este mês?', 'Existem pendências que merecem atenção?'],
  },
  {
    title: 'Agenda',
    examples: ['Quais períodos tiveram menos movimento?', 'Como estão meus agendamentos?'],
  },
]

const STEPS = [
  {
    num: '01',
    title: 'Sua rotina acontece no Gendaz',
    text: 'Agendamentos, clientes, serviços e movimentações ficam registrados no sistema conforme o uso do negócio.',
  },
  {
    num: '02',
    title: 'Os dados são sincronizados',
    text: 'A gendazIA trabalha com uma fotografia das informações disponíveis no negócio.',
  },
  {
    num: '03',
    title: 'Você faz uma pergunta',
    text: 'Pergunte normalmente sobre clientes, agenda, serviços ou financeiro.',
  },
  {
    num: '04',
    title: 'A gendazIA responde com o que existe',
    text: 'Quando existe informação suficiente, ela ajuda a interpretar os dados. Quando não existe, deve sinalizar que faltam informações.',
  },
]

const FAQ = [
  {
    q: 'O que a gendazIA analisa?',
    a: 'Ela trabalha com as informações disponíveis no Gendaz, como agenda, clientes, serviços e dados financeiros registrados no sistema.',
  },
  {
    q: 'A gendazIA usa dados do meu negócio?',
    a: 'Sim. A proposta é responder utilizando as informações disponíveis na operação cadastrada no Gendaz.',
  },
  {
    q: 'E se não existirem dados suficientes?',
    a: 'A experiência foi desenhada para indicar quando não há informação suficiente em vez de inventar números ou conclusões.',
  },
  {
    q: 'Preciso entender de inteligência artificial?',
    a: 'Não. A ideia é perguntar em linguagem normal, como você perguntaria para alguém da sua equipe.',
  },
  {
    q: 'A gendazIA toma decisões pelo meu negócio?',
    a: 'Não. Ela ajuda a interpretar informações. A decisão continua sendo do responsável pelo negócio.',
  },
  {
    q: 'Em quais planos a gendazIA está disponível?',
    a: 'plans',
  },
]

const jsonLd = [
  {
    '@context': 'https://schema.org',
    '@type': 'SoftwareApplication',
    name: 'Gendaz',
    applicationCategory: 'BusinessApplication',
    operatingSystem: 'Web',
    url: `${SITE_BASE}/gendazia`,
    featureList: ['Agenda', 'Gestão de clientes', 'Financeiro', 'CRM', 'gendazIA'],
  },
  {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'Início', item: `${SITE_BASE}/` },
      { '@type': 'ListItem', position: 2, name: 'gendazIA', item: `${SITE_BASE}/gendazia` },
    ],
  },
]

export default function GendazIAMarketingPage() {
  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' })
  }, [])

  return (
    <>
      <SEO
        title="GendazIA: IA para Entender seu Negócio | Gendaz"
        description="Pergunte sobre agenda, clientes, serviços e financeiro. A gendazIA usa os dados disponíveis no seu Gendaz para ajudar você a entender o negócio."
        canonical="https://gendaz.site/gendazia"
        ogTitle="GendazIA: IA para Entender seu Negócio | Gendaz"
        ogDescription="Pergunte sobre agenda, clientes, serviços e financeiro usando as informações disponíveis no seu Gendaz."
        ogUrl="https://gendaz.site/gendazia"
        jsonLd={jsonLd}
      />
      <main className="marketing-page gendazia-page">
        <header className="marketing-nav-gendo">
          <div className="marketing-nav-gendo-shell">
            <Link to="/" className="marketing-brand-gendo" aria-label="Gendaz — início">
              <img src={logoWhite} alt="gendaz" className="nav-logo-gendo" />
            </Link>
            <nav className="marketing-nav-links-gendo" aria-label="Navegação principal">
              <Link to="/">Início</Link>
              <a href="/#sobre">Sobre</a>
              <a href="/#segmentos">Segmentos</a>
              <Link to="/gendazia">GendazIA</Link>
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

        {/* HERO */}
        <section className="gia-hero" aria-label="Apresentação da gendazIA">
          <div className="gia-hero-inner">
            <ScrollReveal delay={0}>
              <span className="gia-eyebrow">gendazIA</span>
              <h1>Seu negócio já tem os dados. A gendazIA ajuda você a entendê-los.</h1>
              <p className="gia-hero-sub">
                Pergunte sobre agenda, clientes, serviços e financeiro em linguagem natural. A
                gendazIA responde usando as informações disponíveis no seu Gendaz.
              </p>
              <div className="gia-hero-ctas">
                <Link to="/criar-conta" className="gia-btn-primary">Criar conta grátis</Link>
                <a href="#como-funciona" className="gia-btn-secondary">Ver como funciona</a>
              </div>
              <p className="gia-hero-micro">Sem relatório complicado. Sem procurar tela por tela.</p>
            </ScrollReveal>
            <ScrollReveal delay={80} className="gia-hero-visual">
              <GendazIAChatPreview />
            </ScrollReveal>
          </div>
        </section>

        {/* PROBLEMAS */}
        <section className="gia-section" aria-label="Por que isso importa">
          <div className="gia-page-inner">
            <ScrollReveal delay={0}>
              <span className="gia-eyebrow">Por que isso importa</span>
              <h2>O problema não é ter dados. É perceber tarde demais.</h2>
            </ScrollReveal>
            <div className="gia-pain-grid">
              <ScrollReveal delay={0}>
                <article className="gia-pain-card">
                  <span className="gia-pain-num" aria-hidden="true">01</span>
                  <h3>Cliente que deixou de voltar</h3>
                  <p>A frequência pode diminuir aos poucos e passar despercebida no meio da rotina.</p>
                </article>
              </ScrollReveal>
              <ScrollReveal delay={80}>
                <article className="gia-pain-card">
                  <span className="gia-pain-num" aria-hidden="true">02</span>
                  <h3>Serviço com menos movimento</h3>
                  <p>Quando tudo parece normal no dia a dia, uma queda gradual pode ser difícil de perceber.</p>
                </article>
              </ScrollReveal>
              <ScrollReveal delay={160}>
                <article className="gia-pain-card">
                  <span className="gia-pain-num" aria-hidden="true">03</span>
                  <h3>Financeiro que exige interpretação</h3>
                  <p>Ter os registros é importante. Entender onde olhar primeiro é o próximo passo.</p>
                </article>
              </ScrollReveal>
            </div>
          </div>
        </section>

        {/* PERGUNTAS */}
        <section className="gia-section" aria-label="Exemplos de perguntas">
          <div className="gia-page-inner">
            <ScrollReveal delay={0}>
              <h2>Pergunte como você perguntaria para alguém da sua equipe.</h2>
              <p className="gia-section-lead">
                Você não precisa montar fórmula, relatório ou consulta. Pergunte usando linguagem normal.
              </p>
            </ScrollReveal>
            <div className="gia-ask-grid">
              {ASK_GROUPS.map((g, i) => (
                <ScrollReveal key={g.title} delay={i * 60}>
                  <article className="gia-ask-card">
                    <h3>{g.title}</h3>
                    <ul>
                      {g.examples.map((q) => (
                        <li key={q}>&ldquo;{q}&rdquo;</li>
                      ))}
                    </ul>
                  </article>
                </ScrollReveal>
              ))}
            </div>
          </div>
        </section>

        {/* COMO FUNCIONA */}
        <section id="como-funciona" className="gia-section" aria-label="Como funciona">
          <div className="gia-page-inner">
            <ScrollReveal delay={0}>
              <span className="gia-eyebrow">Como funciona</span>
              <h2>Do dado à resposta em quatro passos.</h2>
            </ScrollReveal>
            <div className="gia-step-grid">
              {STEPS.map((s, i) => (
                <ScrollReveal key={s.num} delay={i * 60}>
                  <article className="gia-step-card">
                    <span className="gia-pain-num" aria-hidden="true">{s.num}</span>
                    <h3>{s.title}</h3>
                    <p>{s.text}</p>
                  </article>
                </ScrollReveal>
              ))}
            </div>
          </div>
        </section>

        {/* CRM + IA */}
        <section className="gia-section" aria-label="Clientes e recorrência">
          <div className="gia-page-inner">
            <ScrollReveal delay={0}>
              <span className="gia-eyebrow">Clientes e recorrência</span>
              <h2>Veja quem está voltando. Entenda quem merece atenção.</h2>
              <p className="gia-section-lead">
                O CRM organiza o acompanhamento dos clientes. A gendazIA ajuda você a interpretar
                essas informações e entender onde vale olhar primeiro.
              </p>
            </ScrollReveal>
            <div className="gia-crm-flow" aria-label="Categorias de clientes">
              <span className="gia-crm-chip">Novos</span>
              <span className="gia-crm-chip">Regulares</span>
              <span className="gia-crm-chip">Em risco</span>
            </div>
            <div className="gia-crm-roles">
              <div className="gia-crm-role">
                <strong>CRM</strong>
                <span>organiza</span>
              </div>
              <span className="gia-crm-arrow" aria-hidden="true">→</span>
              <div className="gia-crm-role">
                <strong>gendazIA</strong>
                <span>ajuda a interpretar</span>
              </div>
            </div>
          </div>
        </section>

        {/* CONFIANÇA */}
        <section className="gia-section" aria-label="Respostas baseadas no seu negócio">
          <div className="gia-page-inner">
            <ScrollReveal delay={0}>
              <span className="gia-eyebrow">Respostas baseadas no seu negócio</span>
              <h2>A gendazIA não precisa inventar uma resposta.</h2>
              <p className="gia-section-lead">
                A gendazIA foi desenhada para utilizar as informações disponíveis no Gendaz. Quando
                os dados necessários não estão disponíveis, a experiência deve indicar que não
                existem informações suficientes para responder.
              </p>
            </ScrollReveal>
            <div className="gia-trust-grid">
              <article className="gia-trust-card">
                <h3>Dados do seu Gendaz</h3>
                <p>A análise parte das informações registradas na sua operação.</p>
              </article>
              <article className="gia-trust-card">
                <h3>Sem números inventados</h3>
                <p>Se não existe informação suficiente, a resposta não deve criar dados.</p>
              </article>
              <article className="gia-trust-card">
                <h3>Você continua no controle</h3>
                <p>A gendazIA ajuda a interpretar. A decisão continua sendo sua.</p>
              </article>
            </div>
          </div>
        </section>

        {/* CTA */}
        <section className="gia-section" aria-label="Começar agora">
          <div className="gia-page-inner">
            <div className="gia-cta">
              <h2>Organize o seu negócio. Depois pergunte o que os dados estão mostrando.</h2>
              <p>Agenda, clientes, financeiro, CRM e gendazIA trabalhando dentro da mesma operação.</p>
              <div className="gia-cta-row">
                <Link to="/criar-conta" className="gia-btn-primary">Criar conta grátis</Link>
                <a href="/#planos" className="gia-btn-secondary">Ver planos</a>
              </div>
            </div>
          </div>
        </section>

        {/* FAQ */}
        <section className="gia-section" aria-label="Perguntas frequentes">
          <div className="gia-page-inner">
            <ScrollReveal delay={0}>
              <h2>Perguntas frequentes</h2>
            </ScrollReveal>
            <div className="gia-faq">
              {FAQ.map((item) => (
                <details key={item.q}>
                  <summary>{item.q}</summary>
                  {item.a === 'plans' ? (
                    <p>
                      A gendazIA faz parte dos planos que incluem Insights, como Pro, Plus e
                      Enterprise. Consulte a <a href="/#planos">seção de planos</a> para verificar
                      os recursos de cada opção.
                    </p>
                  ) : (
                    <p>{item.a}</p>
                  )}
                </details>
              ))}
            </div>
          </div>
        </section>

        <footer className="marketing-footer">
          <small>gendaz</small>
          <div>
            <Link to="/termos-de-uso">Termos de Uso</Link>
            <Link to="/politica-de-privacidade">Política de Privacidade</Link>
          </div>
        </footer>
      </main>
    </>
  )
}
