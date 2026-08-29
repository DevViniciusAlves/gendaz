import { useState } from 'react'
import InsightCard from './InsightCard.jsx'
import InsightDetail from './InsightDetail.jsx'

export default function InsightsDashboard({ dashboard }) {
  const [selecionado, setSelecionado] = useState(null)

  if (!dashboard) {
    return <div className="panel insights-panel">Nenhum dado disponível para análise.</div>
  }

  return (
    <div className="insights-dashboard">
      <section className="panel insights-score">
        <div>
          <span className="section-kicker">Saúde do negócio</span>
          <h2>Score geral</h2>
          <p>Leitura consolidada do período com base nos dados da empresa.</p>
        </div>
        <div className="insights-score__value">{dashboard.scoreGeral ?? '-'}</div>
      </section>

      <section className="panel">
        <h2>Alertas</h2>
        <div className="insights-grid">
          {(dashboard.alertas || []).map((item, index) => (
            <InsightCard
              key={`${item.titulo || 'alerta'}-${index}`}
              insight={item}
              tipo="alerta"
              onClick={() => setSelecionado(item)}
            />
          ))}
          {(dashboard.alertas || []).length === 0 && <p className="insights-empty">Sem alertas no momento.</p>}
        </div>
      </section>

      <section className="panel">
        <h2>Oportunidades</h2>
        <div className="insights-grid">
          {(dashboard.oportunidades || []).map((item, index) => (
            <InsightCard
              key={`${item.titulo || 'oportunidade'}-${index}`}
              insight={item}
              tipo="oportunidade"
              onClick={() => setSelecionado(item)}
            />
          ))}
          {(dashboard.oportunidades || []).length === 0 && <p className="insights-empty">Sem oportunidades destacadas.</p>}
        </div>
      </section>

      <section className="panel">
        <h2>Ações recomendadas</h2>
        <div className="insights-actions">
          {(dashboard.ações || []).map((ação, index) => (
            <div key={`${ação.descrição || 'ação'}-${index}`} className="insights-action">
              <div>
                <strong>{ação.descrição}</strong>
                <p>{ação.impactoEstimado}</p>
              </div>
              <span>{ação.urgencia}</span>
            </div>
          ))}
          {(dashboard.ações || []).length === 0 && <p className="insights-empty">Sem ações sugeridas.</p>}
        </div>
      </section>

      <section className="panel insights-impact">
        <h2>Impacto total estimado</h2>
        <strong>{dashboard.impactoTotal || '-'}</strong>
      </section>

      {selecionado && <InsightDetail insight={selecionado} onClose={() => setSelecionado(null)} />}
    </div>
  )
}
