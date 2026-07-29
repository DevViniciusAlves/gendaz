import { useContext, useEffect, useState } from 'react'
import { RefreshContext } from '../context/RefreshContext.jsx'
import { BarChart2, CalendarDays, CheckCircle, Circle, CreditCard, MessageCircle, RefreshCw, TrendingUp, UserPlus, Wrench } from 'lucide-react'
import { Link } from 'react-router-dom'
import Button from '../components/Button.jsx'
import ScrollReveal from '../components/ScrollReveal.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import Table from '../components/Table.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { currency, PLANOS, todayIso } from '../services/localStore.js'

const STATUS_PAGAMENTO_CONFIRMADO = new Set([
  'PAGO',
  'PAGA',
  'CONFIRMADO',
  'CONFIRMADA',
  'APROVADO',
  'APPROVED',
  'PAID',
  'PAYMENT_APPROVED',
  'PURCHASE_APPROVED',
])

function diasDoMesAtual() {
  const hoje = new Date(`${todayIso()}T12:00:00`)
  return new Date(hoje.getFullYear(), hoje.getMonth() + 1, 0).getDate()
}

function normalizarStatusPagamento(status) {
  return String(status || '').toUpperCase()
}

function pagamentoConfirmado(status) {
  return STATUS_PAGAMENTO_CONFIRMADO.has(normalizarStatusPagamento(status))
}

function extrairDataReceita(pagamento) {
  return String(pagamento?.dataPagamento || pagamento?.dataCriacao || pagamento?.data || pagamento?.createdAt || '').slice(0, 10)
}

function buildReceitaMes(pagamentos) {
  const hoje = new Date(`${todayIso()}T12:00:00`)
  const dias = diasDoMesAtual()
  const mapaReceita = {}

  pagamentos.forEach((p) => {
    if (!pagamentoConfirmado(p.status)) return
    const dia = extrairDataReceita(p)
    if (!dia) return
    if (!dia.startsWith(`${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}`)) return
    mapaReceita[dia] = (mapaReceita[dia] || 0) + Number(p.valor || 0)
  })

  const resultado = []
  const inicioMes = new Date(hoje.getFullYear(), hoje.getMonth(), 1, 12, 0, 0, 0)
  for (let i = 0; i < dias; i++) {
    const data = new Date(inicioMes)
    data.setDate(data.getDate() + i)
    const iso = data.toISOString().slice(0, 10)
    const label = data.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
    resultado.push({ iso, label, valor: mapaReceita[iso] || 0 })
  }
  return resultado
}

function normalizarReceitaDias(dados) {
  return (dados || []).map((item) => ({
    iso: item.iso || item.data || '',
    label: item.label || String(item.data || '').slice(8, 10) || '',
    valor: Number(item.valor || 0),
  }))
}

function combinarReceitaMensal(base, alternativa) {
  const mapa = new Map()
  ;(alternativa || []).forEach((item) => {
    if (!item?.iso) return
    mapa.set(item.iso, { ...item, valor: Number(item.valor || 0) })
  })
  ;(base || []).forEach((item) => {
    if (!item?.iso) return
    const atual = mapa.get(item.iso)
    const valorBase = Number(item.valor || 0)
    const valorAtual = Number(atual?.valor || 0)
    if (!atual || valorBase >= valorAtual) {
      mapa.set(item.iso, { ...item, valor: valorBase })
    }
  })

  const referencia = (base || []).length >= (alternativa || []).length ? base : alternativa
  return (referencia || []).map((item) => mapa.get(item.iso) || { ...item, valor: Number(item.valor || 0) })
}

function resumirReceitaMensal(dados) {
  const positivos = (dados || []).filter((item) => Number(item.valor || 0) > 0)
  const total = (dados || []).reduce((soma, item) => soma + Number(item.valor || 0), 0)
  const melhorDia = positivos.reduce((melhor, item) => {
    if (!melhor || Number(item.valor || 0) > Number(melhor.valor || 0)) return item
    return melhor
  }, null)
  const diasComReceita = positivos.length
  const mediaDiariaComReceita = diasComReceita > 0 ? total / diasComReceita : 0
  const mediaPorDiaDoMes = (dados || []).length > 0 ? total / (dados || []).length : 0
  return {
    total,
    diasComReceita,
    mediaDiariaComReceita,
    mediaPorDiaDoMes,
    melhorDia,
  }
}

