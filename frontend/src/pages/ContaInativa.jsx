import { Copy, CreditCard, Info, LockKeyhole, RefreshCw, ShieldAlert, QrCode } from 'lucide-react'
import QRCode from 'qrcode'
import { Link, useNavigate } from 'react-router-dom'
import { useEffect, useMemo, useState } from 'react'
import Button from '../components/Button.jsx'
import { appApi } from '../api/appApi.js'
import { useAuth } from '../contexts/AuthContext.jsx'
import logoGendaz from '../assets/logos/gendaz-logo-branco.png'
import { useCheckoutTimer } from '../hooks/useCheckoutTimer.js'
import { checkoutAtivo, checkoutExpirado, limparInicioCheckout, registrarInicioCheckout } from '../utils/checkoutUtils.js'

const statusView = {
  PAYMENT_PENDING: { label: 'Pagamento pendente', tone: 'pending' },
  PAYMENT_APPROVED: { label: 'Pagamento aprovado', tone: 'approved' },
  PAYMENT_REJECTED: { label: 'Pagamento recusado', tone: 'danger' },
  PAYMENT_CANCELED: { label: 'Pagamento cancelado', tone: 'danger' },
  PAYMENT_EXPIRED: { label: 'Pagamento expirado', tone: 'danger' },
}

