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
  UserPlus,
  X,
} from 'lucide-react'
import { motion } from 'framer-motion'
import { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import ScrollReveal from '../components/ScrollReveal.jsx'
import logoWhite from '../assets/logos/gendazpng.png'
import notebookMockupImage from '../assets/marketing/mockup-notebook.png'
import salaoBarbeariasImage from '../assets/segments/salao-e-barbearias.jpg'
import barbeariaImage from '../assets/segments/barbearia.jpg'
import manicureImage from '../assets/segments/manicure.jpg'
import depilacaoImage from '../assets/segments/depilacao.jpg'
import sobrancelhasCiliosImage from '../assets/segments/sobrancelhas-e-cilios.jpg'
import locacaoQuadraImage from '../assets/segments/locacao-de-quadra.jpg'
import clinicaOdontologicaImage from '../assets/segments/clinica-odontologica.jpg'
import personalTrainerImage from '../assets/segments/personal-trainer-2.jpg'
import consultoriosImage from '../assets/segments/consultorios.jpg'
import esteticasSpasImage from '../assets/segments/esteticas-e-spas.jpg'
import yogaPilatesImage from '../assets/segments/yoga-e-pilates.jpg'
import cursosTutoriaImage from '../assets/segments/cursos-e-tutoria.webp'
import hospedagensImage from '../assets/segments/hospedagens.jpg'
import servicosAutomotivosImage from '../assets/segments/servicos-automotivos.jpg'
import petshopsImage from '../assets/segments/petshops.jpg'
import estudioFotograficoImage from '../assets/segments/estudio-fotografico.jpg'
import estudoTatuagemImage from '../assets/segments/estudio-tatuagem.png'
import clinicaEsteticaImage from '../assets/segments/clinica-estetica.png'
import psicologosTerapeutasImage from '../assets/segments/psicologos-terapeutas.jpg'
import HeroAnimation from '../components/HeroAnimation.jsx'
import StorytellingSection from '../components/StorytellingSection.jsx'
import TestimonialsSection from '../components/TestimonialsSection.jsx'
import FeaturesMosaicSection from '../components/FeaturesMosaicSection.jsx'


const features = [
  ['01', MessageCircle, 'Assistente de IA no painel', 'Organize agenda, clientes e tarefas do dia com apoio inteligente para reduzir retrabalho e manter tudo em um só lugar.'],
  ['02', CalendarCheck, 'Agenda organizada', 'Acompanhe horários, confirmações, cancelamentos e remarcações.'],
  ['03', CreditCard, 'Pagamentos claros', 'Veja recebidos, pendências e acompanhamento financeiro em um só lugar.'],
  ['04', FileText, 'Gestão simples', 'Organize clientes, serviços e profissionais com uma rotina clara no dia a dia.'],
]

const growthSegments = [
  { title: 'Salão de Beleza', subtitle: 'Agendamento, CRM, financeiro', badge: 'MVP', image: salaoBarbeariasImage },
  { title: 'Barbearia', subtitle: 'Agenda, clientes e controle do dia a dia', badge: 'Cortes', image: barbeariaImage },
  { title: 'Manicure', subtitle: 'Agenda, serviços e retorno de clientes', badge: 'Unhas', image: manicureImage },
  { title: 'Depilação', subtitle: 'Agenda, sessões e acompanhamento de clientes', badge: 'Estética', image: depilacaoImage },
  { title: 'Sobrancelhas e cílios', subtitle: 'Agenda, design e manutenção de atendimentos', badge: 'Beleza', image: sobrancelhasCiliosImage },
  { title: 'Locação de quadra', subtitle: 'Reservas, horários e controle de ocupação', badge: 'Esportes', image: locacaoQuadraImage },
  { title: 'Clínica odontológica', subtitle: 'Consultas, agenda e histórico de pacientes', badge: 'Saúde', image: clinicaOdontologicaImage },
  { title: 'Personal Trainers', subtitle: 'Aulas, sessões e planos mensais', badge: 'Academias', image: personalTrainerImage },
  { title: 'Consultorios', subtitle: 'Agendamentos, historico e prescricoes', badge: 'Saude', image: consultoriosImage },
  { title: 'Esteticas & Spas', subtitle: 'Tratamentos e historico de procedimentos', badge: 'Bem-estar', image: esteticasSpasImage },
  { title: 'Yoga e Pilates', subtitle: 'Aulas, frequencia e evolucao', badge: 'Rotina', image: yogaPilatesImage },
  { title: 'Cursos & Tutoria', subtitle: 'Aulas particulares e gestao de alunos', badge: 'Educacao', image: cursosTutoriaImage },
  { title: 'Hospedagens', subtitle: 'Reservas, disponibilidade e hospedes', badge: 'Viagens', image: hospedagensImage },
  { title: 'Servicos Automotivos', subtitle: 'Manutencao, revisoes e lembretes', badge: 'Auto', image: servicosAutomotivosImage },
  { title: 'Petshops & Veterinárias', subtitle: 'Agendamentos, banho, tosa e prontuário', badge: 'Pets', image: petshopsImage },
  { title: 'Estúdios Fotográficos', subtitle: 'Sessões, agenda e entrega de material', badge: 'Foto', image: estudioFotograficoImage },
  { title: 'Estúdios de Tatuagem', subtitle: 'Agenda, desenho e acompanhamento de clientes', badge: 'Tattoo', image: estudoTatuagemImage },
  { title: 'Clínica de Estética', subtitle: 'Tratamentos, agenda e histórico de clientes', badge: 'Beleza', image: clinicaEsteticaImage },
  { title: 'Psicólogos & Terapeutas', subtitle: 'Consultas, prontuário e acompanhamento', badge: 'Saúde', image: psicologosTerapeutasImage },
]

const plans = [
  {
    nome: 'Plano Básico',
    subtitulo: 'Agenda simples',
    preco: 'R$ 29,90/mês',
    extra: '7 dias grátis',
    descrição: 'Para organizar sua agenda, clientes e serviços de forma prática e eficiente.',
    beneficios: [
      'Financeiro - Pagamentos automatizados - Relatórios',
      'Histórico ilimitado',
      'Agendamentos ilimitados',
      'Confirmação de agendamentos',
    ],
    naoInclui: [
      'CRM integrado',
      'Insights',
      'Até 3 usuários',
      'Financeiro completo',
    ],
    cta: 'Começar no Básico',
  },
  {
    nome: 'Plano Pro',
    subtitulo: 'Gestão completa com financeiro',
    preco: 'R$ 79,90/mês',
    extra: '7 dias grátis',
    descrição: 'Para gerenciar sua agenda, equipe, pagamentos e insights com inteligência.',
    beneficios: [
      'Tudo do Plano Básico +',
      'Até 3 usuários na conta',
      'CRM integrado',
      'Insights com GendazIA no controle',
      'Financeiro completo: caixa, despesas pagamentos automatizados',
    ],
    cta: 'Escolher Pro',
    destaque: true,
  },
  {
    nome: 'Plano Plus',
    subtitulo: 'Mais capacidade para sua equipe',
    preco: 'R$ 109,90/mês',
    extra: '7 dias grátis',
    descrição: 'Para equipes maiores com maior necessidade de gerenciamento e acesso.',
    beneficios: [
      'Tudo do Plano Pro +',
      'Até 7 usuários na conta',
      'CRM integrado',
      'Insights com GendazIA no controle',
      'Financeiro completo: caixa, despesas pagamentos automatizados',
    ],
    cta: 'Assinar Plus',
  },
  {
    nome: 'Plano Enterprise',
    subtitulo: 'Escalabilidade máxima',
    preco: 'R$ 149,90/mês',
    extra: '7 dias grátis',
    descrição: 'Para operações robustas com gerenciamento extensivo de usuários.',
    beneficios: [
      'Tudo do Plano Plus +',
      'Até 15 usuários na conta',
      'CRM integrado',
      'Insights com GendazIA no controle',
      'Financeiro completo: caixa, despesas pagamentos automatizados',
    ],
    cta: 'Assinar Enterprise',
  },
]

export default function Home() {
  const navigate = useNavigate()
  const [segmentOffset, setSegmentOffset] = useState(0)

  function handlePlanClick(plano) {
    navigate(`/criar-conta?plano=${encodeURIComponent(plano.nome)}&preco=${encodeURIComponent(plano.preco)}`)
  }

  function handleSegmentMove(direction) {
    setSegmentOffset((current) => {
      if (direction > 0) {
        return current + 1
      }
      return current > 0 ? current - 1 : 0
    })
  }

  return (
    <>
      <main id="inicio" className="marketing-page">

      {/* Navbar */}
      <header className="marketing-nav-gendo">
        <div className="marketing-nav-gendo-shell">
          <Link to="/" className="marketing-brand-gendo">
            <img src={logoWhite} alt="gendaz" className="nav-logo-gendo" />
          </Link>
          
          <nav className="marketing-nav-links-gendo">
          <a href="#inicio">Início</a>
          <a href="#sobre">Sobre</a>
          <a href="#planos">Planos</a>
          <a href="#suporte">Suporte</a>
          <a href="#contato">Contato</a>
          </nav>
          <div className="marketing-actions-gendo">
            <Link to="/login" className="secondary-link-gendo">Entrar</Link>
            <Link to="/criar-conta" className="primary-link-gendo"><UserPlus size={16} />Criar conta</Link>
          </div>
        </div>
      </header>

      {/* Hero */}
      <section className="hero-section-new">
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.6 }}
          className="hero-new-inner"
        >
          <motion.h1
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.2 }}
            className="hero-new-title"
          >
            <span className="hero-line">Foque no <span className="hero-accent">seu atendimento</span> e deixe os</span>
            <span className="hero-line">agendamentos com <span className="hero-accent">a Gendaz.</span></span>
          </motion.h1>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.3 }}
            className="hero-new-sequence"
          >
            <span className="hero-sequence-item">Cliente</span>
            <span className="hero-sequence-arrow">→</span>
            <span className="hero-sequence-item">Agenda</span>
            <span className="hero-sequence-arrow hero-accent">→</span>
            <span className="hero-sequence-finale hero-accent">O resto é com a Gendaz</span>
          </motion.div>

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

      {/* Stats */}
                    <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.5 }}
            className="hero-new-stats"
          >
              <p>Centralize reservas, pagamentos, clientes e muito mais em só uma plataforma completa, ágil, intuitiva e que funciona em qualquer situação.</p>
          </motion.div>
        </motion.div>
      </section>

      {/* Storytelling */}
      <StorytellingSection />

      <FeaturesMosaicSection />

      {/* Sobre */}
      <ScrollReveal id="sobre" className="marketing-split" delay={80}>
        <div>
          <h2>Qualidade operacional com uma rotina que respeita o seu tempo.</h2>
        </div>
        <div>
          <p>O gendaz não é uma tela para o cliente final. Ele é o painel interno da sua empresa para controlar agenda, clientes, serviços, pagamentos e atendimentos com mais clareza.

