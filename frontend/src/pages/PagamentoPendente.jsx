import { Check, Clock3, Copy, CreditCard, Info, LockKeyhole, RefreshCw } from 'lucide-react'
import QRCode from 'qrcode'
import { Link, useNavigate } from 'react-router-dom'
import { useEffect, useState } from 'react'
import Button from '../components/Button.jsx'
import { appApi } from '../api/appApi.js'
import { useAuth } from '../contexts/AuthContext.jsx'
import logoWhite from '../assets/logos/gendaz-logo-branco.png'
import { useCheckoutTimer } from '../hooks/useCheckoutTimer.js'
import { checkoutAtivo, checkoutExpirado, limparInicioCheckout, registrarInicioCheckout } from '../utils/checkoutUtils.js'

const statusView = {
  PAYMENT_PENDING: { label: 'Aguardando pagamento', tone: 'pending' },
  PAYMENT_APPROVED: { label: 'Pagamento aprovado', tone: 'approved' },
  PAYMENT_REJECTED: { label: 'Pagamento recusado', tone: 'danger' },
  PAYMENT_CANCELED: { label: 'Pagamento cancelado', tone: 'danger' },
  PAYMENT_EXPIRED: { label: 'Pagamento expirado', tone: 'danger' },
}

function mensagemPadrao(status) {
  if (status === 'PAYMENT_APPROVED') return 'Pagamento aprovado! Sua conta Pro foi liberada.'
  if (status === 'PAYMENT_REJECTED') return 'Pagamento recusado. Gere uma nova cobrança e tente novamente.'
  if (status === 'PAYMENT_CANCELED') return 'Pagamento cancelado. Gere uma nova cobrança para continuar.'
  if (status === 'PAYMENT_EXPIRED') return 'Pagamento expirado. Gere uma nova cobrança para continuar.'
  return 'Pagamento ainda não foi confirmado. Aguarde alguns minutos e tente novamente.'
}

async function gerarQrCodeDataUrl(texto) {
  if (!texto) return null
  return QRCode.toDataURL(texto, {
    errorCorrectionLevel: 'M',
    margin: 1,
    width: 360,
    color: {
      dark: '#111111',
      light: '#FFFFFF',
    },
  })
}

