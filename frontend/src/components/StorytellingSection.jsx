import { useEffect, useRef, useState } from 'react'
import { MessageCircle, CalendarCheck, Users, LayoutDashboard, MessagesSquare } from 'lucide-react'

const STEPS = [
  // {
  //   num: '01',
  //   icon: MessageCircle,
  //   body: 'Centralize todas as conversas com seus clientes diretamente no painel. Histórico, status e respostas rápidas em um só lugar.',
  //   visual: {
  //     label: 'Conversa organizada',
  //     detail: '4 mensagens novas',
  //   },
  // },
  {
    id: 'agenda',
    num: '01',
    icon: CalendarCheck,
    heading: 'Meu gendaz',
    body: 'Portal do cliente para agendar, reagendar, cancelar e consultar horários com a Gendaz IA.',
    visual: {
      label: 'Agenda de hoje',
      detail: '6 atendimentos confirmados',
    },
  },
  {
    id: 'clientes',
    num: '02',
    icon: Users,
    heading: 'Agendamento',
    body: 'Quando um cliente agenda pelo portal, o registro aparece automaticamente na Gendaz para o atendimento.',
    visual: {
      label: 'Clientes ativos',
      detail: '120 registros acompanhados',
    },
  },
  {
    id: 'crm',
    num: '03',
    icon: MessagesSquare,
    heading: 'Atendimento',
    body: 'Acesse o agendamento, inicie o atendimento e finalize com a confirmação do pagamento.',
    visual: {
      label: 'CRM ativo',
      detail: 'Conversas e oportunidades centralizadas',
    },
  },
  {
    id: 'painel',
    num: '04',
    icon: LayoutDashboard,
    heading: 'Financeiro',
    body: 'Após a confirmação do pagamento, a Gendaz registra automaticamente o serviço e o profissional na aba financeira.',
    visual: {
      label: 'Painel completo',
      detail: 'Operação em tempo real',
    },
  },
]

export default function StorytellingSection() {
  const [activeIndex, setActiveIndex] = useState(0)
  const [visible, setVisible] = useState(false)
  const sectionRef = useRef(null)

  useEffect(() => {
    const section = sectionRef.current
    if (!section) return undefined

    const observer = new IntersectionObserver(
      ([entry]) => {
        setVisible(entry.isIntersecting)
      },
      { threshold: 0.16, rootMargin: '0px 0px -12% 0px' },
    )

    observer.observe(section)
    return () => observer.disconnect()
  }, [])

  return (
    <section className={`storytelling-section${visible ? ' is-visible' : ''}`} aria-label="Como funciona o gendaz" ref={sectionRef}>
      <div className="storytelling-flow">
        <div className="storytelling-left">
          <div className="storytelling-glow" aria-hidden="true" />
          <div className="storytelling-head">
            <h2>O caminho até o agendamento.</h2>
          </div>

          <div className="storytelling-steps storytelling-steps-compact" role="list">
            {STEPS.map((step, idx) => {
              const Icon = step.icon
              const isActive = activeIndex === idx
              return (
                <button
                  key={step.id}
                  type="button"
                  className={`storytelling-step storytelling-step-card${isActive ? ' is-active' : ''}`}
                  onMouseEnter={() => setActiveIndex(idx)}
                  onFocus={() => setActiveIndex(idx)}
                  onClick={() => setActiveIndex(idx)}
                >
                  <span className="step-num">{step.num}</span>
                  <div className="storytelling-step-main">
                    <h3>
                      <Icon size={20} style={{ display: 'inline', marginRight: 10, verticalAlign: 'middle', color: 'var(--primary)' }} />
                      {step.heading}
                    </h3>
                    <p>{step.body}</p>
                  </div>
                </button>
              )
            })}
          </div>

        </div>
      </div>
    </section>
  )
}
