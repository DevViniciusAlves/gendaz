import ScrollReveal from './ScrollReveal.jsx'
import TestimonialCard from './TestimonialCard.jsx'

const testimonials = [
  {
    quote: 'Com a Gendaz, conseguimos organizar os agendamentos e reduzir bastante as mensagens manuais. Hoje, todo o atendimento ficou mais simples.',
    name: 'Mariana Alves',
    segment: 'Clínica de estética',
    avatar: 'MA',
  },
  {
    quote: 'Meus clientes conseguem agendar sozinhos, e tudo aparece automaticamente no painel. Economizo tempo todos os dias.',
    name: 'Rafael Mendes',
    segment: 'Barbearia',
    avatar: 'RM',
  },
  {
    quote: 'O controle dos atendimentos e pagamentos ficou muito mais organizado. A plataforma é simples e fácil de usar.',
    name: 'Camila Rocha',
    segment: 'Studio de beleza',
    avatar: 'CR',
  },
]

export default function TestimonialsSection() {
  return (
    <section className="marketing-testimonials" aria-label="Depoimentos de clientes">
      <ScrollReveal className="marketing-testimonials__head" delay={0}>
        <h2>Quem usa a Gendaz recomenda</h2>
        <p>Clientes que organizam a rotina com mais clareza, menos retrabalho e um atendimento mais rápido.</p>
      </ScrollReveal>

      <div className="marketing-testimonials__grid">
        {testimonials.map((item, index) => (
          <ScrollReveal key={item.name} delay={index * 80}>
            <TestimonialCard {...item} />
          </ScrollReveal>
        ))}
      </div>
    </section>
  )
}
