import { useContext, useEffect, useState, useMemo } from 'react'
import { RefreshContext } from '../context/RefreshContext.jsx'
import { BarChart2, CalendarDays, CheckCircle, Circle, CreditCard, RefreshCw, Wrench } from 'lucide-react'
import { Link } from 'react-router-dom'
import Button from '../components/Button.jsx'
import ScrollReveal from '../components/ScrollReveal.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import Table from '../components/Table.jsx'
import GraficoReceitaMes from '../components/gendaz/GraficoReceitaMes.jsx'
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

function formatoCompactoReceita(valor) {
  if (!valor || valor <= 0) return 'R$ 0'
  if (valor >= 1000) {
    const milhar = valor / 1000
    const texto = milhar >= 100
      ? Math.round(milhar)
      : (milhar % 1 === 0 ? milhar : milhar.toFixed(1).replace('.', ','))
    return `R$ ${texto}k`
  }
  return `R$ ${Math.round(valor)}`
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
  const primeirosPassos = resumoDashboard?.primeirosPassos || null
  const clientesAtivos = Array.isArray(data.clientes) ? data.clientes.filter((cliente) => !cliente.excluido) : []
  const agendamentosVisiveis = Array.isArray(data.agendamentos) ? data.agendamentos : []
  const conversasVisiveis = Array.isArray(data.conversas) ? data.conversas : []
  const servicosVisiveis = Array.isArray(data.servicos) ? data.servicos : []
  const isPlanoBasico = String(usuario?.plano || '').toUpperCase() === 'BASICO'
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
  const pagamentosVisiveis = Array.isArray(data.pagamentos) ? data.pagamentos : []
  const receitaConfirmadaBase = pagamentosVisiveis
    .filter((p) => ['PAGO', 'PAGA', 'CONFIRMADO', 'CONFIRMADA', 'APROVADO', 'APPROVED', 'PAID', 'PAYMENT_APPROVED', 'PURCHASE_APPROVED'].includes(String(p.status || '').toUpperCase()))
    .reduce((sum, p) => sum + Number(p.valor || 0), 0)
  const pagamentosPendentesBase = pagamentosVisiveis.filter((p) => String(p.status || '').toUpperCase() === 'PENDENTE')
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
  const receitaDiasBase = buildReceitaMes(pagamentosVisiveis)
  const receitaDias = combinarReceitaMensal(receitaDiasResumo, receitaDiasBase)
  const resumoReceitaMes = useMemo(
    () => resumirReceitaMensal(receitaDias),
    [receitaDias]
  )
  const proximosAtendimentos = resumoDashboard?.proximosAgendamentos?.length
    ? resumoDashboard.proximosAgendamentos
    : agendamentosVisiveis
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
  const ultimosAtendimentos = [...agendamentosVisiveis]
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
      { key: 'pendentes', icon: CreditCard, label: 'Pendente de cobranca', value: totalPendente === 0 ? 'R$ 0,00' : currency(totalPendente), detail: totalPendente === 0 ? 'nenhum pendente' : 'valor total pendente' },
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
            <Button variant="secondary" icon={RefreshCw} onClick={recarregarDashboard} loading={recarregando} loadingText="Recarregando...">
              Recarregar
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
            {metrics.map(({ key, icon: Icon, label, value, detail }) => (
              <ScrollReveal key={key} delay={0}>
                <article className={`dashboard-summary-card ${key === 'agenda' ? 'dashboard-summary-card--agenda' : ''} ${key === 'pendencias' ? 'dashboard-summary-card--pendencias' : ''} ${key === 'pendentes' ? 'dashboard-summary-card--pendentes' : ''} ${key === 'financeiro' ? 'dashboard-summary-card--financeiro' : ''}`}>
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
                <div className="panel-head receita-chart-head">
                  <div className="receita-chart-copy">
                    <span className="section-kicker">Financeiro</span>
                    <h2>Receita do mês</h2>
                    <strong className="receita-chart-total">{currency(resumoReceitaMes.total)}</strong>
                    <p className="receita-chart-subtitle">Base confirmada com os pagamentos da sua empresa vinculada.</p>
                  </div>
                  <div className="receita-chart-periodo">
                    <small>{mesAtual}</small>
                  </div>
                </div>
                <GraficoReceitaMes dados={receitaDias} formatarEixoY={formatoCompactoReceita} />
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
              <ScrollReveal className="panel" delay={0}>
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

              {canFinanceiro && !isPlanoBasico && (
                <ScrollReveal className="panel" delay={0}>
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
                <ScrollReveal className="panel" delay={0}>
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

          <ScrollReveal className="panel" delay={0}>
            <div className="panel-head">
              <div>
                <span className="section-kicker">Agenda</span>
                <h2>Ultimos atendimentos</h2>
              </div>
              <Link className="inline-link" to="/sistema/agenda">Ver todos</Link>
            </div>
            {agendamentosVisiveis.length === 0 ? (
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





