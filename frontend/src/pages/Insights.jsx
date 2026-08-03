import { useState } from 'react'
import {
  AlertCircle,
  Calendar,
  Clock,
  HelpCircle,
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

function safeArray(value) {
  return Array.isArray(value) ? value : []
}

function formatCurrency(valor) {
  const numero = Number(String(valor ?? '').replace(/[^\d,-]/g, '').replace(',', '.'))
  if (Number.isNaN(numero) || numero === 0) return String(valor ?? '-')
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 }).format(numero)
}

function badgeImpacto(item) {
  const texto = String(item?.urgencia || item?.impacto || '').toLowerCase()
  if (texto.includes('alta') || texto.includes('urg')) return 'Alto impacto'
  if (texto.includes('médio') || texto.includes('medio')) return 'Médio impacto'
  return 'Baixo impacto'
}

function iconPorTipo(tipo) {
  const valor = String(tipo || '').toLowerCase()
  if (valor.includes('cliente')) return Users
  if (valor.includes('finance')) return TrendingUp
  if (valor.includes('agenda') || valor.includes('ocios')) return Calendar
  if (valor.includes('acao') || valor.includes('açao') || valor.includes('ação')) return Wrench
  return AlertCircle
}

function resumoTexto(dashboard) {
  if (!dashboard) return 'Carregando análise real da empresa...'

  const score = Number(dashboard?.scoreGeral ?? 0)
  const alertas = safeArray(dashboard?.alertas).length
  const oportunidades = safeArray(dashboard?.oportunidades).length
  const acoes = safeArray(dashboard?.acoes).length

  return `Analisei sua empresa nos últimos 30 dias. Score ${score}/100, ${alertas} alertas, ${oportunidades} oportunidades e ${acoes} recomendações prioritárias.`
}

function getMetaMensal(dashboard) {
  const valor = dashboard?.metaMes || dashboard?.metaMensal || dashboard?.financeiro?.metaMes
  return valor ? formatCurrency(valor) : 'Sem meta'
}

function getReceitaMensal(dashboard) {
  const valor = dashboard?.financeiro?.totalRecebidoMes || dashboard?.receitaMensal || dashboard?.financeiro?.receitaMes
  return valor ? formatCurrency(valor) : 'Sem receita'
}

function getNomeMesAtual() {
  return new Intl.DateTimeFormat('pt-BR', { month: 'long' }).format(new Date())
}

function getTendencia(dashboard) {
  return dashboard?.tendencia || dashboard?.comparativo?.tendencia || null
}

function getVariacaoTendencia(dashboard) {
  return dashboard?.variacao || dashboard?.comparativo?.variacao || null
}

function getStatusScore(score) {
  if (score >= 70) return 'Muito saudável'
  if (score >= 45) return 'Saudável'
  return 'Em risco'
}

function getRisco(score) {
  if (score >= 70) return 'Baixo'
  if (score >= 45) return 'Médio'
  return 'Alto'
}

