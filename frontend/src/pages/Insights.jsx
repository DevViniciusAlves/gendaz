import { useMemo, useState } from 'react'
import {
  AlertCircle,
  Calendar,
  CheckCircle,
  Clock,
  HelpCircle,
  Megaphone,
  Send,
  Sparkles,
  Target,
  TrendingUp,
  Users,
  Wand2,
  Wrench,
  Zap,
} from 'lucide-react'
import Button from '../components/Button.jsx'
import { useInsights } from '../hooks/useInsights.js'
import InsightsChat from './insights/InsightsChat.jsx'
import './insights/styles.css'

function formatCurrency(valor) {
  const numero = Number(valor || 0)
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 }).format(numero)
}

function safeArray(value) {
  return Array.isArray(value) ? value : []
}

function badgeImpacto(item) {
  const texto = String(item?.urgencia || item?.impacto || '').toLowerCase()
  if (texto.includes('alta') || texto.includes('urg')) return 'Alto impacto'
  if (texto.includes('m') || texto.includes('médio') || texto.includes('medio')) return 'Médio impacto'
  return 'Baixo impacto'
}

function nomeCategoria(item, fallback) {
  return String(item?.tipo || fallback || '').toUpperCase()
}

function resumoTexto(dashboard) {
  const score = Number(dashboard?.scoreGeral ?? 0)
  const alertas = safeArray(dashboard?.alertas).length
  const oportunidades = safeArray(dashboard?.oportunidades).length
  const acoes = safeArray(dashboard?.acoes).length
  if (!dashboard) return 'Carregando consultoria inteligente...'
  return `Analisei sua empresa nos últimos 30 dias. Score ${score}/100, ${alertas} alertas, ${oportunidades} oportunidades e ${acoes} recomendações prioritárias.`
}

function insightPrincipal(item) {
  return {
    titulo: item?.titulo || item?.descricao || 'Insight',
    descricao: item?.descricao || item?.impacto || 'Sem detalhes adicionais.',
    impacto: item?.impacto || item?.urgencia || '-',
    tipo: item?.tipo || 'geral',
  }
}

