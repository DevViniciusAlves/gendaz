export default function InsightDetail({ insight, onClose }) {
  return (
    <div className="insights-modal-backdrop system-modal-backdrop" onClick={onClose} role="presentation">
      <article className="panel insights-modal system-modal" onClick={(e) => e.stopPropagation()}>
        <div className="insights-modal__head">
          <div>
            <span className="section-kicker">Detalhe</span>
            <h2>{insight.titulo}</h2>
          </div>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Fechar detalhe">
            ×
          </button>
        </div>
        <p>{insight.descricao}</p>
        <div className="insights-detail-grid">
          <div>
            <span>Impacto</span>
            <strong>{insight.impacto}</strong>
          </div>
          <div>
            <span>Urgência</span>
            <strong>{insight.urgencia}</strong>
          </div>
          <div>
            <span>Tipo</span>
            <strong>{insight.tipo}</strong>
          </div>
        </div>
      </article>
    </div>
  )
}
