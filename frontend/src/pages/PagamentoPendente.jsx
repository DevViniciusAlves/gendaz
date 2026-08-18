import { Check, Clock3, CreditCard, Info, LockKeyhole, RefreshCw } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import Button from '../components/Button.jsx'
import { appApi } from '../api/appApi.js'
import { useAuth } from '../contexts/AuthContext.jsx'
import logoWhite from '../assets/logos/gendaz-logo-branco.png'
import { useCheckoutTimer } from '../hooks/useCheckoutTimer.js'
import { checkoutAtivo, checkoutExpirado } from '../utils/checkoutUtils.js'

const statusView = {
  PAYMENT_PENDING: { label: 'Aguardando pagamento', tone: 'pending' },
  PAYMENT_APPROVED: { label: 'Pagamento aprovado', tone: 'approved' },
  PAYMENT_REJECTED: { label: 'Pagamento recusado', tone: 'danger' },
  PAYMENT_CANCELED: { label: 'Pagamento cancelado', tone: 'danger' },
  PAYMENT_EXPIRED: { label: 'Pagamento expirado', tone: 'danger' },
}

function mensagemPadrao(status) {
  if (status === 'PAYMENT_APPROVED') return 'Pagamento aprovado! Sua conta foi liberada.'
  if (status === 'PAYMENT_REJECTED') return 'Pagamento recusado. Gere uma nova cobranca e tente novamente.'
  if (status === 'PAYMENT_CANCELED') return 'Pagamento cancelado. Gere uma nova cobranca para continuar.'
  if (status === 'PAYMENT_EXPIRED') return 'Pagamento expirado. Gere uma nova cobranca para continuar.'
  return 'Pagamento ainda nao foi confirmado. Aguarde alguns minutos e tente novamente.'
}

