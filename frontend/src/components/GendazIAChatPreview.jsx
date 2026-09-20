import { useState } from 'react'

const SCENARIOS = [
  {
    question: 'Quais clientes devo tentar recuperar?',
    answer:
      'Comece pelos clientes que reduziram a frequência ou deixaram de retornar. No Gendaz, você consegue acompanhar quem merece atenção primeiro.',
  },
  {
    question: 'Qual serviço teve menos movimento?',
    answer:
      'A gendazIA pode usar os dados de serviços e agendamentos disponíveis para mostrar quais tiveram menos movimento no período analisado.',
  },
  {
    question: 'Como está meu financeiro este mês?',
    answer:
      'A gendazIA pode ajudar a interpretar recebidos, pendências e movimentações registradas no Gendaz para mostrar o que merece atenção.',
  },
]

export default function GendazIAChatPreview() {
  const [activeIndex, setActiveIndex] = useState(0)
  const current = SCENARIOS[activeIndex]

  return (
    <div className="gia-chat" role="region" aria-label="Demonstração ilustrativa da gendazIA. As respostas reais dependem dos dados disponíveis no seu negócio.">
      <div className="gia-chat-header">
        <span className="gia-chat-avatar" aria-hidden="true">G</span>
        <span className="gia-chat-title">gendazIA</span>
        <span className="gia-chat-status">Dados sincronizados</span>
      </div>

      <div className="gia-chat-messages" aria-live="polite">
        <div className="gia-chat-message gia-chat-message--user">
          <p>{current.question}</p>
        </div>
        <div className="gia-chat-message gia-chat-message--ia">
          <p>{current.answer}</p>
        </div>
      </div>

      <div className="gia-question-chips" role="group" aria-label="Exemplos de perguntas">
        {SCENARIOS.map((s, index) => (
          <button
            key={s.question}
            type="button"
            className={`gia-question-chip${index === activeIndex ? ' is-active' : ''}`}
            onClick={() => setActiveIndex(index)}
            aria-pressed={index === activeIndex}
          >
            {s.question}
          </button>
        ))}
      </div>

      <p className="gia-chat-note">Exemplo ilustrativo. As respostas reais dependem dos dados disponíveis no seu negócio.</p>
    </div>
  )
}