function suavizarPontos(pontos, largura, altura) {
  if (pontos.length < 2) return ''
  const curvas = [`M ${pontos[0].x} ${pontos[0].y}`]
  for (let i = 0; i < pontos.length - 1; i++) {
    const atual = pontos[i]
    const proximo = pontos[i + 1]
    const pontoMeio = (atual.x + proximo.x) / 2
    curvas.push(`C ${pontoMeio} ${atual.y}, ${pontoMeio} ${proximo.y}, ${proximo.x} ${proximo.y}`)
  }
  return curvas.join(' ')
}

function GraficoArea({ dados }) {
  const [tooltip, setTooltip] = useState(null)
  const temDados = dados.some((d) => d.valor > 0)
  const width = 760
  const height = 240
  const pLeft = 24
  const pRight = 18
  const pTop = 16
  const pBottom = 28
  const chartW = width - pLeft - pRight
  const chartH = height - pTop - pBottom
  const maxValor = Math.max(...dados.map((d) => d.valor), 1)
  const gridFracs = [0, 0.25, 0.5, 0.75, 1]

  const pontos = dados.map((d, index) => {
    const x = pLeft + (chartW * (dados.length > 1 ? index / (dados.length - 1) : 0))
    const y = pTop + chartH - ((d.valor || 0) / maxValor) * chartH
    return { ...d, x, y }
  })

  const linha = suavizarPontos(pontos, width, height)
  const areaBase = `M ${pontos[0]?.x || pLeft} ${pTop + chartH} ${linha} L ${pontos[pontos.length - 1]?.x || pLeft} ${pTop + chartH} Z`
  const linhaSecundaria = suavizarPontos(
    pontos.map((p, index) => ({
      ...p,
      y: p.y + Math.min(26, 10 + (index % 4) * 4),
    })),
    width,
    height,
  )
  const areaSecundaria = `M ${pontos[0]?.x || pLeft} ${pTop + chartH} ${linhaSecundaria} L ${pontos[pontos.length - 1]?.x || pLeft} ${pTop + chartH} Z`
  const grade = { dark: 'rgba(255,255,255,0.08)', light: 'rgba(17,24,39,0.10)' }

  if (!temDados) {
    return (
      <div className="bar-chart-empty">
        <BarChart2 size={40} color="var(--primary)" />
        <p>Nenhuma receita registrada neste mes.</p>
        <small>Os valores aparecerao aqui conforme os pagamentos confirmados entrarem no periodo.</small>
      </div>
    )
  }

  return (
    <div className="area-chart-shell">
      <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Grafico de receita por dia" style={{ width: '100%', height: '100%', overflow: 'visible' }}>
        {gridFracs.map((frac) => {
          const y = pTop + chartH * (1 - frac)
          const val = maxValor * frac
          return (
            <g key={frac}>
              <line x1={pLeft} y1={y} x2={width - pRight} y2={y} stroke="var(--dashboard-chart-grid)" strokeWidth={1} />
              <text x={pLeft - 6} y={y + 4} textAnchor="end" fontSize={10} fill="var(--dashboard-chart-text)">
                {val === 0 ? 'R$ 0' : `R$ ${(val / 1000).toFixed(val >= 1000 ? 1 : 0)}${val >= 1000 ? 'k' : ''}`}
              </text>
            </g>
          )
        })}

        <defs>
          <linearGradient id="areaPrimary" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.78" />
            <stop offset="100%" stopColor="var(--primary)" stopOpacity="0.18" />
          </linearGradient>
          <linearGradient id="areaSecondary" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.48" />
            <stop offset="100%" stopColor="var(--primary)" stopOpacity="0.08" />
          </linearGradient>
        </defs>

        <path d={areaSecundaria} fill="url(#areaSecondary)" opacity={0.7} />
        <path d={areaBase} fill="url(#areaPrimary)" opacity={0.92} />
        <path d={linha} fill="none" stroke="var(--primary)" strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" />

        {pontos.map((p, index) => {
          const hasValue = p.valor > 0
          const isHovered = tooltip?.index === index
          return (
            <g key={p.iso}>
              <circle
                cx={p.x}
                cy={p.y}
                r={isHovered ? 5.5 : 4}
                fill={isHovered ? 'var(--primary)' : 'rgba(255,255,255,0.92)'}
                stroke="var(--primary)"
                strokeWidth={1.8}
                opacity={hasValue ? 1 : 0.45}
                onMouseEnter={() => {
                  if (!hasValue) return
                  setTooltip({ index, x: p.x, y: p.y, valor: p.valor, label: p.label })
                }}
                onMouseLeave={() => setTooltip(null)}
                style={{ cursor: hasValue ? 'pointer' : 'default' }}
              />
              {index % 5 === 0 && (
                <text x={p.x} y={height - 8} textAnchor="middle" fontSize={10} fill="var(--dashboard-chart-text)">
                  {p.label}
                </text>
              )}
            </g>
          )
        })}

        {tooltip && (() => {
          const tx = Math.min(Math.max(tooltip.x, pLeft + 48), width - pRight - 48)
          const ty = Math.max(tooltip.y - 54, pTop)
          return (
            <g style={{ pointerEvents: 'none' }}>
              <rect x={tx - 52} y={ty} width={104} height={38} rx={8} fill="var(--dashboard-chart-tooltip-bg)" opacity={0.96} />
              <text x={tx} y={ty + 14} textAnchor="middle" fontSize={10} fill="var(--dashboard-chart-tooltip-muted)">{tooltip.label}</text>
              <text x={tx} y={ty + 29} textAnchor="middle" fontSize={12} fill="var(--dashboard-chart-tooltip-text)" fontWeight={700}>
                {currency(tooltip.valor)}
              </text>
            </g>
          )
        })()}
      </svg>
    </div>
  )
}