function formatarMoeda(valor) {
  if (valor == null || Number.isNaN(Number(valor))) return null
  return Number(valor).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function identificarTipoDocumento(documento) {
  const digitos = String(documento || '').replace(/\D/g, '')
  if (digitos.length === 14) return 'cnpj'
  if (digitos.length === 11) return 'cpf'
  return ''
}

function mensagemErroReativacao(error, fallback) {
  const status = error?.response?.status
  const mensagem = String(error?.response?.data?.mensagem || error?.response?.data?.message || '').toLowerCase()
  const sessaoEncerrada = mensagem.includes('sessao foi encerrada')
    || mensagem.includes('sessão foi encerrada')
    || mensagem.includes('acessada em outro dispositivo')
  if (status === 401 && sessaoEncerrada) {
    return fallback
  }
  return error?.response?.data?.mensagem || error?.message || fallback
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

export default function ContaInativa() {
  const navigate = useNavigate()
  const { usuario, atualizarUsuario, logout } = useAuth()
  const [assinatura, setAssinatura] = useState(null)
  const [pagamento, setPagamento] = useState(null)
  const [pagamentosPlano, setPagamentosPlano] = useState([])
  const [mensagem, setMensagem] = useState('')
  const [tipoMensagem, setTipoMensagem] = useState('')
  const [carregando, setCarregando] = useState(true)
  const [gerando, setGerando] = useState(false)
  const [copiado, setCopiado] = useState(false)
  const [qrDataUrl, setQrDataUrl] = useState('')
  const [planoSelecionado, setPlanoSelecionado] = useState(() => String(usuario?.plano || usuario?.assinatura?.planoNome || 'BASICO').toUpperCase())

  const planoAtual = assinatura?.planoNome || usuario?.plano || 'BASICO'
  const statusPagamento = pagamento?.status || 'PAYMENT_PENDING'
  const statusAtual = statusView[statusPagamento] || statusView.PAYMENT_PENDING
  const valoresPlanos = {
    BASICO: pagamento?.plano?.toUpperCase?.() === 'BASICO' ? (pagamento?.valor ?? 0) : (pagamentosPlano.find((item) => String(item?.plano || item?.planoNome || '').toUpperCase() === 'BASICO')?.valor ?? 0),
    PRO: 89,
  }
  const pagamentoDoPlanoSelecionado = useMemo(() => {
    const planoNormalizado = String(planoSelecionado || 'BASICO').toUpperCase()
    return pagamentosPlano.find((item) => String(item?.plano || item?.planoNome || '').toUpperCase() === planoNormalizado) || null
  }, [pagamentosPlano, planoSelecionado])
  const valorPlanoSelecionado = planoSelecionado === 'PRO'
    ? valoresPlanos.PRO
    : pagamentoDoPlanoSelecionado?.valor ?? pagamento?.valor ?? valoresPlanos.BASICO ?? null
  const checkoutAtivoAtual = checkoutAtivo(pagamento)
  const checkoutExpiradoAtual = checkoutExpirado(pagamento)
  const timerCheckout = useCheckoutTimer(pagamento)

  const formularioPadrao = useMemo(() => ({
    customerName: usuario?.nome || usuario?.nomeResponsavel || '',
    customerEmail: usuario?.email || '',
    customerPhone: usuario?.telefone || usuario?.empresa?.telefone || '',
    customerDocType: identificarTipoDocumento(usuario?.documento || usuario?.cpfCnpj || usuario?.empresa?.documento),
    customerDocNumber: usuario?.documento || usuario?.cpfCnpj || usuario?.empresa?.documento || '',
  }), [usuario])

  const [form, setForm] = useState(formularioPadrao)

  useEffect(() => {
    setForm(formularioPadrao)
  }, [formularioPadrao])

  useEffect(() => {
    setPlanoSelecionado(String(usuario?.plano || assinatura?.planoNome || 'BASICO').toUpperCase())
  }, [assinatura?.planoNome, usuario?.plano])

  useEffect(() => {
    let ativo = true
    async function carregar() {
      if (!usuario?.empresaId) {
        setCarregando(false)
        return
      }
      try {
        const [assinaturaAtual, pagamentosPlano] = await Promise.all([
          appApi.consultarPlanoAtual(usuario.empresaId, { skipUsuarioHeader: true }),
          appApi.listarPagamentosPlano(usuario.empresaId, { skipUsuarioHeader: true }),
        ])

        if (assinaturaAtual?.status === 'ATIVA' || assinaturaAtual?.status === 'TESTE') {
          atualizarUsuario({ statusConta: 'ACTIVE', assinatura: assinaturaAtual, plano: assinaturaAtual.planoNome || usuario.plano })
          navigate('/sistema/dashboard', { replace: true })
          return
        }

        if (!ativo) return
        setAssinatura(assinaturaAtual || null)
        setPagamentosPlano(Array.isArray(pagamentosPlano) ? pagamentosPlano : [])
        const planoInicial = String(usuario?.plano || assinaturaAtual?.planoNome || 'BASICO').toUpperCase()
        setPlanoSelecionado(planoInicial)
        const pendente = (Array.isArray(pagamentosPlano) ? pagamentosPlano : []).find((item) => {
          const planoItem = String(item?.plano || item?.planoNome || '').toUpperCase()
          return item.status === 'PAYMENT_PENDING' && planoItem === planoInicial
        }) || (Array.isArray(pagamentosPlano) ? pagamentosPlano.find((item) => item.status === 'PAYMENT_PENDING') : null) || null
        setPagamento(pendente)
        if (pendente?.pixCopiaECola && !pendente?.pixQrCodeBase64) {
          const qr = await gerarQrCodeDataUrl(pendente.pixCopiaECola)
          if (ativo) setQrDataUrl(qr || '')
        } else if (pendente?.pixCopiaECola && pendente?.pixQrCodeBase64) {
          setQrDataUrl('')
        }
      } catch (error) {
        if (!ativo) return
        setMensagem(mensagemErroReativacao(error, 'Não foi possível carregar as informações da conta.'))
        setTipoMensagem('error')
      } finally {
        if (ativo) setCarregando(false)
      }
    }
    carregar()
    return () => {
      ativo = false
    }
  }, [navigate, atualizarUsuario, usuario, assinatura?.planoNome])

  useEffect(() => {
    let ativo = true
    async function gerarQr() {
      const copia = pagamento?.pixCopiaECola
      if (!copia || pagamento?.pixQrCodeBase64) {
        setQrDataUrl('')
        return
      }
      const dataUrl = await gerarQrCodeDataUrl(copia)
      if (ativo) setQrDataUrl(dataUrl || '')
    }
    gerarQr().catch(() => null)
    return () => {
      ativo = false
    }
  }, [pagamento?.pixCopiaECola, pagamento?.pixQrCodeBase64])

  function salvarPagamento(atualizado) {
    setPagamento(atualizado)
  }

  async function gerarPagamento() {
    if (!usuario?.empresaId) {
      setTipoMensagem('error')
      setMensagem('Não encontramos uma conta ativa neste navegador.')
      return
    }
    if (pagamento?.status === 'PAYMENT_PENDING') {
      setTipoMensagem('info')
      setMensagem('Já existe uma cobrança pendente. Use o pagamento abaixo.')
      return
    }
    setMensagem('')
    setTipoMensagem('')
    setGerando(true)
    try {
      const novoPagamento = await appApi.iniciarPagamentoPlano({
        empresaId: usuario.empresaId,
        metodoPagamento: 'PIX_AUTO',
        plano: planoSelecionado,
        ...form,
      }, { skipUsuarioHeader: true })
      registrarInicioCheckout(novoPagamento)
      salvarPagamento(novoPagamento)
      setPagamentosPlano((atual) => {
        const semAtual = atual.filter((item) => String(item.id) !== String(novoPagamento?.id))
        return [novoPagamento, ...semAtual]
      })
      setTipoMensagem('success')
      setMensagem('Cobrança gerada com sucesso. Conclua o pagamento para reativar a conta.')
    } catch (error) {
      setTipoMensagem('error')
      setMensagem(mensagemErroReativacao(error, 'Não foi possível gerar o pagamento. Tente novamente em instantes.'))
    } finally {
      setGerando(false)
    }
  }

  async function verificarPagamento() {
    if (!usuario?.empresaId || !pagamento?.id) {
      setTipoMensagem('error')
      setMensagem('Não encontramos um pagamento pendente para esta conta.')
      return
    }
    setMensagem('')
    setTipoMensagem('')
    setCarregando(true)
    try {
      const resultado = await appApi.verificarPagamentoPlano(usuario.empresaId, pagamento.id, { skipUsuarioHeader: true })
      salvarPagamento(resultado.pagamento)
      setAssinatura((atual) => resultado.assinatura || atual)
      setTipoMensagem(resultado.statusVerificacao === 'APPROVED' ? 'success' : resultado.statusVerificacao === 'PENDING' ? 'info' : 'error')
      setMensagem(resultado.mensagem || 'Status atualizado com sucesso.')
      if (resultado.statusVerificacao === 'APPROVED') {
        limparInicioCheckout(pagamento)
        atualizarUsuario({ statusConta: 'ACTIVE', assinatura: resultado.assinatura, plano: resultado.assinatura?.planoNome || planoAtual })
        setTimeout(() => navigate('/sistema/dashboard', { replace: true }), 1800)
      }
    } catch (error) {
      setTipoMensagem('error')
      setMensagem(mensagemErroReativacao(error, 'Não encontramos um pagamento aprovado para esta conta.'))
    } finally {
      setCarregando(false)
    }
  }
  async function abrirCheckout() {
    if (!checkoutAtivoAtual) {
      setTipoMensagem('error')
      setMensagem('Checkout expirado ou indisponível. Gere um novo pagamento para continuar.')
      return
    }
    window.open(pagamento.checkoutUrl, '_blank', 'noopener,noreferrer')
  }

  async function copiarPix() {
    if (!pagamento?.pixCopiaECola) return
    await navigator.clipboard.writeText(pagamento.pixCopiaECola)
    setCopiado(true)
    setTimeout(() => setCopiado(false), 2500)
  }

  function sairDaConta() {
    logout('manual')
    navigate('/login', { replace: true })
  }

  function trocarPlano(event) {
    const novoPlano = String(event.target.value || 'BASICO').toUpperCase()
    setPlanoSelecionado(novoPlano)
    const pagamentoDoPlano = pagamentosPlano.find((item) => {
      const planoItem = String(item?.plano || item?.planoNome || '').toUpperCase()
      return item.status === 'PAYMENT_PENDING' && planoItem === novoPlano
    }) || null
    setPagamento(pagamentoDoPlano)
    setQrDataUrl('')
  }

  if (!usuario) {
    return null
  }

  if (carregando && !assinatura) {
    return (
      <main className="payment-page">
        <section className="payment-pro-card payment-empty-card">
          <img src={logoGendaz} alt="gendaz" className="payment-pro-logo" />
          <h1>Carregando conta inativa</h1>
          <p>Aguarde alguns instantes enquanto verificamos sua assinatura e seu pagamento.</p>
        </section>
      </main>
    )
  }

  return (
    <main className="payment-page">
      <section className="payment-pro-card">
        <img src={logoGendaz} alt="gendaz" className="payment-pro-logo" />

        <span className="payment-plan-badge">Conta inativa</span>
        <h1>Conta inativa</h1>
        <p className="payment-pro-copy">
          Seu período gratuito terminou. Para continuar usando o gendaz, regularize sua mensalidade.
        </p>

        <div className="payment-status-card danger">
          <div className="payment-status-icon">
            <ShieldAlert size={24} />
          </div>
          <div>
            <span>Status da conta</span>
            <strong>Conta inativa</strong>
          </div>
          <em>{planoSelecionado}{valorPlanoSelecionado ? ` · ${formatarMoeda(valorPlanoSelecionado)}` : ''}</em>
        </div>

        <div className="payment-checkout-card">
          <CreditCard size={38} />
          <div>
            <strong>Regularização</strong>
            <h2>Gerar pagamento</h2>
            <p>Use o fluxo de pagamento já existente para reativar sua conta.</p>
          </div>

          <div className="payment-client-grid single-row">
            <label>
              <span>Plano</span>
              <select value={planoSelecionado} onChange={trocarPlano}>
                <option value="BASICO">Básico</option>
                <option value="PRO">Pro</option>
              </select>
            </label>
            <div className="payment-status-card">
              <div>
                <span>Valor do plano</span>
                <strong>{valorPlanoSelecionado ? formatarMoeda(valorPlanoSelecionado) : 'Selecione um plano'}</strong>
              </div>
              <em>{planoSelecionado}</em>
            </div>
          </div>

          <div className="payment-client-grid">
            <label>
              <span>Nome</span>
              <input type="text" value={form.customerName} onChange={(e) => setForm((atual) => ({ ...atual, customerName: e.target.value }))} />
            </label>
            <label>
              <span>E-mail</span>
              <input type="email" value={form.customerEmail} onChange={(e) => setForm((atual) => ({ ...atual, customerEmail: e.target.value }))} />
            </label>
            <label>
              <span>Telefone</span>
              <input type="text" value={form.customerPhone} onChange={(e) => setForm((atual) => ({ ...atual, customerPhone: e.target.value }))} />
            </label>
            <label>
              <span>Documento</span>
              <input type="text" value={form.customerDocNumber} onChange={(e) => setForm((atual) => ({ ...atual, customerDocNumber: e.target.value }))} />
            </label>
          </div>

          <div className="payment-client-grid single-row">
            <label>
              <span>Tipo do documento</span>
              <select value={form.customerDocType} onChange={(e) => setForm((atual) => ({ ...atual, customerDocType: e.target.value }))}>
                <option value="">Selecione</option>
                <option value="cpf">CPF</option>
                <option value="cnpj">CNPJ</option>
              </select>
            </label>
          </div>

          <Button type="button" icon={LockKeyhole} onClick={gerarPagamento} disabled={gerando}>
            {gerando ? 'Gerando...' : 'Gerar pagamento'}
          </Button>

          {checkoutAtivoAtual && (
            <div className="checkout-container">
              <Button type="button" variant="secondary" icon={RefreshCw} onClick={abrirCheckout}>
                Abrir checkout
              </Button>
              {timerCheckout.tempoRestante !== null && (
                <span className="checkout-timer">Expira em: {timerCheckout.formatado}</span>
              )}
            </div>
          )}
          {pagamento?.checkoutUrl && checkoutExpiradoAtual && (
            <small className="plan-checkout-expired-note">Checkout expirado. Gere um novo pagamento para continuar.</small>
          )}

          {pagamento?.id && (
            <Button type="button" variant="secondary" icon={RefreshCw} onClick={verificarPagamento} disabled={carregando}>
              {carregando ? 'Verificando...' : 'Já paguei, verificar'}
            </Button>
          )}
          <Button type="button" className="payment-logout-button" onClick={sairDaConta}>
            Sair da conta
          </Button>

          <Link to="/" className="payment-back-link">← Voltar ao site</Link>
        </div>

        {(pagamento?.pixCopiaECola || pagamento?.pixQrCodeBase64 || qrDataUrl) && (
          <div className="payment-pix-card">
            <div className="payment-pix-header">
              <QrCode size={22} />
              <strong>Pagamento PIX</strong>
            </div>
            <div className="payment-pix-body">
              {pagamento?.pixQrCodeBase64 ? (
                <img className="pix-qr-image large" src={`data:image/png;base64,${pagamento.pixQrCodeBase64}`} alt="QR Code PIX" />
              ) : qrDataUrl ? (
                <img className="pix-qr-image large" src={qrDataUrl} alt="QR Code PIX" />
              ) : null}
              <div className="payment-pix-copy">
                <small>{pagamento?.pixCopiaECola || 'O código PIX será exibido aqui quando a cobrança for gerada.'}</small>
                {pagamento?.dataExpiracao && <small>Vencimento: {new Date(pagamento.dataExpiracao).toLocaleString('pt-BR')}</small>}
                {pagamento?.pixCopiaECola && (
                  <button type="button" className="btn btn-secondary" onClick={copiarPix}>
                    <Copy size={16} /> {copiado ? 'Código copiado' : 'Copiar código PIX'}
                  </button>
                )}
              </div>
            </div>
          </div>
        )}

        <div className="payment-info-card">
          <Info size={20} />
          <span>Após a aprovação, sua conta pode levar até 15 minutos para ser liberada.</span>
        </div>

        {mensagem && <div className={`payment-feedback ${tipoMensagem}`}>{mensagem}</div>}
      </section>
    </main>
  )
}