export default function Insights() {
  const { dashboard, historico, loading, error, recarregar, analisar } = useInsights()
  const [chatAberto, setChatAberto] = useState(true)
  const [analiseAberta, setAnaliseAberta] = useState(false)
  const [sincronizando, setSincronizando] = useState(false)
  const [metaAberta, setMetaAberta] = useState(false)
  const [metaSalva, setMetaSalva] = useState(() => {
    try {
      const raw = localStorage.getItem('agendapro_insights_meta')
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  })
  const [novaMeta, setNovaMeta] = useState({ titulo: '', quantidade: '', tempo: '' })

  const score = Number(dashboard?.scoreGeral ?? 0)
  const alertas = safeArray(dashboard?.alertas)
  const oportunidades = safeArray(dashboard?.oportunidades)
  const recomendacoes = safeArray(dashboard?.acoes)
  const principais = safeArray(dashboard?.principais).slice(0, 3)
  const impactoTotal = dashboard?.impactoTotal || 'Sem impacto calculado'
  const dataAnalise = dashboard?.geradoEm
    ? new Date(dashboard.geradoEm).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' })
    : null
  const tendencia = getTendencia(dashboard)
  const variacaoTendencia = getVariacaoTendencia(dashboard)
  const receitaMensal = getReceitaMensal(dashboard)
  const mesAtual = getNomeMesAtual()
  const riscoAtual = getRisco(score)
  const riscoAtivos = score >= 70 ? 8 : score >= 45 ? 5 : 2
  const riscoDescricao = score >= 70
    ? 'Acompanhe os pontos de atenção para manter sua empresa saudável.'
    : score >= 45
      ? 'Há sinais de atenção. Revise os principais pontos identificados.'
      : 'O cenário exige ação imediata para reduzir riscos.'
  const acaoPrincipal = dashboard?.acaoPrioritaria || recomendacoes[0] || null

  async function handleSincronizarDados() {
    if (sincronizando) return

    try {
      setSincronizando(true)
      await recarregar(30)
      window.dispatchEvent(new CustomEvent('agendapro:toast', {
        detail: {
          type: 'success',
          message: 'Dados sincronizados com sucesso.',
        },
      }))
    } catch (err) {
      console.error('[insights] erro ao sincronizar dados')
      window.dispatchEvent(new CustomEvent('agendapro:toast', {
        detail: {
          type: 'error',
          message: err?.response?.data?.mensagem || err?.message || 'Não foi possível sincronizar os dados.',
        },
      }))
    } finally {
      setSincronizando(false)
    }
  }

  function abrirMeta() {
    setNovaMeta({
      titulo: metaSalva?.titulo || '',
      quantidade: metaSalva?.quantidade || '',
      tempo: metaSalva?.tempo || '',
    })
    setMetaAberta(true)
  }

  function salvarMeta() {
    const meta = {
      titulo: String(novaMeta.titulo || '').trim(),
      quantidade: String(novaMeta.quantidade || '').trim(),
      tempo: String(novaMeta.tempo || '').trim(),
    }

    if (!meta.titulo || !meta.quantidade || !meta.tempo) return

    setMetaSalva(meta)
    localStorage.setItem('agendapro_insights_meta', JSON.stringify(meta))
    setMetaAberta(false)
  }

  return (
    <section className="page insights-page insights-page--new">
      <header className="page-title insights-hero">
        <div>
          <span className="section-kicker">Consultoria IA</span>
          <h1>Insights</h1>
          <p>A IA analisa os dados da sua empresa e recomenda ações para crescer.</p>
        </div>
        <Button variant="secondary" icon={Sparkles} onClick={handleSincronizarDados} disabled={sincronizando || loading}>
          {sincronizando ? 'Sincronizando...' : 'Sincronizar dados'}
        </Button>
      </header>

      {loading && <div className="panel insights-panel">Carregando análise real da empresa...</div>}
      {error && <div className="panel insights-panel insights-error">{String(error?.response?.data?.mensagem || error?.message || error)}</div>}

      {!loading && !error && (
        <div className="insights-layout">
          <div className="insights-main">
            <section className="panel insights-summary-panel">
              <div className="insights-summary-panel__content">
                <span className="insights-label">Resumo inteligente</span>
                <h2>{dashboard?.empresaNome ? `Olá, ${dashboard.empresaNome}.` : 'Resumo inteligente'}</h2>
                <p>{resumoTexto(dashboard)}</p>
                <small className="insights-summary-panel__meta">
                  {dataAnalise ? `Última sincronização: ${dataAnalise}` : 'Aguardando sincronização da empresa vinculada'}
                </small>
                <Button variant="secondary" onClick={() => setAnaliseAberta(true)}>
                  Ver análise completa
                </Button>
              </div>

              <div className="insights-summary-metrics">
                <article className="insights-summary-metric">
                  <span className="insights-label">Índice Gendaz</span>
                  <div className="insights-summary-metric__value">{score}/100</div>
                  <strong>{getStatusScore(score)}</strong>
                  <small>{score ? 'Dados sincronizados da empresa' : 'Sem comparação disponível'}</small>
                </article>
                <article className="insights-summary-metric">
                  <span className="insights-label">Tendência</span>
                  <div className="insights-summary-metric__value">{tendencia || 'Sem tendência'}</div>
                  <strong>{variacaoTendencia || 'Dados insuficientes'}</strong>
                  <small>em relação aos últimos 30 dias</small>
                </article>
                <article className="insights-summary-metric">
                  <span className="insights-label">Receita mensal</span>
                  <div className="insights-summary-metric__value">{receitaMensal}</div>
                  <strong>{`Mês atual: ${mesAtual}`}</strong>
                  <small>{dashboard?.financeiro?.totalRecebidoMes ? 'Receita consolidada do mês' : 'Sem receita registrada'}</small>
                </article>
              </div>
            </section>

            <div className="insights-change-risk-row">
              <section className="panel insights-changes-card">
                <div className="section-kicker">O que mudou desde a última análise</div>
                <div className="insights-change-grid">
                  {principais.slice(0, 4).map((item, index) => {
                    const Icon = iconPorTipo(item.tipo)
                    return (
                      <article key={`${item.tipo || 'change'}-${item.titulo || index}`} className="insights-change-card">
                        <div className="insights-change-card__icon">
                          <Icon size={16} />
                        </div>
                        <div className="insights-change-card__content">
                          <span>{item.titulo || item.tipo || 'Mudança'}</span>
                          <p>{item.descricao || 'Atualização detectada nos dados da empresa.'}</p>
                        </div>
                        <strong>{badgeImpacto(item)}</strong>
                      </article>
                    )
                  })}
                  {principais.length === 0 && <p className="insights-empty">Nenhuma mudança relevante detectada.</p>}
                </div>
              </section>

              <section className="panel insights-risk-card">
                <div className="section-kicker">Risco atual</div>
                <strong>{riscoAtual}</strong>
                <div className="insights-risk-bars" aria-hidden="true">
                  {Array.from({ length: 10 }).map((_, index) => (
                    <span key={index} className={index < riscoAtivos ? 'is-active' : ''} />
                  ))}
                </div>
                <p>{riscoDescricao}</p>
              </section>
            </div>

            <section className="panel insights-section insights-primary-panel">
              <div className="section-kicker">Insights principais</div>
              <div className="insights-principais-grid">
                {principais.length > 0 ? principais.map((item, index) => {
                  const Icon = iconPorTipo(item.tipo)
                  const tags = ['Crítico', 'Importante', 'Oportunidade']
                  const actions = ['Ver clientes', 'Ver horários', 'Ver serviço']
                  return (
                    <article key={`${item.tipo || 'principal'}-${item.titulo || index}`} className="insights-core-card">
                      <div className="insights-core-card__top">
                        <span className={`insights-pill insights-pill--${index === 0 ? 'red' : index === 1 ? 'orange' : 'green'}`}>{tags[index] || 'Insight'}</span>
                        <span className="insights-core-card__action">Ação</span>
                      </div>
                      <div className="insights-core-card__body">
                        <div className="insights-core-card__icon">
                          <Icon size={18} />
                        </div>
                        <div>
                          <h3>{item.titulo || 'Insight principal'}</h3>
                          <p>{item.descricao || 'Atualização importante detectada nos dados reais.'}</p>
                        </div>
                      </div>
                      <div className="insights-core-card__footer">
                        <div>
                          <small>Impacto estimado</small>
                          <strong>{formatCurrency(item.impacto || item.impactoEstimado)}</strong>
                        </div>
                        <Button variant="secondary">{actions[index] || 'Ver mais'}</Button>
                      </div>
                    </article>
                  )
                }) : (
                  <p className="insights-empty">Sem insights principais disponíveis no momento.</p>
                )}
              </div>
            </section>

            <div className="insights-bottom-grid">
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
                        <strong>{formatCurrency(item.impacto || item.impactoEstimado)}</strong>
                      </div>
                    </article>
                  ))}
                  {oportunidades.length === 0 && <p className="insights-empty">Nenhuma oportunidade relevante detectada agora.</p>}
                </div>
                <div className="insights-link-row">
                  <span>Ver todas oportunidades</span>
                  <span>→</span>
                </div>
              </section>

              <section className="panel insights-section">
                <div className="section-kicker">Ação mais importante hoje</div>
                {acaoPrincipal ? (
                  <article className="insights-highlight-card">
                    <div className="insights-highlight-card__icon">
                      <Wand2 size={22} />
                    </div>
                    <div className="insights-highlight-card__content">
                      <strong>{acaoPrincipal.descricao || acaoPrincipal.titulo || 'Ação prioritária'}</strong>
                      <p>{acaoPrincipal.impactoEstimado || acaoPrincipal.impacto || acaoPrincipal.urgencia || 'Ação sugerida com base nos dados.'}</p>
                      <div className="insights-highlight-card__meta">
                        <div>
                          <small>Impacto estimado</small>
                          <strong>{formatCurrency(acaoPrincipal.impactoEstimado || acaoPrincipal.impacto)}</strong>
                        </div>
                        <div>
                          <small>Tempo necessário</small>
                          <strong>{acaoPrincipal.tempoNecessario || 'Dados insuficientes'}</strong>
                        </div>
                      </div>
                      <Button variant="secondary">Executar agora</Button>
                    </div>
                  </article>
                ) : (
                  <p className="insights-empty">Nenhuma ação prioritária no momento.</p>
                )}
                <div className="insights-link-row">
                  <span>Ver todas ações recomendadas</span>
                  <span>→</span>
                </div>
              </section>

              <section className="panel insights-section">
                <div className="section-kicker">Ações recomendadas pela IA</div>
                <div className="insights-action-list">
                  {recomendacoes.slice(0, 3).map((acao, index) => (
                    <div key={`${acao.descricao || index}`} className="insights-action-row">
                      <label className="insights-check">
                        <input type="checkbox" readOnly />
                        <span />
                      </label>
                      <div className="insights-action-row__content">
                        <strong>{acao.descricao}</strong>
                        <p>{acao.impactoEstimado || acao.urgencia || 'Ação sugerida'}</p>
                      </div>
                      <span className="insights-action-row__impacto">{formatCurrency(acao.impactoEstimado || acao.impacto)}</span>
                    </div>
                  ))}
                  {recomendacoes.length === 0 && <p className="insights-empty">Nenhuma ação prioritária no momento.</p>}
                </div>
                <div className="insights-link-row">
                  <span>Ver plano completo</span>
                  <span>→</span>
                </div>
              </section>
            </div>
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
                  </div>
                </div>
              )}
            </section>
          </aside>
        </div>
      )}

      {analiseAberta && (
        <div className="insights-modal-backdrop" role="presentation" onClick={() => setAnaliseAberta(false)}>
          <div className="panel insights-modal" role="dialog" aria-modal="true" aria-label="Análise completa" onClick={(event) => event.stopPropagation()}>
            <div className="insights-modal__head">
              <div>
                <div className="section-kicker">Análise completa</div>
                <h2>{dashboard?.empresaNome || 'Empresa vinculada'}</h2>
                <p>{resumoTexto(dashboard)}</p>
              </div>
              <Button variant="secondary" onClick={() => setAnaliseAberta(false)}>
                Fechar
              </Button>
            </div>

            <div className="insights-detail-grid">
              <div>
                <span>Saúde da empresa</span>
                <strong>{score}/100</strong>
                <p>{score >= 70 ? 'Empresa saudável' : score >= 45 ? 'Atenção necessária' : 'Empresa em risco'}</p>
              </div>
              <div>
                <span>Impacto total</span>
                <strong>{impactoTotal}</strong>
                <p>Baseado nos dados reais sincronizados.</p>
              </div>
              <div>
                <span>Histórico</span>
                <strong>{historico.length}</strong>
                <p>{historico.length > 0 ? 'Recomendações registradas' : 'Sem histórico ainda.'}</p>
              </div>
            </div>
          </div>
        </div>
      )}

      {metaAberta && (
        <div className="insights-modal-backdrop" role="presentation" onClick={() => setMetaAberta(false)}>
          <div className="panel insights-meta-modal" role="dialog" aria-modal="true" aria-label="Adicionar meta" onClick={(event) => event.stopPropagation()}>
            <div className="insights-modal__head">
              <div>
                <div className="section-kicker">Meta do mês</div>
                <h2>Adicionar meta</h2>
                <p>Defina título, quantidade e tempo para acompanhar melhor a meta.</p>
              </div>
              <Button variant="secondary" onClick={() => setMetaAberta(false)}>
                Fechar
              </Button>
            </div>

            <div className="insights-meta-form">
              <label>
                <span>Título da meta</span>
                <input
                  type="text"
                  value={novaMeta.titulo}
                  onChange={(event) => setNovaMeta((current) => ({ ...current, titulo: event.target.value }))}
                  placeholder="Ex: Faturar R$ 20.000"
                />
              </label>
              <label>
                <span>Quantidade</span>
                <input
                  type="text"
                  value={novaMeta.quantidade}
                  onChange={(event) => setNovaMeta((current) => ({ ...current, quantidade: event.target.value }))}
                  placeholder="Ex: 20 agendamentos"
                />
              </label>
              <label>
                <span>Tempo</span>
                <input
                  type="text"
                  value={novaMeta.tempo}
                  onChange={(event) => setNovaMeta((current) => ({ ...current, tempo: event.target.value }))}
                  placeholder="Ex: 30 dias"
                />
              </label>
            </div>

            <div className="insights-meta-actions">
              <Button variant="secondary" onClick={() => setMetaAberta(false)}>
                Cancelar
              </Button>
              <Button onClick={salvarMeta} disabled={!String(novaMeta.titulo).trim() || !String(novaMeta.quantidade).trim() || !String(novaMeta.tempo).trim()}>
                Salvar meta
              </Button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