export default function Insights() {
  const { dashboard, historico, loading, error, recarregar, analisar } = useInsights()
  const [chatAberto, setChatAberto] = useState(true)

  const score = Number(dashboard?.scoreGeral ?? 0)
  const alertas = safeArray(dashboard?.alertas)
  const oportunidades = safeArray(dashboard?.oportunidades)
  const recomendacoes = safeArray(dashboard?.acoes)
  const nomeEmpresa = dashboard?.empresaNome || 'Sua empresa'
  const impactoTotal = dashboard?.impactoTotal || 'Sem impacto calculado'
  const dataAnalise = dashboard?.geradoEm
    ? new Date(dashboard.geradoEm).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' })
    : null

  const principais = useMemo(() => {
    const principaisDaEmpresa = []

    if (alertas[0]) {
      principaisDaEmpresa.push({
        icon: AlertCircle,
        tag: nomeCategoria(alertas[0], 'Alertas'),
        titulo: alertas[0].titulo || 'Alerta principal',
        descricao: alertas[0].descricao || alertas[0].impacto || 'Sem descrição detalhada.',
        impacto: alertas[0].impacto || alertas[0].urgencia || 'Alto impacto',
      })
    }

    if (alertas[1]) {
      principaisDaEmpresa.push({
        icon: Calendar,
        tag: nomeCategoria(alertas[1], 'Agenda'),
        titulo: alertas[1].titulo || 'Agenda',
        descricao: alertas[1].descricao || alertas[1].impacto || 'Sem descrição detalhada.',
        impacto: alertas[1].impacto || alertas[1].urgencia || 'Médio impacto',
      })
    }

    if (oportunidades[0]) {
      principaisDaEmpresa.push({
        icon: TrendingUp,
        tag: nomeCategoria(oportunidades[0], 'Oportunidade'),
        titulo: oportunidades[0].titulo || 'Oportunidade',
        descricao: oportunidades[0].descricao || oportunidades[0].impacto || 'Sem descrição detalhada.',
        impacto: oportunidades[0].impacto || oportunidades[0].urgencia || 'Médio impacto',
      })
    }

    if (recomendacoes[0]) {
      principaisDaEmpresa.push({
        icon: Wrench,
        tag: nomeCategoria(recomendacoes[0], 'Ação'),
        titulo: recomendacoes[0].descricao || 'Ação recomendada',
        descricao: recomendacoes[0].impactoEstimado || recomendacoes[0].urgencia || 'Sem descrição detalhada.',
        impacto: recomendacoes[0].urgencia || 'Alta prioridade',
      })
    }

    while (principaisDaEmpresa.length < 4) {
      principaisDaEmpresa.push({
        icon: Users,
        tag: 'Empresa',
        titulo: 'Dados da empresa sincronizados',
        descricao: 'A análise está lendo os dados reais vinculados à sua conta.',
        impacto: 'Dados atualizados',
      })
    }

    return principaisDaEmpresa.slice(0, 4)
  }, [alertas, oportunidades, recomendacoes, nomeEmpresa])

  const simulacoes = [
    { pergunta: `E se eu ampliar horários na ${nomeEmpresa}?`, impacto: 'Impacto estimado com base na ocupação atual' },
    { pergunta: 'E se eu subir 10% o preço?', impacto: 'Simulação sobre o faturamento atual' },
    { pergunta: 'E se eu atuar nos clientes inativos?', impacto: 'Simulação sobre recorrência e reativação' },
  ]

  const perguntasDoDia = [
    'Você costuma recusar clientes por falta de horário?',
    'Seu objetivo é vender mais ou fidelizar?',
    'Qual serviço você quer divulgar esta semana?',
  ]

  return (
    <section className="page insights-page insights-page--new">
      <div className="page-title insights-hero">
        <div>
          <span className="section-kicker">Consultoria IA</span>
          <h1>Insights</h1>
          <p>A IA analisa os dados da sua empresa e recomenda ações para crescer.</p>
        </div>
        <Button variant="secondary" icon={Sparkles} onClick={() => recarregar(30)}>
          Recarregar
        </Button>
      </div>

      {loading && <div className="panel insights-panel">Carregando insights...</div>}
      {error && <div className="panel insights-panel insights-error">{String(error?.response?.data?.mensagem || error?.message || error)}</div>}

      {!loading && !error && (
        <div className="insights-layout">
          <div className="insights-main">
            <section className="panel insights-summary-panel">
              <div className="insights-summary-panel__icon">
                <Wand2 size={28} />
              </div>
              <div className="insights-summary-panel__content">
                <span className="insights-label">Resumo inteligente</span>
                <h2>{dashboard?.empresaNome ? `Olá, ${dashboard.empresaNome}.` : 'Resumo inteligente'}</h2>
                <p>{resumoTexto(dashboard)}</p>
                <small className="insights-summary-panel__meta">
                  {dataAnalise ? `Análise de ${dataAnalise}` : 'Análise sincronizada com a empresa vinculada'}
                </small>
                <Button variant="secondary" onClick={() => setChatAberto(true)}>
                  Ver análise completa
                </Button>
              </div>
              <div className="insights-health-card">
                <span className="insights-label">Saúde da empresa</span>
                <div className="insights-health-score">{score}/100</div>
                <strong>{score >= 70 ? 'Empresa saudável' : score >= 45 ? 'Atenção necessária' : 'Empresa em risco'}</strong>
                <small>{impactoTotal}</small>
              </div>
            </section>

            <section className="panel insights-section">
              <div className="section-kicker">Insights principais</div>
              <div className="insights-principais-grid">
                {principais.map((item) => {
                  const Icon = item.icon
                  return (
                    <article key={item.titulo} className="insights-mini-card">
                      <div className="insights-mini-card__head">
                        <Icon size={18} />
                        <span>{item.tag}</span>
                      </div>
                      <h3>{item.titulo}</h3>
                      <p>{item.descricao}</p>
                      <small>{badgeImpacto(item)}</small>
                    </article>
                  )
                })}
              </div>
            </section>

            <div className="insights-duo-grid">
              <section className="panel insights-section">
                <div className="section-kicker">Oportunidades para crescer</div>
                <div className="insights-list">
                  {oportunidades.slice(0, 3).map((item, index) => (
                    <article key={`${item.titulo || item.descricao || index}`} className="insights-list-item insights-list-item--oportunidade">
                      <div className="insights-list-item__icon">
                        <Zap size={18} />
                      </div>
                      <div className="insights-list-item__content">
                        <strong>{item.titulo || item.descricao || 'Oportunidade'}</strong>
                        <p>{item.descricao || item.impacto || 'Sem descrição'}</p>
                      </div>
                      <div className="insights-list-item__meta">
                        <small>Impacto estimado</small>
                        <strong>{item.impacto || '-'}</strong>
                      </div>
                    </article>
                  ))}
                  {oportunidades.length === 0 && <p className="insights-empty">Sem oportunidades no momento.</p>}
                </div>
                <div className="insights-link-row">
                  <span>Ver todos oportunidades</span>
                  <span>→</span>
                </div>
              </section>

              <section className="panel insights-section">
                <div className="section-kicker">Ações recomendadas pela IA</div>
                <div className="insights-action-list">
                  {recomendacoes.slice(0, 4).map((acao, index) => (
                    <div key={`${acao.descricao || index}`} className="insights-action-row">
                      <label className="insights-check">
                        <input type="checkbox" readOnly />
                        <span />
                      </label>
                      <div className="insights-action-row__content">
                        <strong>{acao.descricao}</strong>
                        <p>{acao.impactoEstimado || acao.urgencia || 'Ação sugerida'}</p>
                      </div>
                      <Button variant="secondary" className="insights-execute">Executar</Button>
                    </div>
                  ))}
                  {recomendacoes.length === 0 && <p className="insights-empty">Sem ações sugeridas.</p>}
                </div>
                <div className="insights-link-row">
                  <span>Ver plano completo</span>
                  <span>→</span>
                </div>
              </section>
            </div>

            <section className="panel insights-section">
              <div className="section-kicker">Histórico de recomendações</div>
              <div className="insights-history">
                {(historico || []).slice(0, 4).map((item, index) => (
                  <article key={item.id || `${index}`} className="insights-history-item">
                    <div className="insights-history-item__date">
                      <CheckCircle size={18} />
                      <span>{item.dataCriacao ? new Date(item.dataCriacao).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' }).toUpperCase() : 'RECENTE'}</span>
                    </div>
                    <strong>{item.pergunta || item.tipo || 'Recomendação'}</strong>
                    <p>{item.resposta || item.tipo || 'Concluído'}</p>
                  </article>
                ))}
                {historico.length === 0 && <p className="insights-empty">Sem histórico ainda.</p>}
              </div>
            </section>
          </div>

          <aside className="insights-sidebar">
            <section className={`panel insights-sidebar-card ${chatAberto ? 'is-open' : 'is-closed'}`}>
              <div className="insights-sidebar-head">
                <div>
                  <div className="section-kicker">IA Gendaz</div>
                  <h2>Chat IA</h2>
                </div>
                <button type="button" className="icon-btn" onClick={() => setChatAberto((value) => !value)} aria-label="Abrir ou fechar chat">
                  <HelpCircle size={18} />
                </button>
              </div>
              {chatAberto && (
                <>
                  <div className="insights-sidebar-layout">
                    <div className="insights-sidebar-chat">
                      <InsightsChat onEnviar={analisar} historico={historico} />
                    </div>

                    <div className="insights-sidebar-stack">
                      <div className="insights-sidebar-block">
                        <span className="insights-label">Sugestões rápidas</span>
                        <div className="insights-suggestions">
                          {[
                            'Como aumentar meu faturamento?',
                            'Quais clientes devo recuperar?',
                            'Qual serviço devo divulgar?',
                            'Como reduzir cancelamentos?',
                            'O que fazer esta semana?',
                          ].map((texto) => (
                            <button
                              key={texto}
                              type="button"
                              className="insights-suggestion"
                              onClick={() => {
                                window.dispatchEvent(new CustomEvent('agendapro:insights-suggestion', { detail: { pergunta: texto } }))
                              }}
                            >
                              <Target size={14} />
                              <span>{texto}</span>
                            </button>
                          ))}
                        </div>
                      </div>

                      <div className="insights-sidebar-block">
                        <span className="insights-label">Simulações</span>
                        <div className="insights-simulations">
                          {simulacoes.map((item) => (
                            <article key={item.pergunta} className="insights-simulation">
                              <div>
                                <strong>{item.pergunta}</strong>
                                <p>{item.impacto}</p>
                              </div>
                              <Clock size={14} />
                            </article>
                          ))}
                        </div>
                      </div>
                    </div>
                  </div>
                </>
              )}
            </section>
          </aside>
        </div>
      )}
    </section>
  )
}
