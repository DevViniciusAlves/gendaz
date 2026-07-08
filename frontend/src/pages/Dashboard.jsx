import { useState } from 'react'
import { BarChart2, CalendarDays, CheckCircle, Circle, CreditCard, MessageCircle, RefreshCw, TrendingUp, UserPlus, Wrench } from 'lucide-react'
import { Link } from 'react-router-dom'
import Button from '../components/Button.jsx'
import ScrollReveal from '../components/ScrollReveal.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import Table from '../components/Table.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'
import { useLocalData } from '../hooks/useLocalData.js'
import { currency, PLANOS, todayIso } from '../services/localStore.js'

function buildReceitaDias(pagamentos, dias = 30) {
  const hoje = new Date(`${todayIso()}T12:00:00`)
  const mapaReceita = {}

  pagamentos.forEach((p) => {
    const status = String(p.status || '').toUpperCase()
    const confirmado = ['PAGO', 'PAGA', 'CONFIRMADO', 'CONFIRMADA', 'APROVADO', 'APPROVED', 'PAID', 'PAYMENT_APPROVED', 'PURCHASE_APPROVED'].includes(status)
    if (!confirmado || !p.dataPagamento) return
    const dia = String(p.dataPagamento).slice(0, 10)
    mapaReceita[dia] = (mapaReceita[dia] || 0) + Number(p.valor || 0)
  })

  const resultado = []
  for (let i = dias - 1; i >= 0; i--) {
    const data = new Date(hoje)
    data.setDate(data.getDate() - i)
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

function GraficoBarras({ dados }) {
  const [tooltip, setTooltip] = useState(null)
  const maxValor = Math.max(...dados.map((d) => d.valor), 1)
  const temDados = dados.some((d) => d.valor > 0)
  const width = 760
  const height = 240
  const pLeft = 64
  const pRight = 16
  const pTop = 16
  const pBottom = 36
  const chartW = width - pLeft - pRight
  const chartH = height - pTop - pBottom
  const barCount = dados.length
  const barGap = 3
  const barW = Math.max(4, (chartW - barGap * (barCount - 1)) / barCount)
  const gridFracs = [0, 0.25, 0.5, 0.75, 1]

  if (!temDados) {
    return (
      <div className="bar-chart-empty">
        <BarChart2 size={40} color="var(--primary)" />
        <p>Nenhuma receita registrada nos ultimos 30 dias.</p>
        <small>Os valores aparecerao aqui conforme os pagamentos forem confirmados.</small>
      </div>
    )
  }

  return (
    <div className="bar-chart-shell">
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

        {dados.map((d, i) => {
          const barH = d.valor > 0 ? Math.max(3, (d.valor / maxValor) * chartH) : 2
          const x = pLeft + i * (barW + barGap)
          const y = pTop + chartH - barH
          const isHovered = tooltip?.index === i
          const hasValue = d.valor > 0

          return (
            <g key={d.iso}>
              <rect
                x={x}
                y={y}
                width={barW}
                height={barH}
                rx={3}
                style={{ fill: isHovered ? 'var(--primary)' : hasValue ? 'var(--primary)' : 'var(--dashboard-chart-empty-bar)', cursor: hasValue ? 'pointer' : 'default', transition: 'fill 0.14s, opacity 0.14s', opacity: isHovered ? 1 : hasValue ? 0.85 : 0.6 }}
                onMouseEnter={() => {
                  if (!hasValue) return
                  setTooltip({ index: i, x: x + barW / 2, y, valor: d.valor, label: d.label })
                }}
                onMouseLeave={() => setTooltip(null)}
              />
              {i % 5 === 0 && (
                <text x={x + barW / 2} y={height - 6} textAnchor="middle" fontSize={10} fill="var(--dashboard-chart-text)">
                  {d.label}
                </text>
              )}
            </g>
          )
        })}

        {tooltip && (() => {
          const tx = Math.min(Math.max(tooltip.x, pLeft + 44), width - pRight - 44)
          const ty = Math.max(tooltip.y - 50, pTop)
          return (
            <g style={{ pointerEvents: 'none' }}>
              <rect x={tx - 46} y={ty} width={92} height={36} rx={7} fill="var(--dashboard-chart-tooltip-bg)" opacity={0.96} />
              <text x={tx} y={ty + 13} textAnchor="middle" fontSize={10} fill="var(--dashboard-chart-tooltip-muted)">{tooltip.label}</text>
              <text x={tx} y={ty + 28} textAnchor="middle" fontSize={12} fill="var(--dashboard-chart-tooltip-text)" fontWeight={700}>
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
  const { usuario } = useAuth()
  const [passosAberto, setPassosAberto] = useState(true)
  const [recarregando, setRecarregando] = useState(false)

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
  const allowed = PLANOS[usuario?.plano]?.rotas || []
  const canFinanceiro = allowed.includes('financeiro')
  const hoje = todayIso()
  const hojeDate = new Date(`${hoje}T12:00:00`)
  const dataExtenso = hojeDate.toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' })

  const agendamentosHoje = resumoDashboard?.agendamentosHoje ?? data.agendamentos.filter((a) => a.data === hoje).length
  const conversasAbertas = resumoDashboard?.conversasAbertas ?? data.conversas.filter((c) => c.status === 'ABERTA').length
  const totalClientes = resumoDashboard?.clientesCadastrados ?? data.clientes.filter((cliente) => !cliente.excluido).length
  const servicosAtivos = resumoDashboard?.servicosAtivos ?? data.servicos.filter((s) => s.status === 'ATIVO').length

  const receitaTotal = resumoDashboard?.receitaConfirmada ?? data.pagamentos
    .filter((p) => ['PAGO', 'PAGA', 'CONFIRMADO', 'CONFIRMADA', 'APROVADO', 'APPROVED', 'PAID', 'PAYMENT_APPROVED', 'PURCHASE_APPROVED'].includes(String(p.status || '').toUpperCase()))
    .reduce((sum, p) => sum + Number(p.valor || 0), 0)
  const totalPendente = resumoDashboard?.pendenteCobranca ?? data.pagamentos
    .filter((p) => p.status === 'PENDENTE')
    .reduce((sum, p) => sum + Number(p.valor || 0), 0)
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

  const receitaDias = resumoDashboard?.receitaPorDia?.length
    ? normalizarReceitaDias(resumoDashboard.receitaPorDia)
    : buildReceitaDias(data.pagamentos, 30)

  const proximosAtendimentos = resumoDashboard?.proximosAgendamentos?.length
    ? resumoDashboard.proximosAgendamentos
    : data.agendamentos
        .filter((a) => a.data >= hoje && (a.status === 'CONFIRMADO' || a.status === 'PENDENTE'))
        .sort((a, b) => (a.data + a.horaInicio).localeCompare(b.data + b.horaInicio))
        .slice(0, 5)

  const servicosTop = resumoDashboard?.servicosMaisAgendados?.length
    ? resumoDashboard.servicosMaisAgendados.map((item) => [item.nome, item.quantidade])
    : Object.entries(servicoCountFallback).sort((a, b) => b[1] - a[1]).slice(0, 5)
  const receitaServicoTop = resumoDashboard?.servicosMaisAgendados?.length
    ? resumoDashboard.servicosMaisAgendados.map((item) => [item.nome, Number(item.valor || 0)]).filter(([, valor]) => valor > 0)
    : Object.entries(receitaServicoFallback).sort((a, b) => b[1] - a[1]).slice(0, 4)
  const receitaServicoMax = receitaServicoTop[0]?.[1] || 1

  const metrics = [
    { key: 'agenda', icon: CalendarDays, label: 'Agendamentos hoje', value: agendamentosHoje, detail: agendamentosHoje === 0 ? 'nenhum hoje' : 'na agenda de hoje' },
    { key: 'whatsapp', icon: MessageCircle, label: 'Conversas abertas', value: conversasAbertas, detail: conversasAbertas === 0 ? 'tudo resolvido' : 'em andamento' },
    { key: 'clientes', icon: UserPlus, label: 'Clientes cadastrados', value: totalClientes, detail: totalClientes === 0 ? 'nenhum cadastrado' : 'base ativa' },
    { key: 'servicos', icon: Wrench, label: 'Servicos ativos', value: servicosAtivos, detail: servicosAtivos === 0 ? 'nenhum cadastrado' : 'no catalogo' },
  ]

  const nomeUsuario = usuario?.nome || 'Usuario'
  const nomeEmpresa = usuario?.empresaNome || data.empresa?.nomeFantasia || resumoDashboard?.empresaNome || 'sua empresa'
  if (canFinanceiro) {
    metrics.push(
      { key: 'financeiro', icon: CreditCard, label: 'Receita confirmada', value: receitaTotal === 0 ? 'R$ 0,00' : currency(receitaTotal), detail: receitaTotal === 0 ? 'nenhum recebimento' : 'pagamentos confirmados' },
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
                <article className="dashboard-summary-card">
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
                  <h2>Receita por Dia</h2>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <TrendingUp size={16} color="var(--primary)" />
                  <small style={{ color: 'var(--muted)', fontSize: 13 }}>Mes atual</small>
                </div>
              </div>
              <GraficoBarras dados={receitaDias} />
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
                    <span className="section-kicker">Servicos</span>
                    <h2>Mais agendados</h2>
                  </div>
                </div>
                {servicosTop.length === 0 ? (
                  <div className="dash-empty-state">
                    <Wrench size={28} color="var(--primary)" />
                    <p>Nenhum agendamento ainda.</p>
                  </div>
                ) : (
                  <div className="ranking">
                    {servicosTop.map(([nome, qtd]) => (
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
                      <h2>Receita por servico</h2>
                    </div>
                  </div>
                  {receitaServicoTop.length === 0 ? (
                    <div className="dash-empty-state">
                      <BarChart2 size={28} color="var(--primary)" />
                      <p>Nenhuma receita registrada.</p>
                    </div>
                  ) : (
                    <div className="servico-desempenho">
                      {receitaServicoTop.map(([nome, valor]) => (
                        <div key={nome} className="servico-desempenho-item">
                          <div className="servico-desempenho-label">
                            <span>{nome}</span>
                            <strong>{currency(valor)}</strong>
                          </div>
                          <div className="servico-desempenho-bar">
                            <div
                              className="servico-desempenho-fill"
                              style={{ width: `${Math.round((valor / receitaServicoMax) * 100)}%` }}
                            />
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
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
                  {resumoDashboard?.pagamentosPendentes?.length ? (
                    <div className="ranking">
                      {resumoDashboard.pagamentosPendentes.slice(0, 4).map((p) => (
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
                      <p>Nenhum pendente. 🎉</p>
                    </div>
                  )}
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
                rows={data.agendamentos}
              />
            )}
          </ScrollReveal>
        </>
      )}
    </section>
  )
}
