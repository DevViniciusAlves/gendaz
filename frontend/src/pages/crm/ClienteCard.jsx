import { Mail, Phone } from 'lucide-react'
import { exibirTelefone } from '../../utils/phoneUtils.js'

const SEGMENTO_COLORS = {
  at_risk: { bg: '#fee2e2', text: '#dc2626', dot: '#dc2626' },
  regular: { bg: '#ffedd5', text: '#ea580c', dot: '#ea580c' },
  novo: { bg: 'rgba(75, 171, 58, 0.12)', text: '#4bab3a', dot: '#4bab3a' },
}

const SEGMENTO_LABELS = { at_risk: 'Alto Risco', regular: 'Regular', novo: 'Novo' }

const TEMPLATE_LABELS = {
  resgate: 'Email resgate',
  reconexao: 'Email reconexao',
  lembrete: 'Email lembrete',
}

function formatCurrency(valor) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(valor || 0)
}

function formatDateShort(data) {
  if (!data) return '—'
  const valor = String(data)
  const partes = valor.split('-')
  if (partes.length === 3) {
    const [ano, mes, dia] = partes
    return `${dia}/${mes}/${ano}`
  }
  const d = new Date(valor)
  if (Number.isNaN(d.getTime())) return valor
  return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

function tempoRelativo(data) {
  if (!data) return '—'
  const inicio = new Date(data).getTime()
  const diffDias = Math.max(0, Math.floor((Date.now() - inicio) / (1000 * 60 * 60 * 24)))
  if (diffDias === 0) return 'hoje'
  if (diffDias === 1) return '1 dia atrás'
  return `${diffDias} dias atrás`
}

function corRisco(score) {
  if (score >= 75) return '#dc2626'
  if (score >= 45) return '#f59e0b'
  return '#111827'
}

function formatarAcao(ultimaMensagem) {
  if (!ultimaMensagem) return null

  const labelBase = TEMPLATE_LABELS[ultimaMensagem.template] || 'Contato'
  const status = ultimaMensagem.status === 'aberto' ? '(abriu ✓)' : '(enviado)'
  return `${labelBase}: ${tempoRelativo(ultimaMensagem.dataCriacao)} ${status}`
}

export default function ClienteCard({ cliente, onEnviarMensagem, onVerHistorico }) {
  const seg = SEGMENTO_COLORS[cliente.segment] || SEGMENTO_COLORS.regular
  const iniciais = (cliente.nome || 'CL').substring(0, 2).toUpperCase()
  const ultimaAcao = formatarAcao(cliente.ultimaMensagem)
  const scoreRisco = Number.isFinite(Number(cliente.scoreRisco)) ? Number(cliente.scoreRisco) : 0
  const gastoMedio = Number.isFinite(Number(cliente.gastoMedio)) ? Number(cliente.gastoMedio) : 0
  const totalGasto = Number.isFinite(Number(cliente.totalGasto)) ? Number(cliente.totalGasto) : 0

  return (
    <article className="crm-card">
      <header className="crm-card-header">
        <div className="crm-card-avatar">{iniciais}</div>

        <div className="crm-card-headline">
          <div className="crm-card-name-row">
            <span className="crm-card-name">{cliente.nome}</span>
            <span className="crm-card-badge" style={{ background: seg.bg, color: seg.text }}>
              {SEGMENTO_LABELS[cliente.segment] || cliente.segment}
            </span>
          </div>

          <div className="crm-card-contact">
            {cliente.telefone && (
              <span>
                <Phone size={10} /> {exibirTelefone(cliente.telefone)}
              </span>
            )}
            {cliente.email && (
              <span>
                <Mail size={10} /> {cliente.email}
              </span>
            )}
          </div>
        </div>
      </header>

      <section className="crm-card-metrics">
        <div className="crm-card-metric">
          <div className="crm-card-metric-value">{`Inativo ${cliente.diasSemAgendar ?? 0} dias`}</div>
          <div className="crm-card-metric-label">
            <span
              className="crm-card-risk"
              style={{
                color: corRisco(scoreRisco),
                borderColor: `${corRisco(scoreRisco)}33`,
                display: 'inline-flex',
                marginTop: 4,
              }}
            >
              {`${scoreRisco}% RISCO`}
            </span>
          </div>
        </div>
        <div className="crm-card-metric">
          <div className="crm-card-metric-value">{formatCurrency(totalGasto)}</div>
          <div className="crm-card-metric-label">
            Gasto total
            <span style={{ margin: '0 6px', opacity: 0.7 }}>|</span>
            Gasto médio: {formatCurrency(gastoMedio)}
          </div>
        </div>
        <div className="crm-card-metric">
          <div className="crm-card-metric-value">{`${cliente.padraoFrequencia ?? 0}d`}</div>
          <div className="crm-card-metric-label">Frequência</div>
        </div>
      </section>

      <section className="crm-card-agenda">
        <span><strong>Última:</strong> {formatDateShort(cliente.ultimoAgendamentoData)}</span>
        <span>
          <strong>Próxima:</strong> {cliente.proximaAgendamentoData ? formatDateShort(cliente.proximaAgendamentoData) : '—'}{' '}
          {cliente.proximaAgendamentoData ? '[Agendado]' : '[Não agendado]'}
        </span>
      </section>

      <section className="crm-card-actions-log">
        <div className="crm-card-actions-log-title">Últimas ações</div>
        {ultimaAcao ? (
          <div className="crm-card-actions-log-item">• {ultimaAcao}</div>
        ) : (
          <div className="crm-card-actions-log-item crm-card-actions-empty">Nenhuma ação registrada.</div>
        )}
      </section>

      <footer className="crm-card-footer">
        <div className="crm-card-buttons">
          {[
            { label: 'Resgatar', template: 'resgate', color: '#4bab3a' },
            { label: 'Reconexão', template: 'reconexao', color: '#dc2626' },
          ].map((btn) => (
            <button
              key={btn.template}
              className="crm-chip-btn"
              onClick={() => onEnviarMensagem?.(cliente, btn.template)}
              style={{ background: btn.color, borderColor: btn.color }}
            >
              {btn.label}
            </button>
          ))}
        </div>

        <button className="crm-history-btn" onClick={() => onVerHistorico?.(cliente)}>
          Ver histórico
        </button>
      </footer>
    </article>
  )
}
