import { Search } from 'lucide-react'

const SEGMENTO_OPTIONS = [
  { value: 'todos', label: 'Todos', cor: '' },
  { value: 'at_risk', label: 'At-risk', cor: '#EF4444' },
  { value: 'vip', label: 'VIP', cor: '#42f569' },
  { value: 'regular', label: 'Regular', cor: '#F59E0B' },
  { value: 'novo', label: 'Novo', cor: '#3B82F6' },
]

const ORDENACAO_OPTIONS = [
  { value: 'recente', label: 'Ultimos adicionados' },
  { value: 'maior_gasto', label: 'Maior gasto' },
  { value: 'menor_gasto', label: 'Menor gasto' },
  { value: 'dias_sem_agendar_asc', label: 'Dias sem agendar ↑' },
  { value: 'dias_sem_agendar_desc', label: 'Dias sem agendar ↓' },
]

const PERIODO_OPTIONS = [
  { value: 30, label: 'Ultimos 30 dias' },
  { value: 60, label: 'Ultimos 60 dias' },
  { value: 90, label: 'Ultimos 90 dias' },
  { value: 180, label: 'Ultimos 180 dias' },
  { value: 0, label: 'Todos os tempos' },
]

const inputStyle = {
  width: '100%',
  height: 36,
  padding: '8px 12px',
  border: '1px solid rgba(255,255,255,0.2)',
  borderRadius: 6,
  background: 'rgba(255,255,255,0.05)',
  color: '#fff',
  fontSize: 12,
  outline: 'none',
  boxSizing: 'border-box',
}

export default function CrmFilters({ filtros, onFiltroChange, onLimpar }) {
  return (
    <div style={{
      width: 260,
      flexShrink: 0,
      background: 'rgba(255,255,255,0.05)',
      border: '1px solid rgba(255,255,255,0.1)',
      borderRadius: 10,
      padding: 16,
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
      height: 'fit-content',
      position: 'sticky',
      top: 24,
    }}>
      <span style={{ color: '#fff', fontWeight: 700, fontSize: 13 }}>Filtros</span>

      <div>
        <span style={{ color: '#9ca3af', fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 6 }}>Segmentacao</span>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {SEGMENTO_OPTIONS.map((opt) => (
            <label key={opt.value} style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer', fontSize: 13, color: '#d1d5db' }}>
              <input
                type="radio"
                name="segmento"
                value={opt.value}
                checked={filtros.segment === opt.value}
                onChange={() => onFiltroChange({ segment: opt.value })}
                style={{ accentColor: '#42f569', width: 14, height: 14, cursor: 'pointer' }}
              />
              {opt.cor && <span style={{ width: 7, height: 7, borderRadius: '50%', background: opt.cor, flexShrink: 0 }} />}
              {opt.label}
            </label>
          ))}
        </div>
      </div>

      <div>
        <span style={{ color: '#9ca3af', fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 6 }}>Buscar</span>
        <div style={{ position: 'relative' }}>
          <Search size={12} style={{ position: 'absolute', left: 8, top: '50%', transform: 'translateY(-50%)', color: '#9ca3af' }} />
          <input
            type="text"
            placeholder="Nome, telefone..."
            value={filtros.search}
            onChange={(e) => onFiltroChange({ search: e.target.value })}
            style={{ ...inputStyle, paddingLeft: 28 }}
          />
        </div>
      </div>

      <div>
        <span style={{ color: '#9ca3af', fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 6 }}>Ordenar por</span>
        <select
          value={filtros.orderBy}
          onChange={(e) => onFiltroChange({ orderBy: e.target.value })}
          style={inputStyle}
        >
          {ORDENACAO_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>
      </div>

      <div>
        <span style={{ color: '#9ca3af', fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 6 }}>Periodo</span>
        <select
          value={filtros.period}
          onChange={(e) => onFiltroChange({ period: Number(e.target.value) })}
          style={inputStyle}
        >
          {PERIODO_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>
      </div>

      <button
        onClick={onLimpar}
        style={{
          background: 'transparent',
          border: 'none',
          color: '#9ca3af',
          cursor: 'pointer',
          fontSize: 12,
          padding: '4px 0',
          textAlign: 'left',
          transition: 'color 0.2s',
        }}
        onMouseEnter={(e) => e.target.style.color = '#42f569'}
        onMouseLeave={(e) => e.target.style.color = '#9ca3af'}
      >
        Limpar filtros
      </button>
    </div>
  )
}
