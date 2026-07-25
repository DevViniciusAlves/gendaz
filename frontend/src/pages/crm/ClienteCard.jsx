import { Mail, Phone } from 'lucide-react'
import { useState } from 'react'

const SEGMENTO_COLORS = {
  at_risk: { bg: '#fee2e2', text: '#dc2626', dot: '#dc2626' },
  regular: { bg: '#ffedd5', text: '#ea580c', dot: '#ea580c' },
  novo: { bg: '#dcfce7', text: '#16a34a', dot: '#16a34a' },
}

const SEGMENTO_LABELS = { at_risk: 'Alto Risco', regular: 'Regular', novo: 'Novo' }

function formatCurrency(valor) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(valor || 0)
}

function formatarData(data) {
  if (!data) return '-'
  const d = new Date(data)
  return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

export default function ClienteCard({ cliente, onEnviarMensagem, onVerHistorico }) {
  const seg = SEGMENTO_COLORS[cliente.segment] || SEGMENTO_COLORS.regular
  const iniciais = (cliente.nome || 'CL').substring(0, 2).toUpperCase()
  const ultimaMsg = cliente.ultimaMensagem && cliente.ultimaMensagem.template

  return (
    <div style={{
      background: 'var(--surface-solid, var(--surface-strong, var(--surface)))',
      border: '1px solid var(--line)',
      borderRadius: 10,
      padding: '10px 12px',
      display: 'flex',
      flexDirection: 'column',
      gap: 8,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <div style={{
          width: 28,
          height: 28,
          borderRadius: '50%',
          background: 'var(--text)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--surface-solid, var(--surface-strong, var(--surface)))',
          fontWeight: 700,
          fontSize: 10,
          flexShrink: 0,
        }}>
          {iniciais}
        </div>

        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
            <span style={{ color: 'var(--text)', fontWeight: 600, fontSize: 13 }}>{cliente.nome}</span>
            <span style={{
              padding: '1px 6px',
              borderRadius: 20,
              fontSize: 10,
              fontWeight: 600,
              background: seg.bg,
              color: seg.text,
            }}>
              {SEGMENTO_LABELS[cliente.segment] || cliente.segment}
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 1, flexWrap: 'wrap' }}>
            {cliente.telefone && (
              <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--muted)', fontSize: 10 }}>
                <Phone size={10} /> {cliente.telefone}
              </span>
            )}
            {cliente.email && (
              <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--muted)', fontSize: 10 }}>
                <Mail size={10} /> {cliente.email}
              </span>
            )}
          </div>
        </div>
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(4, 1fr)',
        gap: 8,
        borderTop: '1px solid var(--line)',
        borderBottom: '1px solid var(--line)',
        padding: '7px 0',
      }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ color: 'var(--text)', fontWeight: 700, fontSize: 10 }}>{formatarData(cliente.ultimoAgendamentoData)}</div>
          <div style={{ color: 'var(--muted)', fontSize: 10 }}>Último agendamento</div>
        </div>
        <div style={{ textAlign: 'center' }}>
          <div style={{ color: 'var(--text)', fontWeight: 700, fontSize: 10 }}>{formatCurrency(cliente.totalGasto)}</div>
          <div style={{ color: 'var(--muted)', fontSize: 10 }}>Gasto</div>
        </div>
        <div style={{ textAlign: 'center' }}>
          <div style={{ color: 'var(--text)', fontWeight: 700, fontSize: 10 }}>{cliente.agendamentos}</div>
          <div style={{ color: 'var(--muted)', fontSize: 10 }}>Agendamentos</div>
        </div>
        <div style={{ textAlign: 'center' }}>
          <div style={{ color: 'var(--text)', fontWeight: 700, fontSize: 10 }}>{cliente.padraoFrequencia}d</div>
          <div style={{ color: 'var(--muted)', fontSize: 10 }}>Frequência</div>
        </div>
      </div>

      {ultimaMsg && (
        <div style={{ fontSize: 10, color: 'var(--muted)' }}>
          Ultima: <span style={{ color: 'var(--text)' }}>"{cliente.ultimaMensagem.template}"</span>
        </div>
      )}

      <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', alignItems: 'center' }}>
        {[
          { label: 'Resgate', template: 'resgate', color: '#16a34a' },
          { label: 'Reconexao', template: 'reconexao', color: '#dc2626' },
          { label: 'Promoções', template: 'promocao', color: '#075ae0' },
        ].map((btn) => (
          <button
            key={btn.template}
            onClick={() => onEnviarMensagem?.(cliente, btn.template)}
            style={{
              padding: '3px 8px',
              border: `1px solid ${btn.color}`,
              borderRadius: 4,
              background: btn.color,
              color: '#fff',
              fontSize: 10,
              fontWeight: 600,
              cursor: 'pointer',
              transition: 'all 0.15s',
              height: 24,
            }}
            onMouseEnter={(e) => { e.target.style.opacity = '0.9'; e.target.style.transform = 'scale(1.02)' }}
            onMouseLeave={(e) => { e.target.style.opacity = '1'; e.target.style.transform = 'scale(1)' }}
          >
            {btn.label}
          </button>
        ))}
        <button
          onClick={() => onVerHistorico?.(cliente)}
          style={{
            padding: '3px 8px',
            border: 'none',
            background: 'transparent',
            color: 'var(--text)',
            fontSize: 10,
            cursor: 'pointer',
            textDecoration: 'underline',
            marginLeft: 'auto',
          }}
        >
          Ver historico
        </button>
      </div>
    </div>
  )
}
