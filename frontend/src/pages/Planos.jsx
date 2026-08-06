import { Check, ExternalLink, RefreshCw, ShieldCheck } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { appApi } from '../api/appApi.js'
import ScrollReveal from '../components/ScrollReveal.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'
import { useCheckoutTimer } from '../hooks/useCheckoutTimer.js'
import { useLocalData } from '../hooks/useLocalData.js'
import { checkoutExpirado, getInicioCheckout, limparInicioCheckout, registrarInicioCheckout } from '../utils/checkoutUtils.js'

const planosBase = [
  {
    codigo: 'BASICO',
    nome: 'Plano Basico',
    subtitulo: 'Agenda simples',
    extra: '7 dias gratis',
    descricao: 'Para organizar conversas, agenda, clientes e servicos no mesmo painel.',
    beneficios: [
      'Agenda com atendimento organizado',
      'Cadastro de clientes pelo painel',
      'Aba de promoções e cupons liberada',
      'Cadastro de ate 4 servicos',
      'Confirmacao de consulta automatizada',
      'Cancelamento e remarcacao automatizados',
    ],
    indicadoPara: ['Clinicas pequenas', 'Atendimento individual', 'Rotina de agenda e conversas'],
    naoInclui: ['Profissionais', 'Financeiro', 'Pagamentos', 'Relatorios'],
    cta: 'Comecar no Basico',
    precoFallback: 39.00,
  },
  {
    codigo: 'PRO',
    nome: 'Plano Pro',
    subtitulo: 'Gestao com financeiro simples',
    descricao: 'Para acompanhar agenda, profissionais, pagamentos e indicadores em um so lugar.',
    beneficios: [
      'Tudo que o Basico oferece',
      'Ate 3 usuarios por conta',
      'CRM automatizado para relacionamento',
      'Insights para apoiar a gestao',
      'Profissionais ilimitados',
      'Servicos ilimitados',
      'Aba de promoções e cupons completa',
    ],
    indicadoPara: ['Equipes de atendimento', 'Servicos com cobranca recorrente', 'Operacao com acompanhamento diario'],
    cta: 'Assinar Pro',
    precoFallback: 89.00,
    destaque: true,
  },
]

