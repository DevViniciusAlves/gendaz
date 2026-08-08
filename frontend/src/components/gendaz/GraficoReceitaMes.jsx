import { useState } from 'react'

function formatarMoeda(valor) {
  return Number(valor || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarDataDDMM(dataIso) {
  if (!dataIso) return ''
  const [ano, mes, dia] = dataIso.split('-')
  return `${dia}/${mes}`
}

function GraficoReceitaMes({ dados }) {
  const [tooltip, setTooltip] = useState(null)
  const temDados = Array.isArray(dados) && dados.length > 0
  const width = 760
  const height = 240
  const pLeft = 42
  const pRight = 18
  const pTop = 36
  const pBottom = 28
  const chartW = width - pLeft - pRight
  const chartH = height - pTop - pBottom

  // Filtrar apenas dias com valor > 0
  const pontosComValor = (dados || []).filter((d) => Number(d.valor || 0) > 0)
  const pontos = pontosComValor.map((d) => ({
    ...d,
    valor: Number(d.valor || 0),
  }))

  // Se não houver pontos com valor, retorna estado vazio
  if (!pontos.length) {
    return (
      <div className="gendaz-receita-mes-empty">
        <BarChart2 size={40} color="var(--primary)" />
        <p>Nenhuma receita registrada neste mês.</p>
        <small>Os valores aparecerão aqui conforme os pagamentos confirmados entrarem no período.</small>
      </div>
    )
  }

  // Escala Y automática
  const maxValor = Math.max(...pontos.map((p) => p.valor), 1)

  // Posicionamento dos pontos
  const pontosPosicionados = pontos.map((p, index) => {
    const x = pLeft + (index / Math.max(pontos.length - 1, 1)) * chartW
    const y = pTop + chartH - (p.valor / maxValor) * chartH
    return { ...p, x, y }
  })

  // Se só houver 1 ponto, não desenhar linha
  const desenharLinha = pontos.length > 1

  // Path da linha
  let pathLinha = ''
  if (desenharLinha) {
    pathLinha = `M ${pontosPosicionados[0].x} ${pontosPosicionados[0].y}`
    for (let i = 1; i < pontosPosicionados.length; i++) {
      const atual = pontosPosicionados[i]
      const anterior = pontosPosicionados[i - 1]
      const pontoMeioX = (anterior.x + atual.x) / 2
      pathLinha += ` C ${pontoMeioX} ${anterior.y}, ${pontoMeioX} ${atual.y}, ${atual.x} ${atual.y}`
    }
  }

  // Path da área (preenchimento)
  let pathArea = ''
  if (desenharLinha) {
    pathArea = `M ${pontosPosicionados[0].x} ${pTop + chartH}`
    pathArea += ` L ${pontosPosicionados[0].x} ${pontosPosicionados[0].y}`
    for (let i = 1; i < pontosPosicionados.length; i++) {
      const atual = pontosPosicionados[i]
      const anterior = pontosPosicionados[i - 1]
      const pontoMeioX = (anterior.x + atual.x) / 2
      pathArea += ` C ${pontoMeioX} ${anterior.y}, ${pontoMeioX} ${atual.y}, ${atual.x} ${atual.y}`
    }
    for (let i = pontosPosicionados.length - 1; i >= 0; i--) {
      pathArea += ` L ${pontosPosicionados[i].x} ${pTop + chartH}`
    }
    pathArea += ' Z'
  }

  // Linhas de grade Y
  const gridFracs = [0, 0.25, 0.5, 0.75, 1]

  return (
    <div className="gendaz-receita-mes-shell">
      <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Gráfico de receita por dia" style={{ width: '100%', height: '100%', overflow: 'visible' }}>
        {/* Grade Y */}
        {gridFracs.map((frac) => {
          const y = pTop + chartH * (1 - frac)
          const val = maxValor * frac
          return (
            <g key={frac}>
              <line x1={pLeft} y1={y} x2={width - pRight} y2={y} stroke="#e0e0e0" strokeWidth={1} />
              <text x={pLeft - 6} y={y + 4} textAnchor="end" fontSize={10} fill="#666">
                {formatarMoeda(val)}
              </text>
            </g>
          )
        })}

        {/* Área preenchida */}
        {pathArea && (
          <path d={pathArea} fill="rgba(255, 255, 255, 0.1)" stroke="none" />
        )}

        {/* Linha */}
        {desenharLinha && (
          <path d={pathLinha} fill="none" stroke="#ffffff" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
        )}

        {/* Pontos (círculos) */}
        {pontosPosicionados.map((ponto, index) => {
          const isHovered = tooltip?.index === index
          return (
            <g key={ponto.iso}>
              <circle
                cx={ponto.x}
                cy={ponto.y}
                r={4}
                fill={isHovered ? '#ffffff' : '#ffffff'}
                stroke="#000"
                strokeWidth={2}
                onMouseEnter={() => setTooltip({ index, x: ponto.x, y: ponto.y, valor: ponto.valor, label: ponto.label, iso: ponto.iso })}
                onMouseLeave={() => setTooltip(null)}
                style={{ cursor: 'pointer' }}
              />
              <text x={ponto.x} y={height - 8} textAnchor="middle" fontSize={10} fill="#666">
                {ponto.label}
              </text>
            </g>
          )
        })}

        {/* Tooltip */}
        {tooltip && (
          <g style={{ pointerEvents: 'none' }}>
            <rect x={tooltip.x - 52} y={tooltip.y - 45} width={104} height={38} rx={4} fill="#000" />
            <text x={tooltip.x} y={tooltip.y - 31} textAnchor="middle" fontSize={10} fill="#fff">
              {tooltip.label}
            </text>
            <text x={tooltip.x} y={tooltip.y - 16} textAnchor="middle" fontSize={12} fill="#fff" fontWeight={700}>
              {formatarMoeda(tooltip.valor)}
            </text>
          </g>
        )}
      </svg>
    </div>
  )
}

// Fallback para icone
function BarChart2({ size = 24, color = '#000' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={color}>
      <path d="M18 3H6C4.89 3 4 3.89 4 5V19C4 20.11 4.89 21 6 21H18C19.11 21 20 20.11 20 19V5C20 3.89 19.11 3 18 3ZM18 19H6V5H18V19Z" />
    </svg>
  )
}

export default GraficoReceitaMes