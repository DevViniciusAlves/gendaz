import { Check, ExternalLink, Loader, RefreshCw, ShieldCheck, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { appApi } from '../api/appApi.js'
import ScrollReveal from '../components/ScrollReveal.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'
import { useCheckoutTimer } from '../hooks/useCheckoutTimer.js'
import { useLocalData } from '../hooks/useLocalData.js'
import { checkoutExpirado } from '../utils/checkoutUtils.js'

const planosBase = [
  {
    codigo: 'BASICO',
    nome: 'Plano Básico',
    subtitulo: 'Agenda simples',
    extra: '7 dias grátis',
    descrição: 'Para organizar sua agenda, clientes e serviços de forma prática e eficiente.',
    beneficios: [
      'Financeiro - Pagamentos automatizados - Relatórios',
      'Histórico ilimitado',
      'Agendamentos ilimitados',
      'Confirmação de agendamentos',
    ],
    naoInclui: [
      'CRM integrado',
      'Insights',
      'Até 3 usuários',
      'Financeiro completo',
    ],
    cta: 'Assinar Básico',
    precoFallback: 29.90,
  },
  {
    codigo: 'PRO',
    nome: 'Plano Pro',
    subtitulo: 'Gestão completa com financeiro',
    extra: '7 dias grátis',
    descrição: 'Para gerenciar sua agenda, equipe, pagamentos e insights com inteligência.',
    beneficios: [
      'Tudo do Plano Básico +',
      'Até 3 usuários na conta',
      'CRM integrado',
      'Insights com GendazIA no controle',
      'Financeiro completo: caixa, despesas pagamentos automatizados',
    ],
    cta: 'Assinar Pro',
    precoFallback: 79.90,
    destaque: true,
  },
  {
    codigo: 'PLUS',
    nome: 'Plano Plus',
    subtitulo: 'Mais capacidade para sua equipe',
    extra: '7 dias grátis',
    descrição: 'Para equipes maiores com maior necessidade de gerenciamento e acesso.',
    beneficios: [
      'Tudo do Plano Pro +',
      'Até 7 usuários na conta',
      'CRM integrado',
      'Insights com GendazIA no controle',
      'Financeiro completo: caixa, despesas pagamentos automatizados',
    ],
    cta: 'Assinar Plus',
    precoFallback: 109.90,
  },
  {
    codigo: 'ENTERPRISE',
    nome: 'Plano Enterprise',
    subtitulo: 'Escalabilidade máxima',
    extra: '7 dias grátis',
    descrição: 'Para operações robustas com gerenciamento extensivo de usuários.',
    beneficios: [
      'Tudo do Plano Plus +',
      'Até 15 usuários na conta',
      'CRM integrado',
      'Insights com GendazIA no controle',
      'Financeiro completo: caixa, despesas pagamentos automatizados',
    ],
    cta: 'Assinar Enterprise',
    precoFallback: 149.90,
  },
]

const statusPagamentoTexto = {
  PAYMENT_PENDING: 'Pagamento não realizado',
  PAYMENT_APPROVED: 'Pagamento aprovado',
  PAYMENT_REJECTED: 'Pagamento recusado',
  PAYMENT_CANCELED: 'Pagamento cancelado',
  PAYMENT_EXPIRED: 'Pagamento expirado',
}

function formatarPreco(valor) {
  return Number(valor || 0).toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: 2,
  }) + '/mes'
}

function formatarDataCurta(iso) {
  if (!iso) return ''
  const [a, m, d] = String(iso).slice(0, 10).split('-')
  return d && m && a ? `${d}/${m}/${a}` : String(iso).slice(0, 10)
}

function hojeISO() {
  return new Date().toISOString().slice(0, 10)
}

function duracaoDiasEntre(inicio, fim) {
  if (!inicio || !fim) return null
  const diff = (new Date(String(fim).slice(0, 10)) - new Date(String(inicio).slice(0, 10))) / 86400000
  return Number.isFinite(diff) ? Math.round(diff) : null
}

