import { useMemo, useState } from 'react'

function formatarMoeda(valor) {
  return Number(valor || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarDataDDMM(dataIso, fallback = '') {
  if (!dataIso) return fallback
  const [, mes, dia] = String(dataIso).split('-')
  if (!dia || !mes) return fallback
  return `${dia}/${mes}`
}

function criarPathSuave(pontos) {
  if (!pontos.length) return ''
  if (pontos.length === 1) return `M ${pontos[0].x} ${pontos[0].y}`

  const comandos = [`M ${pontos[0].x} ${pontos[0].y}`]
  for (let i = 0; i < pontos.length - 1; i++) {
    const atual = pontos[i]
    const proximo = pontos[i + 1]
    const pontoMeioX = (atual.x + proximo.x) / 2
    comandos.push(`C ${pontoMeioX} ${atual.y}, ${pontoMeioX} ${proximo.y}, ${proximo.x} ${proximo.y}`)
  }
  return comandos.join(' ')
}

function arredondarTopoEscala(valor) {
  if (!valor || valor <= 0) return 100
  const potencia = 10 ** Math.floor(Math.log10(valor))
  const normalizado = valor / potencia
  const fator = normalizado <= 1 ? 1 : normalizado <= 2 ? 2 : normalizado <= 5 ? 5 : 10
  return fator * potencia
}

function obterPassoLabel(totalDias) {
  if (totalDias <= 7) return 1
  if (totalDias <= 15) return 2
  if (totalDias <= 24) return 4
  return 5
}

function GraficoReceitaMes({ dados, formatarEixoY }) {
  const [tooltip, setTooltip] = useState(null)
  const dadosNormalizados = useMemo(() => (Array.isArray(dados) ? dados : []).map((item, index) => ({
    ...item,
    indice: index,
    valor: Number(item?.valor || 0),
  })), [dados])

  const width = 760
  const height = 280
  const pLeft = 58
  const pRight = 18
  const pTop = 18
  const pBottom = 34
  const chartW = width - pLeft - pRight
  const chartH = height - pTop - pBottom
  const baseY = pTop + chartH
  const maxValor = arredondarTopoEscala(Math.max(...dadosNormalizados.map((item) => item.valor), 0))
  const labelStep = obterPassoLabel(dadosNormalizados.length)
  const gridTicks = [0, 0.25, 0.5, 0.75, 1]

  const pontos = dadosNormalizados.map((item, index) => {
    const x = pLeft + (index / Math.max(dadosNormalizados.length - 1, 1)) * chartW
    const y = baseY - (item.valor / maxValor) * chartH
    return { ...item, x, y }
  })
  const pathLinha = criarPathSuave(pontos)
  const pathArea = pontos.length
    ? `${pathLinha} L ${pontos[pontos.length - 1].x} ${baseY} L ${pontos[0].x} ${baseY} Z`
    : ''
  const tooltipAtivo = tooltip !== null ? pontos[tooltip] : null

  function ativarTooltip(index) {
    setTooltip(index)
  }

  function limparTooltip() {
    setTooltip(null)
  }

  return (
    <div className="gendaz-receita-mes-shell">
      {dadosNormalizados.length === 0 && (
        <div className="gendaz-receita-mes-empty" aria-live="polite">
          <p>Nenhuma receita registrada neste mês.</p>
          <small>Os valores aparecerão conforme os pagamentos forem confirmados.</small>
        </div>
      )}

      <svg
        className="gendaz-receita-mes-svg"
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label="Gráfico de receita confirmada por dia do mês"
        preserveAspectRatio="xMidYMid meet"
        onMouseLeave={limparTooltip}
      >
        <defs>
          <linearGradient id="receitaMesAreaGradient" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#ff6d24" stopOpacity="0.42" />
            <stop offset="45%" stopColor="#ff6d24" stopOpacity="0.18" />
            <stop offset="100%" stopColor="#ff6d24" stopOpacity="0.02" />
          </linearGradient>
        </defs>

        {gridTicks.map((tick) => {
          const y = baseY - chartH * tick
          const valor = maxValor * tick
          return (
            <g key={tick} className="gendaz-receita-grid-row">
              <line x1={pLeft} y1={y} x2={width - pRight} y2={y} />
              <text x={pLeft - 12} y={y + 4} textAnchor="end">
                {formatarEixoY ? formatarEixoY(valor) : formatarMoeda(valor)}
              </text>
            </g>
          )
        })}

        {pathArea && <path className="gendaz-receita-area" d={pathArea} />}
        {pathLinha && <path className="gendaz-receita-line" d={pathLinha} />}

        {pontos.map((ponto, index) => {
          const dia = String(ponto.iso || '').slice(8, 10) || String(ponto.label || '').slice(0, 2)
          const mostrarLabel = index === 0 || index === pontos.length - 1 || index % labelStep === 0
          const ativo = tooltip === index
          return (
            <g key={ponto.iso || index}>
              {mostrarLabel && (
                <text className="gendaz-receita-x-label" x={ponto.x} y={height - 9} textAnchor="middle">
                  {dia}
                </text>
              )}
              {ponto.valor > 0 && (
                <circle
                  className="gendaz-receita-dot"
                  cx={ponto.x}
                  cy={ponto.y}
                  r="3.4"
                />
              )}
              {pontos.length === 1 && (
                <circle
                  className="gendaz-receita-active-dot"
                  cx={ponto.x}
                  cy={ponto.y}
                  r="5"
                />
              )}
              <circle
                className="gendaz-receita-hit-area"
                cx={ponto.x}
                cy={ponto.y}
                r="13"
                onMouseEnter={() => ativarTooltip(index)}
                onFocus={() => ativarTooltip(index)}
                onTouchStart={() => ativarTooltip(index)}
                tabIndex="0"
                aria-label={`${formatarDataDDMM(ponto.iso, ponto.label)}: ${formatarMoeda(ponto.valor)}`}
              />
              {ativo && (
                <circle
                  className="gendaz-receita-active-dot"
                  cx={ponto.x}
                  cy={ponto.y}
                  r="5"
                />
              )}
            </g>
          )
        })}

        {tooltipAtivo && (
          <g className="gendaz-receita-tooltip" style={{ pointerEvents: 'none' }}>
            {(() => {
              const tooltipW = 116
              const tooltipH = 54
              const x = Math.min(Math.max(tooltipAtivo.x - tooltipW / 2, pLeft), width - pRight - tooltipW)
              const y = Math.max(8, tooltipAtivo.y - tooltipH - 14)
              return (
                <>
                  <rect x={x} y={y} width={tooltipW} height={tooltipH} rx="12" />
                  <text className="gendaz-receita-tooltip-date" x={x + 14} y={y + 21}>
                    {formatarDataDDMM(tooltipAtivo.iso, tooltipAtivo.label)}
                  </text>
                  <text className="gendaz-receita-tooltip-value" x={x + 14} y={y + 40}>
                    {formatarMoeda(tooltipAtivo.valor)}
                  </text>
                </>
              )
            })()}
          </g>
        )}
      </svg>
    </div>
  )
}

export default GraficoReceitaMes
