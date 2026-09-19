import ScrollReveal from './ScrollReveal.jsx'
import '../styles/gendazia-marketing.css'

const SIGNALS = [
  {
    num: '01',
    title: 'Cliente que parou de voltar',
    text: 'Ele frequentava o negócio, reduziu a frequência e você pode perceber isso somente depois de muito tempo.',
  },
  {
    num: '02',
    title: 'Serviço perdendo movimento',
    text: 'A mudança acontece aos poucos e pode passar despercebida no meio da rotina de atendimentos.',
  },
  {
    num: '03',
    title: 'Financeiro difícil de interpretar',
    text: 'Recebimentos e pendências estão registrados, mas nem sempre sobra tempo para entender o que merece atenção primeiro.',
  },
]

export default function BusinessSignalsSection() {
  return (
    <section className="gia-pain-section" aria-label="Rotina do negócio">
      <div className="gia-pain-inner">
        <ScrollReveal delay={0}>
          <span className="gia-eyebrow">Rotina do negócio</span>
          <h2 className="gia-pain-title">O problema não é ter dados. É perceber tarde demais.</h2>
          <p className="gia-pain-lead">
            Sua agenda, seus clientes e seu financeiro mudam todos os dias. O Gendaz organiza essas
            informações para você enxergar o que merece atenção.
          </p>
        </ScrollReveal>
        <div className="gia-pain-grid">
          {SIGNALS.map((s, index) => (
            <ScrollReveal key={s.num} delay={index * 80}>
              <article className="gia-pain-card">
                <span className="gia-pain-num" aria-hidden="true">{s.num}</span>
                <h3>{s.title}</h3>
                <p>{s.text}</p>
              </article>
            </ScrollReveal>
          ))}
        </div>
      </div>
    </section>
  )
}
