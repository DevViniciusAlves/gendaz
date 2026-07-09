import { CalendarPlus, Clock3, RotateCw, X } from 'lucide-react'
import { useCliente } from '../../context/ClienteContext.jsx'

export default function Agenda() {
  const { portal } = useCliente()

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Agenda</span>
        <h1>Próximos agendamentos</h1>
        <p>Aqui ficam apenas os compromissos futuros. Histórico não aparece nesta aba.</p>
      </header>

      <div className="gendaz-actions">
        <button className="gendaz-btn gendaz-btn--primary" type="button">
          <CalendarPlus size={16} />Novo agendamento
        </button>
      </div>

      <div className="gendaz-table">
        {portal.agendamentos.map((item) => (
          <article key={item.id} className="gendaz-table__row gendaz-table__row--agenda">
            <div>
              <strong>{item.servico}</strong>
              <small>{item.profissional}</small>
            </div>
            <div>
              <span>{new Date(`${item.data}T12:00:00`).toLocaleDateString('pt-BR')}</span>
              <small><Clock3 size={14} /> {item.hora}</small>
            </div>
            <div>
              <span>Status</span>
              <strong>{item.status}</strong>
            </div>
            <small>{item.observacao}</small>
            <div className="gendaz-card__actions">
              <button className="gendaz-btn" type="button"><RotateCw size={16} />Reagendar</button>
              <button className="gendaz-btn gendaz-btn--ghost" type="button"><X size={16} />Cancelar</button>
            </div>
          </article>
        ))}
      </div>

      <article className="gendaz-panel">
        <div className="gendaz-panel__head">
          <Clock3 size={18} />
          <h2>Lista de espera</h2>
        </div>
        <p>Recurso futuro. Quando disponível, aparecerá aqui para encaixes inteligentes.</p>
      </article>
    </section>
  )
}
