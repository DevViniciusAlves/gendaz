import { Link } from 'react-router-dom'
import ScrollReveal from './ScrollReveal.jsx'
import GendazIAChatPreview from './GendazIAChatPreview.jsx'
import '../styles/gendazia-marketing.css'

const QUESTIONS = [
  'Quais clientes devo tentar recuperar?',
  'Qual serviço teve menos movimento?',
  'Como está meu financeiro este mês?',
]

export default function GendazIAHomeSection() {
  return (
    <section id="gendazia" className="gia-home" aria-label="gendazIA">
      <div className="gia-home-inner">
        <ScrollReveal delay={0} className="gia-home-copy">
          <span className="gia-eyebrow">gendazIA</span>
          <h2>Pergunte ao seu próprio negócio.</h2>
          <p className="gia-home-lead">
            Seu negócio gera informações todos os dias. A gendazIA usa os dados disponíveis no
            Gendaz para ajudar você a entender clientes, agenda, serviços e financeiro sem precisar
            procurar informação tela por tela.
          </p>
          <ul className="gia-home-questions">
            {QUESTIONS.map((q) => (
              <li key={q}>&ldquo;{q}&rdquo;</li>
            ))}
          </ul>
          <div className="gia-home-ctas">
            <Link to="/gendazia" className="gia-btn-primary">Conhecer a gendazIA</Link>
            <Link to="/criar-conta" className="gia-btn-secondary">Criar conta grátis</Link>
          </div>
        </ScrollReveal>
        <ScrollReveal delay={80} className="gia-home-visual">
          <GendazIAChatPreview />
        </ScrollReveal>
      </div>
    </section>
  )
}