export default function Planos() {
  const navigate = useNavigate()
  const [data] = useLocalData('planos')
  const { usuario, atualizarPlanoAtual, atualizarUsuario } = useAuth()
  const empresaId = usuario?.empresaId
  const usuarioRef = useRef(usuario)
  usuarioRef.current = usuario
  const [metodoPagamento] = useState('CREDIT_CARD')
  const [pagamentoPlano, setPagamentoPlano] = useState(() => usuario?.pagamentoPlano || null)
  const [checkoutSolicitado, setCheckoutSolicitado] = useState(false)
  const [carregando, setCarregando] = useState(false)
  const [planoCarregando, setPlanoCarregando] = useState(null)
  const [erro, setErro] = useState('')
  const [filaAssinaturas, setFilaAssinaturas] = useState([])
  const perfilAtendente = String(usuario?.perfil || '').toUpperCase() === 'ATENDENTE'

  const planos = useMemo(() => planosBase.map((plano) => {
    const planoApi = data.planos?.find((item) => String(item.nome).toUpperCase() === plano.codigo)
    return {
      ...plano,
      preco: formatarPreco(planoApi?.valorMensal ?? plano.precoFallback),
    }
  }), [data.planos])

  const timerPlano = useCheckoutTimer(checkoutSolicitado ? pagamentoPlano : null)
  const checkoutValidoPlano = checkoutSolicitado && Boolean(pagamentoPlano?.checkoutUrl) && !timerPlano.expirou

  const filaAtiva = useMemo(() => {
    const hoje = hojeISO()
    return (Array.isArray(filaAssinaturas) ? filaAssinaturas : [])
      .filter((item) => {
        const status = String(item?.status || '').toUpperCase()
        if (status !== 'ATIVA' && status !== 'TESTE') return false
        if (!item?.dataFim) return true
        return String(item.dataFim).slice(0, 10) > hoje
      })
      .sort((a, b) => String(a.dataInicio || '').localeCompare(String(b.dataInicio || '')))
  }, [filaAssinaturas])
  const planoVigente = String(usuario?.plano || usuario?.assinatura?.planoNome || '').toUpperCase()
  const limiteAtingido = filaAtiva.length >= 2
  const proximoPlano = filaAtiva.find((item) => String(item.planoNome || '').toUpperCase() !== planoVigente) || null
  const statusPagamentoPlano = pagamentoPlano?.status === 'PAYMENT_PENDING'
    ? statusPagamentoTexto.PAYMENT_PENDING
    : statusPagamentoTexto[pagamentoPlano?.status] || pagamentoPlano?.status

  const sincronizarDadosPlanos = useCallback(async () => {
    const empresaAtual = usuarioRef.current?.empresaId
    if (!empresaAtual) return

    await atualizarPlanoAtual().catch(() => null)
    if (usuarioRef.current?.empresaId !== empresaAtual) return

    const listaAssinaturas = await appApi.listarAssinaturas(empresaAtual).catch(() => [])
    if (usuarioRef.current?.empresaId !== empresaAtual) return
    setFilaAssinaturas(Array.isArray(listaAssinaturas) ? listaAssinaturas : [])

    const pagamentos = await appApi.listarPagamentosPlano(empresaAtual).catch(() => [])
    if (usuarioRef.current?.empresaId !== empresaAtual) return

    const listaPagamentos = Array.isArray(pagamentos) ? pagamentos : []
    const usuarioAtual = usuarioRef.current
    const planoAtual = String(usuarioAtual?.plano || usuarioAtual?.pagamentoPlano?.planoNome || usuarioAtual?.pagamentoPlano?.plano || '').toUpperCase()
    const pendenteMesmoPlano = listaPagamentos.find((item) => {
      const planoItem = String(item?.planoNome || item?.plano || '').toUpperCase()
      return item?.status === 'PAYMENT_PENDING' && planoItem === planoAtual
    })
    const pendenteRecente = listaPagamentos.find((item) => item?.status === 'PAYMENT_PENDING')
    const pagamentoAtual = pendenteMesmoPlano || pendenteRecente || listaPagamentos[0] || usuarioAtual?.pagamentoPlano || null
    const checkoutAindaValido = Boolean(pagamentoAtual?.checkoutUrl) && !checkoutExpirado(pagamentoAtual)

    setPagamentoPlano(pagamentoAtual)
    setCheckoutSolicitado((atual) => checkoutAindaValido || atual)
  }, [empresaId, atualizarPlanoAtual])

  useEffect(() => {
    if (!empresaId) return
    sincronizarDadosPlanos()
  }, [empresaId, sincronizarDadosPlanos])

  async function iniciarPagamentoPro() {
    if (!usuario) {
      navigate('/criar-conta?plano=PRO')
      return
    }
    if (perfilAtendente) {
      setErro('Seu perfil não permite comprar ou editar planos.')
      return
    }
    if (limiteAtingido) {
      setErro('Voce ja possui 2 planos ativos. Aguarde um deles expirar para contratar novamente.')
      return
    }

    setErro('')
    setPlanoCarregando('PRO')
    try {
      const pagamento = await appApi.iniciarPagamentoPro({
        empresaId: usuario.empresaId,
        metodoPagamento: 'CREDIT_CARD',
        plano: 'PRO',
        customerName: usuario.nome,
        customerEmail: usuario.email,
        customerPhone: usuario.telefone,
        antifraudProfilingAttemptReference: usuario.id ? `agendeasy-${usuario.id}` : '',
        forceNew: true,
      })
      setPagamentoPlano(pagamento)
      setCheckoutSolicitado(true)
      atualizarUsuario({ pagamentoPlano: pagamento })
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Nao foi possivel iniciar o pagamento.')
    } finally {
      setPlanoCarregando(null)
    }
  }

  async function iniciarPagamentoBasico() {
    if (!usuario) {
      navigate('/criar-conta?plano=BASICO')
      return
    }
    if (perfilAtendente) {
      setErro('Seu perfil não permite comprar ou editar planos.')
      return
    }
    if (limiteAtingido) {
      setErro('Voce ja possui 2 planos ativos. Aguarde um deles expirar para contratar novamente.')
      return
    }

    setErro('')
    setPlanoCarregando('BASICO')
    try {
      const pagamento = await appApi.iniciarPagamentoPlano({
        empresaId: usuario.empresaId,
        metodoPagamento: 'CREDIT_CARD',
        plano: 'BASICO',
        customerName: usuario.nome,
        customerEmail: usuario.email,
        customerPhone: usuario.telefone,
        antifraudProfilingAttemptReference: usuario.id ? `agendeasy-${usuario.id}` : '',
        forceNew: true,
      })
      setPagamentoPlano(pagamento)
      setCheckoutSolicitado(true)
      atualizarUsuario({ pagamentoPlano: pagamento })
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Nao foi possivel iniciar o pagamento.')
    } finally {
      setPlanoCarregando(null)
    }
  }

  async function atualizarStatusPagamento() {
    if (!usuario?.empresaId || !pagamentoPlano?.id) return
    setErro('')
    setCarregando(true)
    try {
      const resultado = await appApi.verificarPagamentoPlano(usuario.empresaId, pagamentoPlano.id)
      const pagamento = resultado.pagamento
      setPagamentoPlano(pagamento)
      atualizarUsuario({ pagamentoPlano: pagamento })
      if (resultado.statusVerificacao === 'APPROVED') {
        setCheckoutSolicitado(false)
        await sincronizarDadosPlanos()
      } else if (resultado.mensagem) {
        setErro(resultado.mensagem)
      }
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Nao foi possivel consultar o pagamento.')
    } finally {
      setCarregando(false)
    }
  }

  function handlePlanClick(plano) {
    if (plano.codigo === 'PRO') {
      iniciarPagamentoPro()
      return
    }
    if (!usuario) {
      navigate(`/criar-conta?plano=${encodeURIComponent(plano.codigo)}`)
      return
    }
    if (plano.codigo === 'BASICO' || plano.codigo === 'PLUS' || plano.codigo === 'ENTERPRISE') {
      iniciarPagamentoPlano(plano.codigo)
      return
    }
  }

  async function iniciarPagamentoPlano(codigoPlano) {
    if (!usuario) {
      navigate(`/criar-conta?plano=${encodeURIComponent(codigoPlano)}`)
      return
    }
    if (perfilAtendente) {
      setErro('Seu perfil não permite comprar ou editar planos.')
      return
    }
    if (limiteAtingido) {
      setErro('Voce ja possui 2 planos ativos. Aguarde um deles expirar para contratar novamente.')
      return
    }

    setErro('')
    setPlanoCarregando(codigoPlano)
    try {
      const pagamento = await appApi.iniciarPagamentoPlano({
        empresaId: usuario.empresaId,
        metodoPagamento: 'CREDIT_CARD',
        plano: codigoPlano,
        customerName: usuario.nome,
        customerEmail: usuario.email,
        customerPhone: usuario.telefone,
        antifraudProfilingAttemptReference: usuario.id ? `agendeasy-${usuario.id}` : '',
        forceNew: true,
      })
      setPagamentoPlano(pagamento)
      setCheckoutSolicitado(true)
      atualizarUsuario({ pagamentoPlano: pagamento })
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Nao foi possivel iniciar o pagamento.')
    } finally {
      setPlanoCarregando(null)
    }
  }

  return (
    <section className="page">
      <div className="page-title plans-page-title">
        <span className="section-kicker">Comercial</span>
        <h1>Planos do atendimento</h1>
        <p>Escolha o plano que melhor se encaixa na rotina do seu atendimento.</p>
        {perfilAtendente && <p className="plan-payment-note plan-payment-helper">Seu perfil não pode comprar ou editar planos.</p>}
      </div>

      {usuario && (
        <div className="panel plans-page-title plan-payment-panel">
          <div className="panel-head">
            <div>
              <span className="section-kicker">Plano atual</span>
              <h2>{planoVigente === 'PRO' ? 'Plano Pro em vigor' : planoVigente === 'PLUS' ? 'Plano Plus em vigor' : planoVigente === 'ENTERPRISE' ? 'Plano Enterprise em vigor' : 'Plano Basico em vigor'}</h2>
            </div>
          </div>

          {filaAtiva.length === 0 && (
            <p className="plan-payment-note">Voce ainda não possui um plano ativo. Escolha um plano abaixo para comecar.</p>
          )}
          {filaAtiva.length === 1 && !limiteAtingido && (
            <p className="plan-payment-note">
              {proximoPlano
                ? 'Voce possui 1 plano ativo. Pode adicionar mais um: ele comeca automaticamente depois que o plano atual terminar.'
                : 'Voce possui 1 plano ativo. Quer garantir mais tempo? Compre o mesmo plano para somar os dias, ou adicione o outro plano para quando este terminar.'}
            </p>
          )}
          {limiteAtingido && (
            <p className="plan-payment-note plan-payment-helper">
              Limite de 2 planos ativos atingido. Aguarde um deles expirar para contratar novamente.
            </p>
          )}

          {filaAtiva.length > 0 && (
            <div className="plan-payment-status">
              {filaAtiva.map((item) => {
                const hoje = hojeISO()
                const inicio = String(item.dataInicio || '').slice(0, 10)
                const fim = String(item.dataFim || '').slice(0, 10)
                const ehFuturo = inicio > hoje
                const ehTeste = String(item.status || '').toLowerCase() === 'teste'
                const duracao = duracaoDiasEntre(inicio, fim)
                return (
                  <div key={item.id} className="plan-payment-status-head">
                    <div>
                      <span>
                        {String(item.planoNome || '').toUpperCase()}
                        {ehFuturo
                          ? ` · começa em ${formatarDataCurta(inicio)}${duracao ? ` (${duracao} dias)` : ''}`
                          : item.diasRestantes != null && item.diasRestantes > 0
                            ? ` · restam ${item.diasRestantes} dias`
                            : ''}
                      </span>
                      <strong>
                        {ehTeste ? 'Teste gratuito' : ehFuturo ? 'Agendado' : 'Em vigor'} · {inicio} a {fim}
                      </strong>
                    </div>
                  </div>
                )
              })}
            </div>
          )}

          {checkoutValidoPlano && (
            <div className="plan-payment-status plan-payment-status--checkout">
              <div className="plan-payment-status-head">
                <div>
                  <span>Status do pagamento</span>
                  <strong>{statusPagamentoPlano}</strong>
                </div>
              </div>
              <p className="plan-payment-note">Faça o pagamento pelo checkout abaixo.</p>
              <div className="plan-payment-actions">
                <a href={pagamentoPlano.checkoutUrl} target="_blank" rel="noreferrer" className="btn btn-primary">
                  <ExternalLink size={16} /> Abrir checkout seguro
                </a>
                <span className="checkout-timer">Expira em: {timerPlano.formatado}</span>
                <button type="button" className="btn btn-secondary" onClick={atualizarStatusPagamento} disabled={carregando}>
                  {carregando ? <><Loader className="spin" size={16} /> Verificando...</> : <><RefreshCw size={16} /> Já paguei, verificar</>}
                </button>
              </div>
              <small className="plan-payment-helper">Após a aprovação, sua conta Pro pode levar até 30 minutos para ser liberada.</small>
            </div>
          )}

          {erro && <p className="form-error">{erro}</p>}
        </div>
      )}

      <div className="plans-grid detailed plans-centered-grid">
        {planos.map((plano, index) => (
          <ScrollReveal className="plan-card plan-card-sale" delay={index * 100} key={plano.nome}>
            {plano.destaque && <span className="recommended-badge">Mais usado</span>}
            <div className="plan-card-body">
                <div className="plan-head">
                  <div>
                  <h2 style={{ color: '#ffa95e' }}>{plano.nome}</h2>
                  <p className="plan-subtitle">{plano.subtitulo}</p>
                </div>
              </div>

              <div className="plan-price-block">
                {plano.extra && <span className="plan-price-extra">{plano.extra}</span>}
                <strong className="plan-price">{plano.preco}</strong>
              </div>

              <p className="plan-description">{plano.descrição}</p>

              <div className="plan-section">
                <h3>Benefícios</h3>
                <div className="plan-list">
                  {plano.beneficios.map((item) => (
                    <strong key={item}>
                      <Check size={16} style={{ color: '#22c55e' }} />{item}
                    </strong>
                  ))}
                </div>
              </div>

              {plano.beneficiosExtra && plano.beneficiosExtra.length > 0 && (
                <div className="plan-section">
                  <h3>Benefícios</h3>
                  <div className="plan-list">
                    {plano.beneficiosExtra.map((item) => (
                      <strong key={item}>
                        <Check size={16} style={{ color: '#22c55e' }} />{item}
                      </strong>
                    ))}
                  </div>
                </div>
              )}

              {plano.naoInclui && plano.naoInclui.length > 0 && (
                <div className="plan-unavailable">
                  <span>Não inclui</span>
                  {plano.naoInclui.map((item) => (
                    <small key={item} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <X size={14} style={{ color: '#ef4444', flexShrink: 0 }} />
                      {item}
                    </small>
                  ))}
                </div>
              )}
            </div>

            <button
              type="button"
              onClick={() => handlePlanClick(plano)}
              className="btn btn-primary plan-action-link"
              disabled={planoCarregando != null || limiteAtingido || perfilAtendente}
            >
              {planoCarregando === plano.codigo
                ? <><Loader className="spin" size={16} /> Iniciando...</>
                : perfilAtendente
                  ? 'Bloqueado para atendente'
                  : limiteAtingido
                    ? 'Limite de 2 planos atingido'
                    : String(usuario?.plano || '').toUpperCase() === plano.codigo
                      ? 'Adicionar dias ao plano'
                      : 'Adicionar este plano'}
            </button>
          </ScrollReveal>
        ))}
      </div>
    </section>
  )
}