Com uma Assistente de IA, o sistema ajuda a reduzir tarefas repetitivas, organizar informações e deixar sua operação mais simples no dia a dia.</p>
          <p>O foco é dar previsibilidade para os atendimentos e organizar a empresa com clareza no dia a dia.</p>
        </div>
      </ScrollReveal>
      <ScrollReveal className="bounce-reveal" delay={0} threshold={0.18} rootMargin="0px 0px -10% 0px">
          <section className="marketing-segments" aria-label="Segmentos de crescimento">
            <div className="marketing-segments-head">
              <div>
              <h2>Segmentos que crescem ao lado da Gendaz.</h2>
              </div>
            <div className="marketing-segments-arrows" aria-hidden="true">
              <button type="button" className="marketing-segments-arrow" onClick={() => handleSegmentMove(-1)} aria-label="Anterior">‹</button>
              <button type="button" className="marketing-segments-arrow" onClick={() => handleSegmentMove(1)} aria-label="Próximo">›</button>
            </div>
          </div>

          <div className="marketing-segments-marquee">
            <div className="marketing-segments-track" style={{ '--segment-offset': segmentOffset }}>
              {[...growthSegments, ...growthSegments, ...growthSegments, ...growthSegments, ...growthSegments].map((segment, index) => (
                <article className="marketing-segment-card" key={`${segment.title}-${index}`}>
                  <div className="marketing-segment-image" aria-hidden="true">
                    {segment.image ? <img src={segment.image} alt="" /> : <span>{String((index % growthSegments.length) + 1).padStart(2, '0')}</span>}
                  </div>
                  <div className="marketing-segment-content">
                    <span className="marketing-segment-risk" aria-hidden="true" />
                    <strong>{segment.title}</strong>
                  </div>
                </article>
              ))}
            </div>
          </div>
        </section>
      </ScrollReveal>

      <ScrollReveal className="bounce-reveal" delay={80} threshold={0.18} rootMargin="0px 0px -10% 0px">
        <section className="marketing-showcase-band" aria-label="Destaque do aplicativo">
          <div className="marketing-showcase-band-inner">
            <div className="marketing-showcase-copy marketing-showcase-copy-left">
              <p>Não precisa entender nada de tecnologia, é só deixar a Gendaz trabalhar para você.</p>
            </div>

            <div className="marketing-showcase-device marketing-showcase-notebook" aria-hidden="true">
              <div className="marketing-showcase-notebook-screen" aria-hidden="true">
                <div className="marketing-showcase-dashboard-fake">
                  <aside className="marketing-showcase-dashboard-sidebar">
                    <strong className="marketing-showcase-dashboard-logo">gendaz</strong>
                    <span className="marketing-showcase-dashboard-kicker">Navegacao</span>
                    {['Dashboard', 'Agendamentos', 'Clientes', 'Profissionais', 'Serviços', 'CRM', 'Insights', 'Financeiro'].map((item, index) => (
                      <span className={index === 0 ? 'is-active' : ''} key={item}>{item}</span>
                    ))}
                  </aside>
                  <div className="marketing-showcase-dashboard-main">
                    <div className="marketing-showcase-dashboard-top">
                      <div>
                        <span>Operação Gendaz</span>
                        <strong>Visão geral</strong>
                      </div>
                      <em>Plano Pro</em>
                    </div>
                    <div className="marketing-showcase-dashboard-hero">
                      <small className="marketing-showcase-dashboard-date">Terça-feira, 04 de agosto</small>
                      <strong>Olá, Vinicius Henrique.</strong>
                    </div>
                    <div className="marketing-showcase-dashboard-metrics">
                      <span><strong>2</strong>Agendamentos</span>
                      <span><strong>3</strong>Pendente</span>
                      <span><strong>R$ 100</strong>Receita</span>
                      <span><strong>300</strong>Cobrança</span>
                    </div>
                    <div className="marketing-showcase-dashboard-chart">
                      <div className="marketing-showcase-dashboard-chart-head">
                        <strong>Financeiro</strong>
                        <small>Agosto 2026</small>
                      </div>
                      <div className="marketing-showcase-dashboard-graph">
                        {Array.from({ length: 18 }).map((_, index) => (
                          <i style={{ '--bar-height': `${index === 4 ? 70 : index === 5 ? 28 : 8}%` }} key={index} />
                        ))}
                      </div>
                    </div>
                    <div className="marketing-showcase-dashboard-bottom">
                      <span><strong>Hoje</strong>2 confirmados</span>
                      <span><strong>CRM</strong>12 clientes ativos</span>
                      <span><strong>Fila</strong>3 retornos</span>
                    </div>
                  </div>
                </div>
              </div>
              <img className="marketing-showcase-device-image" src={notebookMockupImage} alt="" draggable="false" />
            </div>

            <div className="marketing-showcase-copy marketing-showcase-copy-right">
              <p>Gerencie sua agenda, clientes e pagamentos em um único painel inteligente. Simples, rápido e seguro.</p>
            </div>
          </div>
        </section>
      </ScrollReveal>

      {/* Planos */}
      <section id="planos" className="marketing-plans marketing-plans-sale pricing-section">
        <ScrollReveal className="plans-page-title" delay={0}>
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
                      <h2 style={{ color: '#ff5e29' }}>{plano.nome}</h2>
                      <p className="plan-subtitle">{plano.subtitulo}</p>
                    </div>
                  {plano.destaque && <ShieldCheck size={20} className="plan-head-icon" />}
                </div>

                <div className="plan-price-block">
                  {plano.extra && <span className="plan-price-extra">{plano.extra}</span>}
                  <strong className="plan-price">{plano.preco}</strong>
                </div>

                <p className="plan-description">{plano.descrição}</p>

                <div className="plan-section">
                  <h3>Benefícios</h3>
                  <div className="plan-list">
                    {plano.beneficios.map((item) => (
                      <strong key={item} className={item === 'Tudo do Plano Básico +' ? 'plan-list-tudo-basico' : ''}>
                        <Check size={16} style={{ color: '#22c55e' }} />{item}
                      </strong>
                    ))}
                  </div>
                </div>

                {plano.beneficiosExtra && plano.beneficiosExtra.length > 0 && (
                  <div className="plan-section">
                    <h3>Benefícios</h3>
                    <div className="plan-list">
                      {plano.beneficiosExtra.map((item) => (
                        <strong key={item}>
                          <Check size={16} style={{ color: '#22c55e' }} />{item}
                        </strong>
                      ))}
                    </div>
                  </div>
                )}

                {plano.naoInclui && plano.naoInclui.length > 0 && (
                  <div className="plan-unavailable">
                    <span>Não inclui</span>
                    {plano.naoInclui.map((item) => (
                      <small key={item} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <X size={14} style={{ color: '#ef4444', flexShrink: 0 }} />
                        {item}
                      </small>
                    ))}
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

      <TestimonialsSection />

      {/* Suporte */}
      <section id="suporte" className="marketing-support">
        <ScrollReveal className="solutions-head" delay={0}>
          <div>
            <h2>Ajuda rápida para começar sem travar.</h2>
          </div>
          <p>Fale com a equipe, tire dúvidas sobre o painel e entenda qual plano faz mais sentido para o seu atendimento.</p>
        </ScrollReveal>
        <div className="support-grid marketing-support-grid">
          <ScrollReveal className="panel support-card premium-border" delay={0}>
            <LifeBuoy size={26} />
            <h2>Base de ajuda</h2>
            <p>Orientações rápidas para agenda, clientes, pagamentos e configurações.</p>
          </ScrollReveal>
          <ScrollReveal className="panel support-card premium-border" delay={80}>
            <MessageCircle size={26} />
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

      {/* Contato / CTA */}
      <ScrollReveal id="contato" className="contact-band premium-border" delay={0}>
        <div>
          <span className="section-kicker">Contato</span>
          <h2>Fale com a equipe e veja como o gendaz funciona na prática.</h2>
        </div>
        <a
          href="mailto:contato@gendaz.site"
          className="primary-link"
          target="_blank"
          rel="noreferrer"
        >
          Falar com a Gendaz
        </a>
      </ScrollReveal>

      {/* Footer */}
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





