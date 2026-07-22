import { useState } from 'react'
import { Bot, Sparkles, LineChart, MessageCircle, RefreshCw } from 'lucide-react'
import Button from '../components/Button.jsx'
import DashboardCard from '../components/DashboardCard.jsx'
import { useInsights } from '../hooks/useInsights.js'
import InsightsDashboard from './insights/InsightsDashboard.jsx'
import InsightsChat from './insights/InsightsChat.jsx'
import './insights/styles.css'

export default function Insights() {
  const [aba, setAba] = useState('dashboard')
  const { dashboard, historico, loading, error, recarregar, analisar } = useInsights()

  return (
    <section className="page insights-page">
      <div className="page-title">
        <div>
          <span className="section-kicker">Consultoria</span>
          <h1>Insights</h1>
          <p>Análises reais da empresa para destacar riscos, oportunidades e ações recomendadas.</p>
        </div>
        <Button variant="secondary" icon={RefreshCw} onClick={() => recarregar(30)}>
          Recarregar
        </Button>
      </div>

      <div className="insights-summary-grid">
        <DashboardCard title="Dashboard" value={dashboard?.scoreGeral ?? '-'} detail="Score geral" icon={LineChart} />
        <DashboardCard title="Alertas" value={dashboard?.alertas?.length ?? 0} detail="Pontos críticos" icon={Sparkles} />
        <DashboardCard title="Oportunidades" value={dashboard?.oportunidades?.length ?? 0} detail="Potencial de melhoria" icon={MessageCircle} />
        <DashboardCard title="Histórico" value={historico.length} detail="Análises registradas" icon={Bot} />
      </div>

      <div className="insights-tabs">
        <button type="button" className={aba === 'dashboard' ? 'active' : ''} onClick={() => setAba('dashboard')}>
          Dashboard
        </button>
        <button type="button" className={aba === 'chat' ? 'active' : ''} onClick={() => setAba('chat')}>
          Chat IA
        </button>
      </div>

      {loading && <div className="panel insights-panel">Carregando insights...</div>}
      {error && <div className="panel insights-panel insights-error">{error}</div>}

      {!loading && !error && aba === 'dashboard' && <InsightsDashboard dashboard={dashboard} />}

      {!loading && !error && aba === 'chat' && (
        <InsightsChat onEnviar={analisar} historico={historico} />
      )}
    </section>
  )
}
