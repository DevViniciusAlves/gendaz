import { CalendarPlus, RotateCw, X } from 'lucide-react'
import { useCliente } from '../../context/ClienteContext.jsx'

export default function Agenda() {
  const { portal } = useCliente()

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Agenda</span>
        <h1>Próximos agendamentos</h1>
        <p>Aqui ficam apenas os compromissos futuros.</p>
      </header>

      <div className="gendaz-actions">
        <button className="gendaz-btn gendaz-btn--primary" type="button"><CalendarPlus size={16} />Novo agendamento</button>
      </div>

      <div className="gendaz-list-cards">
        {portal.agendamentos.map((item) => (
          <article key={item.id} className="gendaz-card">
            <strong>{item.servico}</strong>
            <span>{item.profissional}</span>
            <small>{new Date(`${item.data}T12:00:00`).toLocaleDateString('pt-BR')} às {item.hora}</small>
            <div className="gendaz-card__actions">
              <button className="gendaz-btn" type="button"><RotateCw size={16} />Reagendar</button>
              <button className="gendaz-btn gendaz-btn--ghost" type="button"><X size={16} />Cancelar</button>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}