export default function PagamentoPendente() {
  const navigate = useNavigate()
  const { getPagamentoPendente, limparPagamentoPendente } = useAuth()
  const [pendente, setPendente] = useState(() => getPagamentoPendente())
  const [mensagem, setMensagem] = useState('')
  const [tipoMensagem, setTipoMensagem] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [gerando, setGerando] = useState(false)
  const [copiado, setCopiado] = useState(false)
  const [qrDataUrl, setQrDataUrl] = useState('')

  const pagamento = pendente?.pagamentoPlano
  const usuarioPendente = pendente?.usuario || null
  const status = pagamento?.status || 'PAYMENT_PENDING'
  const statusAtual = statusView[status] || statusView.PAYMENT_PENDING
  const aprovado = status === 'PAYMENT_APPROVED'
  const precisaNovaCobranca = ['PAYMENT_REJECTED', 'PAYMENT_CANCELED', 'PAYMENT_EXPIRED'].includes(status)
  const checkoutAtivoAtual = checkoutAtivo(pagamento)
  const checkoutExpiradoAtual = checkoutExpirado(pagamento)
  const timerCheckout = useCheckoutTimer(pagamento)

  useEffect(() => {
    let ativo = true
    async function gerarQr() {
      const copia = pagamento?.pixCopiaECola
      if (!copia || pagamento?.pixQrCodeBase64) {
        setQrDataUrl('')
        return
      }
      const dataUrl = await gerarQrCodeDataUrl(copia)
      if (ativo) {
        setQrDataUrl(dataUrl || '')
      }
    }
    gerarQr().catch(() => null)
    return () => {
      ativo = false
    }
  }, [pagamento?.pixCopiaECola, pagamento?.pixQrCodeBase64])

  function salvarPendente(atualizado) {
    setPendente(atualizado)
    localStorage.setItem('agendeasy_pagamento_pendente', JSON.stringify(atualizado))
  }

  function abrirCheckout() {
    if (!checkoutAtivoAtual) {
      setTipoMensagem('error')
      setMensagem('Checkout expirado ou indisponível. Gere uma nova cobrança para continuar.')
      return
    }
    window.open(pagamento.checkoutUrl, '_blank', 'noopener,noreferrer')
  }

  async function verificarStatus() {
    if (!pagamento?.empresaId || !pagamento?.id) {
      setTipoMensagem('error')
      setMensagem('Não encontramos um pagamento aprovado para esta conta.')
      return
    }
    setMensagem('')
    setTipoMensagem('')
    setCarregando(true)
    try {
      const resultado = await appApi.verificarPagamentoPlano(pagamento.empresaId, pagamento.id)
      const atualizado = { ...pendente, pagamentoPlano: resultado.pagamento, assinatura: resultado.assinatura || pendente?.assinatura }
      salvarPendente(atualizado)
      setTipoMensagem(resultado.statusVerificacao === 'APPROVED' ? 'success' : resultado.statusVerificacao === 'PENDING' ? 'info' : 'error')
      setMensagem(resultado.mensagem || mensagemPadrao(resultado.pagamento?.status))
      if (resultado.statusVerificacao === 'APPROVED') {
        limparInicioCheckout(pagamento)
        limparPagamentoPendente()
        setTimeout(() => navigate('/login'), 1800)
      }
    } catch (error) {
      setTipoMensagem('error')
      setMensagem(error.response?.data?.mensagem || 'Não encontramos um pagamento aprovado para esta conta.')
    } finally {
      setCarregando(false)
    }
  }

  async function gerarCheckout() {
    const empresaId = pagamento?.empresaId || pendente?.assinatura?.empresaId
    if (!empresaId) {
      setTipoMensagem('error')
      setMensagem('Não encontramos uma conta pendente neste navegador. Entre novamente para continuar.')
      return
    }
    setMensagem('')
    setTipoMensagem('')
    setGerando(true)
    try {
      const novoPagamento = await appApi.iniciarPagamentoPro({
        empresaId,
        metodoPagamento: 'PIX_AUTO',
        plano: pendente?.assinatura?.planoNome || 'PRO',
      })
      registrarInicioCheckout(novoPagamento)
      const atualizado = { ...pendente, pagamentoPlano: novoPagamento }
      salvarPendente(atualizado)
      setTipoMensagem('success')
      setMensagem('Cobrança gerada. Abra o pagamento para continuar.')
    } catch (error) {
      setTipoMensagem('error')
      setMensagem(error.response?.data?.mensagem || 'Não foi possível gerar o checkout. Tente novamente em instantes.')
    } finally {
      setGerando(false)
    }
  }

  async function copiarPix() {
    const texto = pagamento?.pixCopiaECola
    if (!texto) return
    await navigator.clipboard.writeText(texto)
    setCopiado(true)
    setTimeout(() => setCopiado(false), 2500)
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

        <span className="payment-plan-badge">Plano Pro</span>
        <h1>{aprovado ? 'Pagamento aprovado' : 'Finalize seu pagamento'}</h1>
        <p className="payment-pro-copy">
          {aprovado
            ? 'Sua conta Pro foi liberada. Você já pode entrar no sistema.'
            : 'Seu cadastro foi criado com sucesso. Para liberar sua conta Pro, conclua o pagamento no checkout seguro.'}
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
            <h2>Checkout seguro da Cakto</h2>
            <p>Finalize o pagamento direto no checkout da Cakto. Os dados da conta já seguem vinculados pela sessão.</p>
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
            <small className="plan-checkout-expired-note">Checkout expirado. Gere uma nova cobrança para continuar.</small>
          )}

          <Button type="button" variant="secondary" onClick={verificarStatus} disabled={carregando}>
            <RefreshCw size={20} /> {carregando ? 'Verificando...' : 'Já paguei, verificar'}
          </Button>

          {checkoutExpiradoAtual && (
            <Button type="button" variant="secondary" onClick={gerarCheckout} disabled={gerando}>
              <RefreshCw size={20} /> {gerando ? 'Gerando...' : 'Gerar nova cobrança'}
            </Button>
          )}

          <Link to="/" className="payment-back-link">← Voltar ao site</Link>
        </div>

        {(pagamento?.pixCopiaECola || pagamento?.pixQrCodeBase64 || qrDataUrl) && (
          <div className="payment-pix-card">
            <div className="payment-pix-header">
              <QrCode size={22} />
              <strong>PIX automático</strong>
            </div>
            <div className="payment-pix-body">
              {pagamento?.pixQrCodeBase64 ? (
                <img className="pix-qr-image large" src={`data:image/png;base64,${pagamento.pixQrCodeBase64}`} alt="QR Code PIX" />
              ) : qrDataUrl ? (
                <img className="pix-qr-image large" src={qrDataUrl} alt="QR Code PIX" />
              ) : null}
              <div className="payment-pix-copy">
                <small>{pagamento?.pixCopiaECola || 'PIX ainda não retornou o código copia e cola. Tente gerar novamente.'}</small>
                {pagamento?.dataExpiracao && <small>Vencimento: {new Date(pagamento.dataExpiracao).toLocaleString('pt-BR')}</small>}
                {pagamento?.pixCopiaECola && (
                  <button type="button" className="btn btn-secondary" onClick={copiarPix}>
                    <Copy size={16} /> {copiado ? 'Código copiado' : 'Copiar código PIX'}
                  </button>
                )}
              </div>
            </div>
            <small>Apos a aprovacao, sua conta Pro pode levar ate 15 minutos para ser liberada.</small>
          </div>
        )}

        {pagamento?.metodoPagamento === 'CREDIT_CARD' && (
          <div className="payment-info-card">
            <Info size={20} />
            <span>Finalize no checkout seguro da Cakto. Após a aprovação, sua conta Pro pode levar até 15 minutos para ser liberada.</span>
          </div>
        )}

        {mensagem && <div className={`payment-feedback ${tipoMensagem}`}>{mensagem}</div>}
      </section>
    </main>
  )
}