export default function Dashboard() {
  const [data, , { loading, reload }] = useLocalData('dashboard')
  const { refreshTrigger } = useContext(RefreshContext)
  const { usuario } = useAuth()
  const [passosAberto, setPassosAberto] = useState(true)
  const [recarregando, setRecarregando] = useState(false)

  useEffect(() => {
    reload(true)
  }, [refreshTrigger, reload])

  async function recarregarDashboard() {
    if (recarregando) return
    setRecarregando(true)
    try {
      await reload(true)
    } finally {
      setRecarregando(false)
    }
  }

  const resumoDashboard = data.dashboardResumo || null
  console.log('[dashboard-debug] dados recebidos', data)
  const primeirosPassos = resumoDashboard?.primeirosPassos || null
  const clientesAtivos = Array.isArray(data.clientes) ? data.clientes.filter((cliente) => !cliente.excluido) : []
  const agendamentosVisiveis = Array.isArray(data.agendamentos) ? data.agendamentos : []
  const conversasVisiveis = Array.isArray(data.conversas) ? data.conversas : []
  const servicosVisiveis = Array.isArray(data.servicos) ? data.servicos : []
  const allowed = PLANOS[usuario?.plano]?.rotas || []
  const canFinanceiro = allowed.includes('financeiro')
  const hoje = todayIso()
  const hojeDate = new Date(`${hoje}T12:00:00`)
  const dataExtenso = hojeDate.toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' })
  const mesAtual = hojeDate.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })

  const agendamentosHojeBase = agendamentosVisiveis.filter((a) => a.data === hoje).length
  const conversasAbertasBase = conversasVisiveis.filter((c) => c.status === 'ABERTA').length
  const totalClientesBase = clientesAtivos.length
  const servicosAtivosBase = servicosVisiveis.filter((s) => s.status === 'ATIVO').length
  const receitaConfirmadaBase = data.pagamentos
    .filter((p) => ['PAGO', 'PAGA', 'CONFIRMADO', 'CONFIRMADA', 'APROVADO', 'APPROVED', 'PAID', 'PAYMENT_APPROVED', 'PURCHASE_APPROVED'].includes(String(p.status || '').toUpperCase()))
    .reduce((sum, p) => sum + Number(p.valor || 0), 0)
  const pagamentosPendentesBase = data.pagamentos.filter((p) => String(p.status || '').toUpperCase() === 'PENDENTE')
  const pendenteCobrancaBase = pagamentosPendentesBase.reduce((sum, p) => sum + Number(p.valor || 0), 0)

  const agendamentosHoje = resumoDashboard?.agendamentosHoje > 0 ? resumoDashboard.agendamentosHoje : agendamentosHojeBase
  const conversasAbertas = resumoDashboard?.conversasAbertas > 0 ? resumoDashboard.conversasAbertas : conversasAbertasBase
  const totalClientes = resumoDashboard?.clientesCadastrados > 0 ? resumoDashboard.clientesCadastrados : totalClientesBase
  const servicosAtivos = resumoDashboard?.servicosAtivos > 0 ? resumoDashboard.servicosAtivos : servicosAtivosBase

  const receitaTotal = resumoDashboard?.receitaConfirmada > 0 ? resumoDashboard.receitaConfirmada : receitaConfirmadaBase
  const totalPendente = resumoDashboard?.pendenteCobranca > 0 ? resumoDashboard.pendenteCobranca : pendenteCobrancaBase
  const servicosPorId = new Map((data.servicos || []).map((servico) => [servico.id, servico]))
  const servicoCountFallback = {}
  ;(data.agendamentos || []).forEach((a) => {
    if (a.status === 'CANCELADO') return
    servicoCountFallback[a.servicoNome] = (servicoCountFallback[a.servicoNome] || 0) + 1
  })
  const receitaServicoFallback = {}
  ;(data.agendamentos || []).forEach((a) => {
    if (a.status === 'CANCELADO') return
    const servico = servicosPorId.get(a.servicoId)
    if (!servico) return
    receitaServicoFallback[a.servicoNome] = (receitaServicoFallback[a.servicoNome] || 0) + Number(servico.valor || 0)
  })

  const receitaDiasResumo = resumoDashboard?.receitaPorDia?.length
    ? normalizarReceitaDias(resumoDashboard.receitaPorDia)
    : []
  const receitaDiasBase = buildReceitaMes(data.pagamentos)
  const receitaDias = combinarReceitaMensal(receitaDiasResumo, receitaDiasBase)
  const receitaResumo = resumirReceitaMensal(receitaDias)
  const melhorDiaReceita = receitaResumo.melhorDia
    ? new Date(`${receitaResumo.melhorDia.iso}T12:00:00`).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
    : null

  const proximosAtendimentos = resumoDashboard?.proximosAgendamentos?.length
    ? resumoDashboard.proximosAgendamentos
    : data.agendamentos
        .filter((a) => a.data >= hoje && (a.status === 'CONFIRMADO' || a.status === 'PENDENTE'))
        .sort((a, b) => (a.data + a.horaInicio).localeCompare(b.data + b.horaInicio))
        .slice(0, 3)

  const servicosTop = resumoDashboard?.servicosMaisAgendados?.length
    ? resumoDashboard.servicosMaisAgendados.map((item) => [item.nome, item.quantidade])
    : Object.entries(servicoCountFallback).sort((a, b) => b[1] - a[1]).slice(0, 5)
  const profissionalCountFallback = {}
  ;(data.agendamentos || []).forEach((a) => {
    if (a.status === 'CANCELADO') return
    const nomeProfissional = a.profissionalNome || 'Profissional'
    profissionalCountFallback[nomeProfissional] = (profissionalCountFallback[nomeProfissional] || 0) + 1
  })
  const profissionaisTop = resumoDashboard?.profissionaisMaisAgendados?.length
    ? resumoDashboard.profissionaisMaisAgendados.map((item) => [item.nome, item.quantidade])
    : Object.entries(profissionalCountFallback).sort((a, b) => b[1] - a[1]).slice(0, 5)
  const receitaServicoTop = resumoDashboard?.servicosMaisAgendados?.length
    ? resumoDashboard.servicosMaisAgendados.map((item) => [item.nome, Number(item.valor || 0)]).filter(([, valor]) => valor > 0)
    : Object.entries(receitaServicoFallback).sort((a, b) => b[1] - a[1]).slice(0, 4)
  const receitaServicoMax = receitaServicoTop[0]?.[1] || 1
  const clientesEmRiscoFallback = (data.clientes || [])
    .filter((cliente) => {
      const score = Number(cliente.scoreRisco)
      const diasSemAgendar = Number(cliente.diasSemAgendar)
      return score >= 75 || diasSemAgendar >= 60
    })
    .sort((a, b) => Number(b.scoreRisco || 0) - Number(a.scoreRisco || 0) || Number(b.diasSemAgendar || 0) - Number(a.diasSemAgendar || 0))
    .slice(0, 2)
  const clientesEmRiscoTop = resumoDashboard?.clientesEmRisco?.length
    ? resumoDashboard.clientesEmRisco.slice(0, 2)
    : clientesEmRiscoFallback
  const pagamentosPendentesTop = [...(resumoDashboard?.pagamentosPendentes || pagamentosPendentesBase)]
    .filter((p) => String(p.status || '').toUpperCase() === 'PENDENTE')
    .sort((a, b) => {
      const dataA = String(a.dataPagamento || a.data || '')
      const dataB = String(b.dataPagamento || b.data || '')
      return dataB.localeCompare(dataA)
    })
    .slice(0, 2)
  const ultimosAtendimentos = [...data.agendamentos]
    .sort((a, b) => {
      const dataA = `${a.data || ''} ${a.horaInicio || ''}`
      const dataB = `${b.data || ''} ${b.horaInicio || ''}`
      return dataB.localeCompare(dataA)
    })
    .slice(0, 3)

  const metrics = [
    { key: 'agenda', icon: CalendarDays, label: 'Agendamentos hoje', value: agendamentosHoje, detail: agendamentosHoje === 0 ? 'nenhum hoje' : 'na agenda de hoje' },
    {
      key: 'pendencias',
      icon: CreditCard,
      label: 'Pendente de pagamento',
      value: pagamentosPendentesBase.length,
      detail: pendenteCobrancaBase === 0 ? 'nenhum pendente' : currency(pendenteCobrancaBase),
    },
  ]

  const nomeUsuario = usuario?.nome || 'Usuario'
  const nomeEmpresa = usuario?.empresaNome || data.empresa?.nomeFantasia || resumoDashboard?.empresaNome || 'sua empresa'
  if (canFinanceiro) {
    metrics.push(
      { key: 'financeiro', icon: CreditCard, label: 'Receita total do mes', value: receitaTotal === 0 ? 'R$ 0,00' : currency(receitaTotal), detail: receitaTotal === 0 ? 'nenhum valor no mes' : 'valor total do mes' },
      { key: 'pendentes', icon: CreditCard, label: 'Pendente de cobranca', value: totalPendente === 0 ? 0 : totalPendente, detail: totalPendente === 0 ? 'nenhum pendente' : currency(totalPendente) },
    )
  }

  return (
    <section className="page dashboard-page">
      {loading ? (
        <div className="space-y-4">
          <div className="h-28 animate-pulse rounded bg-gray-700" />
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <div className="h-24 animate-pulse rounded bg-gray-700" />
            <div className="h-24 animate-pulse rounded bg-gray-700" />
            <div className="h-24 animate-pulse rounded bg-gray-700" />
            <div className="h-24 animate-pulse rounded bg-gray-700" />
          </div>
        </div>
      ) : (
        <>
          <ScrollReveal className="dashboard-hero panel-glow">
            <div>
              <span className="section-kicker">{dataExtenso}</span>
              <h1>Ola, {nomeUsuario}. A {nomeEmpresa} esta tudo sob controle.</h1>
              <p>Resumo do seu atendimento - apenas dados reais da sua conta.</p>
            </div>
            <Button variant="secondary" icon={RefreshCw} onClick={recarregarDashboard} disabled={recarregando}>
              {recarregando ? 'Recarregando...' : 'Recarregar'}
            </Button>
          </ScrollReveal>

          {primeirosPassos && primeirosPassos.etapas?.some((etapa) => !etapa.concluido) && (
            <ScrollReveal className="panel first-steps-card" delay={40}>
              <div className="panel-head">
                <div>
                  <span className="section-kicker">Configuracao inicial</span>
                  <h2>Primeiros Passos</h2>
                  <p>{primeirosPassos.concluidos} de {primeirosPassos.total} concluidos</p>
                </div>
                <button className="first-steps-toggle" type="button" onClick={() => setPassosAberto((aberto) => !aberto)}>
                  {passosAberto ? 'Recolher' : 'Expandir'}
                </button>
              </div>
              {passosAberto && (
                <div className="first-steps-grid">
                  {primeirosPassos.etapas.map((etapa) => {
                    const Icon = etapa.concluido ? CheckCircle : Circle
                    return (
                      <Link key={etapa.chave} to={etapa.rota} className={etapa.concluido ? 'first-step done' : 'first-step'}>
                        <Icon size={20} />
                        <div>
                          <strong>{etapa.titulo}</strong>
                          <small>{etapa.subtitulo}</small>
                        </div>
                      </Link>
                    )
                  })}
                </div>
              )}
            </ScrollReveal>
          )}

          <div className="operation-metrics">
            {metrics.map(({ key, icon: Icon, label, value, detail }, index) => (
              <ScrollReveal key={key} delay={index * 60}>
                <article className={`dashboard-summary-card ${key === 'agenda' ? 'dashboard-summary-card--agenda' : ''} ${key === 'pendencias' ? 'dashboard-summary-card--pendencias' : ''} ${key === 'financeiro' ? 'dashboard-summary-card--financeiro' : ''}`}>
                  <Icon size={24} />
                  <div>
                    <span>{label}</span>
                    <strong>{value}</strong>
                    <small>{detail}</small>
                  </div>
                </article>
              </ScrollReveal>
            ))}
          </div>

          {canFinanceiro && (
            <ScrollReveal className="panel receita-chart-panel" delay={0}>
              <div className="panel-head">
                <div>
                  <span className="section-kicker">Financeiro</span>
                  <h2>Receita do mes</h2>
                  <p className="receita-chart-subtitle">Base confirmada com os pagamentos da sua empresa vinculada.</p>
                </div>
                <div className="receita-chart-periodo">
                  <div className="receita-chart-periodo-head">
                    <TrendingUp size={16} color="var(--primary)" />
                    <small>{mesAtual}</small>
                  </div>
                  <span>{receitaResumo.diasComReceita} dia{receitaResumo.diasComReceita !== 1 ? 's' : ''} com receita registrada</span>
                </div>
              </div>
              <GraficoArea dados={receitaDias} />
              <div className="receita-chart-stats">
                <article>
                  <span>Total do mês</span>
                  <strong>{currency(receitaResumo.total)}</strong>
                  <small>{receitaResumo.total === 0 ? 'Nenhuma receita confirmada neste mês.' : 'Somente pagamentos confirmados.'}</small>
                </article>
                <article>
                  <span>Média por dia</span>
                  <strong>{currency(receitaResumo.mediaDiariaComReceita)}</strong>
                  <small>{receitaResumo.diasComReceita > 0 ? `${receitaResumo.diasComReceita} dia${receitaResumo.diasComReceita !== 1 ? 's' : ''} com movimento` : 'Sem dias com receita confirmada.'}</small>
                </article>
                <article>
                  <span>Melhor dia</span>
                  <strong>{melhorDiaReceita || '--/--'}</strong>
                  <small>{receitaResumo.melhorDia ? currency(receitaResumo.melhorDia.valor) : 'Sem pico identificado.'}</small>
                </article>
                <article>
                  <span>Média do mês</span>
                  <strong>{currency(receitaResumo.mediaPorDiaDoMes)}</strong>
                  <small>Distribuída pelos dias do mês atual.</small>
                </article>
              </div>
            </ScrollReveal>
          )}

          <div className="dashboard-grid">
            <ScrollReveal className="panel" delay={0}>
              <div className="panel-head">
                <div>
                  <span className="section-kicker">Agenda</span>
                  <h2>Proximos atendimentos</h2>
                </div>
                <CalendarDays size={20} color="var(--primary)" />
              </div>
              {proximosAtendimentos.length === 0 ? (
                <div className="dash-empty-state">
                  <CalendarDays size={32} color="var(--primary)" />
                  <p>Nenhum atendimento agendado.</p>
                </div>
              ) : (
                proximosAtendimentos.map((item) => (
                  <div className="delivery-mini" key={item.id}>
                    <div className="dash-mini-hora">
                      <strong>{item.horaInicio}</strong>
                      <small>{item.data !== hoje ? item.data.slice(8) + '/' + item.data.slice(5, 7) : 'hoje'}</small>
                    </div>
                    <div>
                      <span>{item.clienteNome}</span>
                      <small>{item.servicoNome}</small>
                    </div>
                    <StatusBadge status={item.status} />
                  </div>
                ))
              )}
            </ScrollReveal>

            <div className="dashboard-side-cards">
              <ScrollReveal className="panel" delay={60}>
                <div className="panel-head">
                  <div>
                    <span className="section-kicker">Profissionais</span>
                    <h2>Mais escolhido</h2>
                  </div>
                </div>
                {profissionaisTop.length === 0 ? (
                  <div className="dash-empty-state">
                    <Wrench size={28} color="var(--primary)" />
                    <p>Nenhum profissional agendado ainda.</p>
                  </div>
                ) : (
                  <div className="ranking">
                    {profissionaisTop.map(([nome, qtd]) => (
                      <div key={nome} className="dashboard-ranking-item dashboard-service-item">
                        <strong>{nome}</strong>
                        <small>{qtd} agendamento{qtd !== 1 ? 's' : ''}</small>
                      </div>
                    ))}
                  </div>
                )}
              </ScrollReveal>

              {canFinanceiro && (
                <ScrollReveal className="panel" delay={90}>
                  <div className="panel-head">
                    <div>
                      <span className="section-kicker">Desempenho</span>
                      <h2>Clientes em risco</h2>
                    </div>
                  </div>
                  {clientesEmRiscoTop.length === 0 ? (
                    <div className="dash-empty-state">
                      <BarChart2 size={28} color="var(--primary)" />
                      <p>Não existe clientes em risco.</p>
                    </div>
                  ) : (
                    <div className="ranking">
                      {clientesEmRiscoTop.map((cliente) => {
                        const score = Number(cliente.scoreRisco || 0)
                        const dias = Number(cliente.diasSemAgendar || 0)
                        return (
                          <div key={cliente.id} className="dashboard-ranking-item dashboard-service-item">
                            <div>
                              <strong>{cliente.nome}</strong>
                              <small>{dias} dias sem agendar</small>
                            </div>
                            <strong style={{ color: 'var(--warning)', whiteSpace: 'nowrap' }}>{score}% risco</strong>
                          </div>
                        )
                      })}
                    </div>
                  )}
                  <Link className="inline-link" to="/sistema/crm?segment=at_risk">
                    Ver todos
                  </Link>
                </ScrollReveal>
              )}

              {canFinanceiro && (
                <ScrollReveal className="panel" delay={120}>
                  <div className="panel-head">
                    <div>
                      <span className="section-kicker">Pagamentos</span>
                      <h2>Pendentes</h2>
                    </div>
                    <CreditCard size={18} color="var(--primary)" />
                  </div>
                  {pagamentosPendentesTop.length ? (
                    <div className="ranking">
                      {pagamentosPendentesTop.map((p) => (
                        <div key={p.id} className="dashboard-ranking-item dashboard-payment-item">
                          <div className="dashboard-payment-copy">
                            <strong>{p.clienteNome || 'Cliente'}</strong>
                            <small>{p.metodoPagamento || 'Aguardando pagamento'}</small>
                          </div>
                          <strong style={{ color: 'var(--warning)', whiteSpace: 'nowrap' }}>{currency(p.valor)}</strong>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="dash-empty-state">
                      <CreditCard size={28} color="var(--primary)" />
                      <p>Nenhum pendente. ðŸŽ‰</p>
                    </div>
                  )}
                  <Link className="inline-link" to="/sistema/financeiro">
                    Ver todos
                  </Link>
                </ScrollReveal>
              )}
            </div>
          </div>

          <ScrollReveal className="panel" delay={80}>
            <div className="panel-head">
              <div>
                <span className="section-kicker">Agenda</span>
                <h2>Ultimos atendimentos</h2>
              </div>
              <Link className="inline-link" to="/sistema/agenda">Ver todos</Link>
            </div>
            {data.agendamentos.length === 0 ? (
              <div className="dash-empty-state" style={{ padding: '32px 0' }}>
                <CalendarDays size={36} color="var(--primary)" />
                <p>Nenhum atendimento registrado ainda.</p>
                <small>Crie agendamentos na secao de Agenda.</small>
              </div>
            ) : (
              <Table
                columns={[
                  { key: 'id', label: 'ATENDIMENTO', render: (row) => `#${String(row.id).padStart(4, '0')}` },
                  { key: 'clienteNome', label: 'CLIENTE' },
                  { key: 'status', label: 'STATUS', render: (row) => <StatusBadge status={row.status} /> },
                  { key: 'servicoNome', label: 'SERVICO' },
                  { key: 'profissionalNome', label: 'PROFISSIONAL' },
                  { key: 'horaInicio', label: 'HORARIO' },
                ]}
                rows={ultimosAtendimentos}
              />
            )}
          </ScrollReveal>
        </>
      )}
    </section>
  )
}