const statusPagamentoTexto = {
  PAYMENT_PENDING: 'Pagamento nao realizado',
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
  const [metodoPagamento] = useState('PIX')
  const [pagamentoPlano, setPagamentoPlano] = useState(() => usuario?.pagamentoPlano || null)
  const [checkoutSolicitado, setCheckoutSolicitado] = useState(false)
  const [checkoutSolicitadoEm, setCheckoutSolicitadoEm] = useState(null)
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState('')
  const [filaAssinaturas, setFilaAssinaturas] = useState([])

  const planos = useMemo(() => planosBase.map((plano) => {
    const planoApi = data.planos?.find((item) => String(item.nome).toUpperCase() === plano.codigo)
    return {
      ...plano,
      preco: formatarPreco(planoApi?.valorMensal ?? plano.precoFallback),
    }
  }), [data.planos])

  const pagamentoCheckoutPlano = pagamentoPlano
    ? { ...pagamentoPlano, checkoutSolicitadoEm: checkoutSolicitadoEm || getInicioCheckout(pagamentoPlano) }
    : null

  const filaAtiva = useMemo(() => {
    const hoje = hojeISO()
    return (Array.isArray(filaAssinaturas) ? filaAssinaturas : [])
      .filter((item) => {
        const status = String(item?.status || '').toUpperCase()
        if (status !== 'ATIVA' && status !== 'TESTE') return false
        if (!item?.dataFim) return true
        return String(item.dataFim).slice(0, 10) >= hoje
      })
      .sort((a, b) => String(a.dataInicio || '').localeCompare(String(b.dataInicio || '')))
  }, [filaAssinaturas])
  const planoVigente = String(usuario?.plano || usuario?.assinatura?.planoNome || '').toUpperCase()
  const limiteAtingido = filaAtiva.length >= 2
  const proximoPlano = filaAtiva.find((item) => String(item.planoNome || '').toUpperCase() !== planoVigente) || null
  const timerPlano = useCheckoutTimer(checkoutSolicitado ? pagamentoCheckoutPlano : null)
  const checkoutValidoPlano = checkoutSolicitado && Boolean(pagamentoPlano?.checkoutUrl) && !timerPlano.expirou
  const statusPagamentoPlano = pagamentoPlano?.status === 'PAYMENT_PENDING'
    ? statusPagamentoTexto.PAYMENT_PENDING
    : statusPagamentoTexto[pagamentoPlano?.status] || pagamentoPlano?.status

  useEffect(() => {
    if (!usuario?.empresaId) return
    atualizarPlanoAtual().catch(() => null)
    appApi.listarAssinaturas(usuario.empresaId)
      .then((lista) => setFilaAssinaturas(Array.isArray(lista) ? lista : []))
      .catch(() => null)
    appApi.listarPagamentosPlano(usuario.empresaId)
      .then((pagamentos) => {
        const lista = Array.isArray(pagamentos) ? pagamentos : []
        const planoAtual = String(usuario?.plano || usuario?.pagamentoPlano?.planoNome || usuario?.pagamentoPlano?.plano || '').toUpperCase()
        const pendenteMesmoPlano = lista.find((item) => {
          const planoItem = String(item?.planoNome || item?.plano || '').toUpperCase()
          return item?.status === 'PAYMENT_PENDING' && planoItem === planoAtual
        })
        const pendenteRecente = lista.find((item) => item?.status === 'PAYMENT_PENDING')
        const pagamentoAtual = pendenteMesmoPlano || pendenteRecente || lista[0] || usuario?.pagamentoPlano || null
        const checkoutAindaValido = Boolean(pagamentoAtual?.checkoutUrl) && !checkoutExpirado(pagamentoAtual)
        const inicioCheckout = checkoutAindaValido ? registrarInicioCheckout(pagamentoAtual, getInicioCheckout(pagamentoAtual) || new Date().toISOString()) : null

        setPagamentoPlano(pagamentoAtual)
        setCheckoutSolicitado((atual) => checkoutAindaValido || atual)
        setCheckoutSolicitadoEm(checkoutAindaValido ? inicioCheckout : null)
      })
      .catch(() => null)
  }, [usuario?.empresaId, usuario?.pagamentoPlano, usuario?.plano, atualizarPlanoAtual])

  async function iniciarPagamentoPro() {
    if (!usuario) {
      navigate('/criar-conta?plano=PRO')
      return
    }
    if (limiteAtingido) {
      setErro('Voce ja possui 2 planos ativos. Aguarde um deles expirar para contratar novamente.')
      return
    }

    setErro('')
    setCarregando(true)
    try {
      const pagamento = await appApi.iniciarPagamentoPro({
        empresaId: usuario.empresaId,
        metodoPagamento: metodoPagamento === 'CREDIT_CARD' ? 'CREDIT_CARD' : 'PIX_AUTO',
        plano: 'PRO',
        customerName: usuario.nome,
        customerEmail: usuario.email,
        customerPhone: usuario.telefone,
        customerDocType: usuario.documento ? 'cpf' : '',
        customerDocNumber: usuario.documento || '',
        antifraudProfilingAttemptReference: usuario.id ? `agendeasy-${usuario.id}` : '',
      })
      const inicioCheckout = registrarInicioCheckout(pagamento)
      setPagamentoPlano(pagamento)
      setCheckoutSolicitadoEm(inicioCheckout)
      setCheckoutSolicitado(true)
      atualizarUsuario({ pagamentoPlano: pagamento })
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Nao foi possivel iniciar o pagamento.')
    } finally {
      setCarregando(false)
    }
  }

  async function iniciarPagamentoBasico() {
    if (!usuario) {
      navigate('/criar-conta?plano=BASICO')
      return
    }
    if (limiteAtingido) {
      setErro('Voce ja possui 2 planos ativos. Aguarde um deles expirar para contratar novamente.')
      return
    }

    setErro('')
    setCarregando(true)
    try {
      const pagamento = await appApi.iniciarPagamentoPlano({
        empresaId: usuario.empresaId,
        metodoPagamento: metodoPagamento === 'CREDIT_CARD' ? 'CREDIT_CARD' : 'PIX_AUTO',
        plano: 'BASICO',
        customerName: usuario.nome,
        customerEmail: usuario.email,
        customerPhone: usuario.telefone,
        customerDocType: usuario.documento ? 'cpf' : '',
        customerDocNumber: usuario.documento || '',
        antifraudProfilingAttemptReference: usuario.id ? `agendeasy-${usuario.id}` : '',
      })
      const inicioCheckout = registrarInicioCheckout(pagamento)
      setPagamentoPlano(pagamento)
      setCheckoutSolicitadoEm(inicioCheckout)
      setCheckoutSolicitado(true)
      atualizarUsuario({ pagamentoPlano: pagamento })
    } catch (error) {
      setErro(error.response?.data?.mensagem || 'Nao foi possivel iniciar o pagamento.')
    } finally {
      setCarregando(false)
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
        limparInicioCheckout(pagamento)
        setCheckoutSolicitado(false)
        setCheckoutSolicitadoEm(null)
        await atualizarPlanoAtual()
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
    if (plano.codigo === 'BASICO') {
      iniciarPagamentoBasico()
      return
    }
  }

  return (
    <section className="page">
      <div className="page-title plans-page-title">
        <span className="section-kicker">Comercial</span>
        <h1>Planos do atendimento</h1>
        <p>Escolha o plano que melhor se encaixa na rotina do seu atendimento.</p>
      </div>

      {usuario && (
        <div className="panel plans-page-title plan-payment-panel">
          <div className="panel-head">
            <div>
              <span className="section-kicker">Plano atual</span>
              <h2>{planoVigente === 'PRO' ? 'Plano Pro em vigor' : 'Plano Basico em vigor'}</h2>
            </div>
          </div>

          {filaAtiva.length === 0 && (
            <p className="plan-payment-note">Voce ainda nao possui um plano ativo. Escolha um plano abaixo para comecar.</p>
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
                  <RefreshCw size={16} /> Já paguei, verificar
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
          <ScrollReveal className={plano.destaque ? 'plan-card highlight plan-card-sale' : 'plan-card plan-card-sale'} delay={index * 100} key={plano.nome}>
            {plano.destaque && <span className="recommended-badge">Mais recomendado</span>}
            <div className="plan-card-body">
                <div className="plan-head">
                  <div>
                  <h2 style={{ color: '#ffa95e' }}>{plano.nome}</h2>
                  <p className="plan-subtitle">{plano.subtitulo}</p>
                </div>
                {plano.destaque && <ShieldCheck size={20} />}
              </div>

              <div className="plan-price-block">
                {plano.extra && <span className="plan-price-extra">{plano.extra}</span>}
                <strong className="plan-price">{plano.preco}</strong>
              </div>

              <p className="plan-description">{plano.descricao}</p>

              <div className="plan-section">
                <h3>Beneficios</h3>
                <div className="plan-list">
                  {plano.beneficios.map((item) => (
                    <strong key={item}>
                      <Check size={16} />{item}
                    </strong>
                  ))}
                </div>
              </div>

              <div className="plan-section">
                <h3>Indicado para</h3>
                <div className="plan-list plan-list-muted">
                  {plano.indicadoPara.map((item) => <small key={item}>{item}</small>)}
                </div>
              </div>

              {plano.naoInclui && (
                <div className="plan-unavailable">
                  <span>O que nao inclui</span>
                  {plano.naoInclui.map((item) => <small key={item}>{item}</small>)}
                </div>
              )}
            </div>

            <button
              type="button"
              onClick={() => handlePlanClick(plano)}
              className={plano.destaque ? 'btn btn-primary plan-action-link' : 'btn btn-secondary plan-action-link'}
              disabled={carregando || limiteAtingido}
            >
              {carregando
                ? 'Iniciando...'
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
