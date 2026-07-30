import { useState } from 'react'
import {
  AlertCircle,
  Calendar,
  CheckCircle,
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

function nomeCategoria(item, fallback) {
  return String(item?.tipo || fallback || 'Empresa').toUpperCase()
}

function iconPorTipo(tipo) {
  const valor = String(tipo || '').toLowerCase()
  if (valor.includes('cliente')) return Users
  if (valor.includes('finance')) return TrendingUp
  if (valor.includes('agenda') || valor.includes('ocios')) return Calendar
  if (valor.includes('acao')) return Wrench
  return AlertCircle
}

function resumoTexto(dashboard) {
  const score = Number(dashboard?.scoreGeral ?? 0)
  const alertas = safeArray(dashboard?.alertas).length
  const oportunidades = safeArray(dashboard?.oportunidades).length
  const acoes = safeArray(dashboard?.acoes).length

  if (!dashboard) return 'Carregando análise real da empresa...'

  return `Analisei sua empresa nos últimos 30 dias. Score ${score}/100, ${alertas} alertas, ${oportunidades} oportunidades e ${acoes} recomendações prioritárias.`
}

export default function Insights() {
  const { dashboard, historico, loading, error, recarregar, analisar } = useInsights()
  const [chatAberto, setChatAberto] = useState(true)
  const [analiseAberta, setAnaliseAberta] = useState(false)
  const [sincronizando, setSincronizando] = useState(false)

  const score = Number(dashboard?.scoreGeral ?? 0)
  const alertas = safeArray(dashboard?.alertas)
  const oportunidades = safeArray(dashboard?.oportunidades)
  const recomendacoes = safeArray(dashboard?.acoes)
  const nomeEmpresa = dashboard?.empresaNome || 'Sua empresa'
  const impactoTotal = dashboard?.impactoTotal || 'Sem impacto calculado'
  const dataAnalise = dashboard?.geradoEm
    ? new Date(dashboard.geradoEm).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' })
    : null
  const principais = safeArray(dashboard?.principais)

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
      console.error('[insights] erro ao sincronizar dados', err)
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

  const simulacoes = [
    { pergunta: `E se eu ampliar horários na ${nomeEmpresa}?`, impacto: 'Impacto estimado com base na ocupação atual' },
    { pergunta: 'E se eu subir 10% o preço?', impacto: 'Simulação sobre o faturamento atual' },
    { pergunta: 'E se eu atuar nos clientes inativos?', impacto: 'Simulação sobre recorrência e reativação' },
  ]

  return (
    <section className="page insights-page insights-page--new">
      <div className="page-title insights-hero">
        <div>
          <span className="section-kicker">Consultoria IA</span>
          <h1>Insights</h1>
          <p>A IA analisa os dados da sua empresa e recomenda ações para crescer.</p>
        </div>
        <Button variant="secondary" icon={Sparkles} onClick={handleSincronizarDados} disabled={sincronizando || loading}>
          {sincronizando ? 'Sincronizando...' : 'Sincronizar dados'}
        </Button>
      </div>

      {loading && <div className="panel insights-panel">Carregando análise real da empresa...</div>}
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
                  {dataAnalise ? `Última sincronização: ${dataAnalise}` : 'Aguardando sincronização da empresa vinculada'}
                </small>
                <Button variant="secondary" onClick={() => setAnaliseAberta(true)}>
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
                {principais.map((item, index) => {
                  const Icon = iconPorTipo(item.tipo)
                  return (
                    <article key={`${item.tipo || 'principal'}-${item.titulo || index}`} className="insights-mini-card">
                      <div className="insights-mini-card__head">
                        <Icon size={18} />
                        <span>{nomeCategoria(item, item.tipo || 'Empresa')}</span>
                      </div>
                      <h3>{item.titulo}</h3>
                      <p>{item.descricao}</p>
                      <small>{badgeImpacto(item)}</small>
                    </article>
                  )
                })}
                {principais.length === 0 && <p className="insights-empty">Sem insights principais disponíveis no momento.</p>}
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
                        <strong>{formatCurrency(item.impacto || item.impactoEstimado)}</strong>
                      </div>
                    </article>
                  ))}
                  {oportunidades.length === 0 && <p className="insights-empty">Nenhuma oportunidade relevante detectada agora.</p>}
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
                  {recomendacoes.length === 0 && <p className="insights-empty">Nenhuma ação prioritária no momento.</p>}
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

            <div className="insights-detail-grid" style={{ marginTop: 12 }}>
              <div>
                <span>Alertas</span>
                <strong>{alertas.length}</strong>
                <p>{alertas[0]?.titulo || 'Nenhum alerta principal encontrado.'}</p>
              </div>
              <div>
                <span>Oportunidades</span>
                <strong>{oportunidades.length}</strong>
                <p>{oportunidades[0]?.titulo || 'Nenhuma oportunidade principal encontrada.'}</p>
              </div>
              <div>
                <span>Ações recomendadas</span>
                <strong>{recomendacoes.length}</strong>
                <p>{recomendacoes[0]?.descricao || 'Nenhuma ação registrada.'}</p>
              </div>
            </div>

            <div className="insights-summary-grid" style={{ marginTop: 12 }}>
              <section className="panel insights-section">
                <div className="section-kicker">Alertas</div>
                <div className="insights-list">
                  {alertas.slice(0, 3).map((item, index) => (
                    <article key={`${item.titulo || item.descricao || index}`} className="insights-list-item">
                      <div className="insights-list-item__icon">
                        <AlertCircle size={18} />
                      </div>
                      <div className="insights-list-item__content">
                        <strong>{item.titulo || 'Alerta'}</strong>
                        <p>{item.descricao || item.impacto || 'Sem descrição detalhada.'}</p>
                      </div>
                      <div className="insights-list-item__meta">
                        <small>{item.urgencia || 'Impacto'}</small>
                        <strong>{item.impacto || '-'}</strong>
                      </div>
                    </article>
                  ))}
                  {alertas.length === 0 && <p className="insights-empty">Sem alertas no momento.</p>}
                </div>
              </section>

              <section className="panel insights-section">
                <div className="section-kicker">Oportunidades</div>
                <div className="insights-list">
                  {oportunidades.slice(0, 3).map((item, index) => (
                    <article key={`${item.titulo || item.descricao || index}`} className="insights-list-item insights-list-item--oportunidade">
                      <div className="insights-list-item__icon">
                        <TrendingUp size={18} />
                      </div>
                      <div className="insights-list-item__content">
                        <strong>{item.titulo || 'Oportunidade'}</strong>
                        <p>{item.descricao || item.impacto || 'Sem descrição detalhada.'}</p>
                      </div>
                      <div className="insights-list-item__meta">
                        <small>Impacto</small>
                        <strong>{item.impacto || '-'}</strong>
                      </div>
                    </article>
                  ))}
                  {oportunidades.length === 0 && <p className="insights-empty">Nenhuma oportunidade relevante detectada agora.</p>}
                </div>
              </section>

              <section className="panel insights-section">
                <div className="section-kicker">Ações recomendadas</div>
                <div className="insights-action-list">
                  {recomendacoes.slice(0, 4).map((item, index) => (
                    <div key={`${item.descricao || index}`} className="insights-action-row">
                      <div className="insights-list-item__icon">
                        <Wrench size={18} />
                      </div>
                      <div className="insights-action-row__content">
                        <strong>{item.descricao || 'Ação recomendada'}</strong>
                        <p>{item.impactoEstimado || item.urgencia || 'Ação sugerida com base nos dados.'}</p>
                      </div>
                    </div>
                  ))}
                  {recomendacoes.length === 0 && <p className="insights-empty">Nenhuma ação prioritária no momento.</p>}
                </div>
              </section>
            </div>

            <section className="panel insights-section" style={{ marginTop: 12 }}>
              <div className="section-kicker">Histórico</div>
              <div className="insights-history">
                {historico.slice(0, 4).map((item, index) => (
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
        </div>
      )}
    </section>
  )
}