export default function PagamentoPendente() {
  const navigate = useNavigate()
  const { getPagamentoPendente, limparPagamentoPendente } = useAuth()
  const [pendente, setPendente] = useState(() => getPagamentoPendente())
  const [mensagem, setMensagem] = useState('')
  const [tipoMensagem, setTipoMensagem] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [gerando, setGerando] = useState(false)

  const pagamento = pendente?.pagamentoPlano
  const status = pagamento?.status || 'PAYMENT_PENDING'
  const statusAtual = statusView[status] || statusView.PAYMENT_PENDING
  const aprovado = status === 'PAYMENT_APPROVED'
  const checkoutAtivoAtual = checkoutAtivo(pagamento)
  const checkoutExpiradoAtual = checkoutExpirado(pagamento)
  const timerCheckout = useCheckoutTimer(pagamento)

  function abrirCheckout() {
    if (!checkoutAtivoAtual || !pagamento?.checkoutUrl) {
      setTipoMensagem('error')
      setMensagem('Checkout expirado ou indisponivel. Gere uma nova cobranca para continuar.')
      return
    }
    const novaGuia = window.open('about:blank', '_blank')
    if (novaGuia) {
      novaGuia.opener = null
      novaGuia.location.href = pagamento.checkoutUrl
      return
    }
    window.location.href = pagamento.checkoutUrl
  }

  async function verificarStatus() {
    const sessionId = pagamento?.stripeSessionId
    if (!sessionId) {
      setTipoMensagem('error')
      setMensagem('Não foi possível verificar o pagamento. Tente novamente ou acesse o link de pagamento novamente.')
      return
    }
    setMensagem('')
    setTipoMensagem('')
    setCarregando(true)
    try {
      const resultado = await appApi.verificarPagamentoPublico(sessionId)
      const atualizado = { ...pendente, pagamentoPlano: { ...pendente.pagamentoPlano, status: resultado.statusVerificacao === 'APPROVED' ? 'PAYMENT_APPROVED' : pendente.pagamentoPlano.status } }
      setPendente(atualizado)
      setTipoMensagem(resultado.statusVerificacao === 'APPROVED' ? 'success' : resultado.statusVerificacao === 'PENDING' ? 'info' : 'error')
      setMensagem(resultado.mensagem || mensagemPadrao(resultado.statusVerificacao === 'APPROVED' ? 'PAYMENT_APPROVED' : pendente.pagamentoPlano.status))
      if (resultado.statusVerificacao === 'APPROVED') {
        limparPagamentoPendente()
        setTimeout(() => navigate('/login'), 1800)
      }
    } catch (error) {
      setTipoMensagem('error')
      setMensagem(error.response?.data?.mensagem || 'Não foi possível verificar o pagamento. Tente novamente.')
    } finally {
      setCarregando(false)
    }
  }

  async function gerarCheckout() {
    const empresaId = pagamento?.empresaId || pendente?.assinatura?.empresaId
    if (!empresaId) {
      setTipoMensagem('error')
      setMensagem('Nao encontramos uma conta pendente neste navegador. Entre novamente para continuar.')
      return
    }
    setMensagem('')
    setTipoMensagem('')
    setGerando(true)

    const novaGuia = window.open('about:blank', '_blank')
    if (novaGuia) {
      novaGuia.opener = null
    }

    try {
       const novoPagamento = await appApi.iniciarPagamentoPro({
         empresaId,
         metodoPagamento: 'CREDIT_CARD',
         plano: pendente?.assinatura?.planoNome || 'PRO',
       })
       if (novoPagamento.checkoutUrl) {
         if (novaGuia && !novaGuia.closed) {
           novaGuia.location.href = novoPagamento.checkoutUrl
           return
         }
         window.location.href = novoPagamento.checkoutUrl
       } else {
         if (novaGuia && !novaGuia.closed) {
           novaGuia.close()
         }
         setTipoMensagem('error')
         setMensagem('Não foi possível gerar o link de pagamento. Tente novamente.')
       }
    } catch (error) {
      if (novaGuia && !novaGuia.closed) {
        novaGuia.close()
      }
      setTipoMensagem('error')
      setMensagem(error.response?.data?.mensagem || 'Nao foi possivel gerar o checkout. Tente novamente em instantes.')
    } finally {
      setGerando(false)
    }
  }

  if (!pendente) {
    return (
      <main className="payment-page">
        <section className="payment-pro-card payment-empty-card">
          <img src={logoWhite} alt="gendaz" className="payment-pro-logo" />
          <h1>Nenhum pagamento pendente</h1>
          <p>Entre na sua conta para consultar o status do plano.</p>
          <Link to="/login" className="btn btn-primary">Entrar</Link>
        </section>
      </main>
    )
  }

  return (
    <main className="payment-page">
      <section className="payment-pro-card">
        <img src={logoWhite} alt="gendaz" className="payment-pro-logo" />

        <span className="payment-plan-badge">Plano {pagamento?.planoNome || 'Pro'}</span>
        <h1>{aprovado ? 'Pagamento aprovado' : 'Finalize seu pagamento'}</h1>
        <p className="payment-pro-copy">
          {aprovado
            ? 'Sua conta foi liberada. Voce ja pode entrar no sistema.'
            : 'Seu cadastro foi criado com sucesso. Para liberar sua conta, conclua o pagamento no checkout seguro.'}
        </p>

        <div className="payment-steps" aria-label="Etapas do pagamento">
          <div className="payment-step done">
            <span><Check size={22} /></span>
            <strong>1. Cadastro criado</strong>
          </div>
          <div className="payment-step active">
            <span>2</span>
            <strong>2. Pagamento</strong>
          </div>
          <div className={aprovado ? 'payment-step done' : 'payment-step'}>
            <span>{aprovado ? <Check size={22} /> : '3'}</span>
            <strong>3. Conta liberada</strong>
          </div>
        </div>

        <div className={`payment-status-card ${statusAtual.tone}`}>
          <div className="payment-status-icon">
            <Clock3 size={24} />
          </div>
          <div>
            <span>Status do pagamento</span>
            <strong>{statusAtual.label}</strong>
          </div>
          <em>{statusAtual.label}</em>
        </div>

        <div className="payment-checkout-card">
          <CreditCard size={38} />
          <div>
            <strong>Pagamento</strong>
            <h2>Checkout seguro da Stripe</h2>
            <p>Finalize o pagamento direto no checkout da Stripe. Os dados da conta ja seguem vinculados pela sessao.</p>
          </div>

          {checkoutAtivoAtual && (
            <div className="checkout-container">
              <Button type="button" onClick={abrirCheckout} disabled={aprovado}>
                <LockKeyhole size={20} /> Ir para pagamento
              </Button>
              {timerCheckout.tempoRestante !== null && (
                <span className="checkout-timer">Expira em: {timerCheckout.formatado}</span>
              )}
            </div>
          )}

          {pagamento?.checkoutUrl && checkoutExpiradoAtual && (
            <small className="plan-checkout-expired-note">Checkout expirado. Gere uma nova cobranca para continuar.</small>
          )}

          <Button type="button" variant="secondary" onClick={verificarStatus} disabled={carregando}>
            <RefreshCw size={20} /> {carregando ? 'Verificando...' : 'Ja paguei, verificar'}
          </Button>

          {checkoutExpiradoAtual && (
            <Button type="button" variant="secondary" onClick={gerarCheckout} disabled={gerando}>
              <RefreshCw size={20} /> {gerando ? 'Gerando...' : 'Gerar nova cobranca'}
            </Button>
          )}

          <Link to="/" className="payment-back-link">Voltar ao site</Link>
        </div>

        <div className="payment-info-card">
          <Info size={20} />
          <span>Finalize no checkout seguro da Stripe. Apos a aprovacao, sua conta pode levar ate 15 minutos para ser liberada.</span>
        </div>

        {mensagem && <div className={`payment-feedback ${tipoMensagem}`}>{mensagem}</div>}
      </section>
    </main>
  )
}
