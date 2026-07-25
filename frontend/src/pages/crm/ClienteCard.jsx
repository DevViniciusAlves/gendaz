import { Mail, Phone } from 'lucide-react'

const SEGMENTO_COLORS = {
  at_risk: { bg: '#fee2e2', text: '#dc2626', dot: '#dc2626' },
  regular: { bg: '#ffedd5', text: '#ea580c', dot: '#ea580c' },
  novo: { bg: '#dcfce7', text: '#16a34a', dot: '#16a34a' },
}

const SEGMENTO_LABELS = { at_risk: 'Alto Risco', regular: 'Regular', novo: 'Novo' }

const TEMPLATE_LABELS = {
  resgate: 'Email resgate',
  reconexao: 'Email reconexao',
  promocao: 'Email promoções',
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
  return '#16a34a'
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
                <Phone size={10} /> {cliente.telefone}
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

      <section className="crm-card-status">
        <div className="crm-card-status-row">
          <span className="crm-card-status-title">Inativo {cliente.diasSemAgendar} dias</span>
          <span className="crm-card-risk" style={{ color: corRisco(cliente.scoreRisco), borderColor: `${corRisco(cliente.scoreRisco)}33` }}>
            {cliente.scoreRisco}% RISCO
          </span>
        </div>
      </section>

      <section className="crm-card-metrics">
        <div className="crm-card-metric">
          <div className="crm-card-metric-value">{formatCurrency(cliente.totalGasto)}</div>
          <div className="crm-card-metric-label">Gasto</div>
        </div>
        <div className="crm-card-metric">
          <div className="crm-card-metric-value">{formatCurrency(cliente.gastoMedio)}</div>
          <div className="crm-card-metric-label">Gasto médio</div>
        </div>
        <div className="crm-card-metric">
          <div className="crm-card-metric-value">{cliente.padraoFrequencia}d</div>
          <div className="crm-card-metric-label">Frequência</div>
        </div>
        <div className="crm-card-metric">
          <div className="crm-card-metric-value">{cliente.visitas90d} em 90d</div>
          <div className="crm-card-metric-label">Últimas visitas</div>
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
            { label: 'Resgatar', template: 'resgate', color: '#16a34a' },
            { label: 'Reconexão', template: 'reconexao', color: '#dc2626' },
            { label: 'Promoções', template: 'promocao', color: '#075ae0' },
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
