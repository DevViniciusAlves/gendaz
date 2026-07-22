export default function InsightCard({ insight, tipo, onClick }) {
  return (
    <button type="button" className={`insights-card insights-card--${tipo}`} onClick={onClick}>
      <div className="insights-card__head">
        <strong>{insight.titulo}</strong>
        <span>{insight.urgencia}</span>
      </div>
      <p>{insight.descricao}</p>
      <small>{insight.impacto}</small>
    </button>
  )
}
