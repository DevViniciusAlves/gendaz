import { Mail, Phone } from 'lucide-react'
import { useState } from 'react'

const SEGMENTO_COLORS = {
  at_risk: { bg: 'var(--surface-soft)', text: 'var(--text)', dot: 'var(--text)' },
  regular: { bg: 'var(--surface-soft)', text: 'var(--text)', dot: 'var(--muted)' },
  novo: { bg: 'var(--surface-soft)', text: 'var(--text)', dot: 'var(--muted)' },
}

const SEGMENTO_LABELS = { at_risk: 'At-risk', regular: 'Regular', novo: 'Novo' }

function formatCurrency(valor) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(valor || 0)
}

function formatarData(data) {
  if (!data) return '-'
  const d = new Date(data)
  return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }) + ' ' + d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
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
      padding: 16,
      display: 'flex',
      flexDirection: 'column',
      gap: 12,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div style={{
          width: 36,
          height: 36,
          borderRadius: '50%',
          background: 'var(--text)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--surface-solid, var(--surface-strong, var(--surface)))',
          fontWeight: 700,
          fontSize: 12,
          flexShrink: 0,
        }}>
          {iniciais}
        </div>

        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
            <span style={{ color: 'var(--text)', fontWeight: 600, fontSize: 14 }}>{cliente.nome}</span>
            <span style={{
              padding: '1px 8px',
              borderRadius: 20,
              fontSize: 11,
              fontWeight: 600,
              background: seg.bg,
              color: seg.text,
            }}>
              {SEGMENTO_LABELS[cliente.segment] || cliente.segment}
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 2 }}>
            {cliente.telefone && (
              <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--muted)', fontSize: 11 }}>
                <Phone size={10} /> {cliente.telefone}
              </span>
            )}
            {cliente.email && (
              <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--muted)', fontSize: 11 }}>
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
        padding: '10px 0',
      }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ color: 'var(--text)', fontWeight: 700, fontSize: 12 }}>{cliente.diasSemAgendar}</div>
          <div style={{ color: 'var(--muted)', fontSize: 10 }}>Dias</div>
        </div>
        <div style={{ textAlign: 'center' }}>
          <div style={{ color: 'var(--text)', fontWeight: 700, fontSize: 12 }}>{formatCurrency(cliente.totalGasto)}</div>
          <div style={{ color: 'var(--muted)', fontSize: 10 }}>Gasto</div>
        </div>
        <div style={{ textAlign: 'center' }}>
          <div style={{ color: 'var(--text)', fontWeight: 700, fontSize: 12 }}>{cliente.agendamentos}</div>
          <div style={{ color: 'var(--muted)', fontSize: 10 }}>Agd</div>
        </div>
        <div style={{ textAlign: 'center' }}>
          <div style={{ color: 'var(--text)', fontWeight: 700, fontSize: 12 }}>{cliente.padraoFrequencia}d</div>
          <div style={{ color: 'var(--muted)', fontSize: 10 }}>Padrao</div>
        </div>
      </div>

      {ultimaMsg && (
        <div style={{ fontSize: 11, color: 'var(--muted)' }}>
          Ultima: <span style={{ color: 'var(--text)' }}>"{cliente.ultimaMensagem.template}"</span> - {formatarData(cliente.ultimaMensagem.dataCriacao)}
          <span style={{ marginLeft: 6 }}>
            {cliente.ultimaMensagem.status === 'aberto' ? '✅' : '❌'}
          </span>
        </div>
      )}

      <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', alignItems: 'center' }}>
        {[
          { label: 'Resgate', template: 'resgate' },
          { label: 'Reconexao', template: 'reconexao' },
          { label: 'Promo', template: 'promocao' },
        ].map((btn) => (
          <button
            key={btn.template}
            onClick={() => onEnviarMensagem?.(cliente, btn.template)}
            style={{
              padding: '4px 10px',
              border: '1px solid var(--text)',
              borderRadius: 4,
              background: 'var(--text)',
              color: 'var(--surface-solid, var(--surface-strong, var(--surface)))',
              fontSize: 11,
              fontWeight: 600,
              cursor: 'pointer',
              transition: 'all 0.15s',
              height: 28,
            }}
            onMouseEnter={(e) => { e.target.style.background = 'var(--text)'; e.target.style.color = 'var(--surface-solid, var(--surface-strong, var(--surface)))'; e.target.style.transform = 'scale(1.02)' }}
            onMouseLeave={(e) => { e.target.style.background = 'var(--text)'; e.target.style.color = 'var(--surface-solid, var(--surface-strong, var(--surface)))'; e.target.style.transform = 'scale(1)' }}
          >
            {btn.label}
          </button>
        ))}
        <button
          onClick={() => onVerHistorico?.(cliente)}
          style={{
            padding: '4px 10px',
            border: 'none',
            background: 'transparent',
            color: 'var(--text)',
            fontSize: 11,
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
