import { Copy, CreditCard, LockKeyhole, RefreshCw, LogOut, QrCode, AlertCircle, MessageSquare } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useEffect, useRef, useState } from 'react'
import Button from '../components/Button.jsx'
import { appApi } from '../api/appApi.js'
import { useAuth } from '../contexts/AuthContext.jsx'
import logoGendaz from '../assets/logos/gendaz-logo-branco.png'
import { checkoutAtivo, checkoutExpirado, limparInicioCheckout, registrarInicioCheckout } from '../utils/checkoutUtils.js'

const statusView = {
  PAYMENT_PENDING: { label: 'Pagamento pendente', tone: 'pending' },
  PAYMENT_APPROVED: { label: 'Pagamento aprovado', tone: 'approved' },
  PAYMENT_REJECTED: { label: 'Pagamento recusado', tone: 'danger' },
  PAYMENT_CANCELED: { label: 'Pagamento cancelado', tone: 'danger' },
  PAYMENT_EXPIRED: { label: 'Pagamento expirado', tone: 'danger' },
}

const WHATSAPP_URL = 'https://wa.me/5565993360300?text=Ol%C3%A1%2C%20minha%20conta%20foi%20suspensa%20e%20gostaria%20de%20mais%20informa%C3%A7%C3%B5es.'

export default function ContaInativa() {
  const navigate = useNavigate()
  const { usuario, atualizarUsuario, logout } = useAuth()
  const [assinatura, setAssinatura] = useState(null)
  const [pagamento, setPagamento] = useState(null)
  const [mensagem, setMensagem] = useState('')
  const [tipoMensagem, setTipoMensagem] = useState('')
  const [carregando, setCarregando] = useState(true)
  const [gerando, setGerando] = useState(false)
  const [copiado, setCopiado] = useState(false)
  const [planoSelecionado, setPlanoSelecionado] = useState(() => String(usuario?.plano || usuario?.assinatura?.planoNome || 'BASICO').toUpperCase())
  const carregadoParaEmpresaRef = useRef(null)

  const motivoInatividade = usuario?.motivoInatividade || 'PAGAMENTO_PENDENTE'
  const isAdminSuspensao = motivoInatividade === 'ADMIN_SUSPENSAO'
  const checkoutAtivoAtual = checkoutAtivo(pagamento)
  const checkoutExpiradoAtual = checkoutExpirado(pagamento)

  useEffect(() => {
    let ativo = true
    async function carregar() {
      if (!usuario?.empresaId) {
        if (ativo) setCarregando(false)
        return
      }
      if (carregadoParaEmpresaRef.current === usuario.empresaId) {
        if (ativo) setCarregando(false)
        return
      }
      try {
        const assinaturaAtual = await appApi.consultarPlanoAtual(usuario.empresaId, { skipUsuarioHeader: true })

        if (!ativo) return
        carregadoParaEmpresaRef.current = usuario.empresaId

        if (assinaturaAtual?.status === 'ATIVA' && !isAdminSuspensao) {
          atualizarUsuario({ statusConta: 'ACTIVE', assinatura: assinaturaAtual, plano: assinaturaAtual.planoNome || usuario.plano, motivoInatividade: null })
          navigate('/sistema/dashboard', { replace: true })
          return
        }

        setAssinatura(assinaturaAtual || null)
      } catch (error) {
        if (!ativo) return
        setMensagem(error?.response?.data?.mensagem || error?.message || 'Não foi possível carregar as informações da conta.')
        setTipoMensagem('error')
      } finally {
        if (ativo) setCarregando(false)
      }
    }
    carregar()
    return () => {
      ativo = false
    }
  }, [navigate, atualizarUsuario, usuario?.empresaId, isAdminSuspensao])

  useEffect(() => {
    if (usuario?.statusConta === 'ACTIVE') {
      navigate('/sistema/dashboard', { replace: true })
    }
  }, [usuario?.statusConta, navigate])

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
      setMensagem('Já existe uma cobrança pendente. Verifique seu pagamento.')
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
      }, { skipUsuarioHeader: true })
      registrarInicioCheckout(novoPagamento)
      salvarPagamento(novoPagamento)
      setTipoMensagem('success')
      setMensagem('Cobrança gerada com sucesso. Conclua o pagamento para reativar a conta.')
    } catch (error) {
      setTipoMensagem('error')
      setMensagem(error?.response?.data?.mensagem || error?.message || 'Não foi possível gerar o pagamento. Tente novamente em instantes.')
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
        atualizarUsuario({ statusConta: 'ACTIVE', assinatura: resultado.assinatura, plano: resultado.assinatura?.planoNome || planoSelecionado, motivoInatividade: null })
        setTimeout(() => navigate('/sistema/dashboard', { replace: true }), 1800)
      }
    } catch (error) {
      setTipoMensagem('error')
      setMensagem(error?.response?.data?.mensagem || error?.message || 'Não encontramos um pagamento aprovado para esta conta.')
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

  function trocarPlano(event) {
    const novoPlano = String(event.target.value || 'BASICO').toUpperCase()
    setPlanoSelecionado(novoPlano)
  }

  function sairDaConta() {
    logout('manual')
    navigate('/login', { replace: true })
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
          <p>Aguarde alguns instantes enquanto verificamos sua assinatura.</p>
        </section>
      </main>
    )
  }

  return (
    <main className="payment-page">
      <section className="payment-pro-card">
        <img src={logoGendaz} alt="gendaz" className="payment-pro-logo" />

        {isAdminSuspensao ? (
          <>
            <span className="payment-plan-badge" style={{ background: 'rgba(220, 38, 38, 0.14)', borderColor: 'rgba(220, 38, 38, 0.26)', color: '#fca5a5' }}>Conta suspensa</span>
            <h1>Conta suspensa</h1>
            <p className="payment-pro-copy">
              Sua conta foi suspensa pelo administrador. Entre em contato com o suporte para mais informações.
            </p>

            <div className="payment-status-card danger">
              <div className="payment-status-icon">
                <AlertCircle size={24} />
              </div>
              <div>
                <span>Motivo</span>
                <strong>Suspensão administrativa</strong>
              </div>
            </div>

            <div className="payment-checkout-card" style={{ textAlign: 'center' }}>
              <MessageSquare size={48} style={{ color: 'var(--color-primary)', marginBottom: '16px' }} />
              <strong style={{ display: 'block', fontSize: '22px', marginBottom: '8px' }}>Fale com o suporte</strong>
              <p style={{ color: 'rgba(255, 255, 255, 0.68)', marginBottom: '24px' }}>
                Nossa equipe está à disposição para ajudar a resolver sua situação.
              </p>
              <a href={WHATSAPP_URL} target="_blank" rel="noopener noreferrer" className="btn btn-primary" style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', textDecoration: 'none' }}>
                <MessageSquare size={20} /> Suporte via WhatsApp
              </a>
            </div>

            <Button type="button" className="payment-logout-button" onClick={sairDaConta}>
              <LogOut size={18} /> Sair da conta
            </Button>
          </>
        ) : (
          <>
            <span className="payment-plan-badge">Conta inativa</span>
            <h1>Conta inativa</h1>
            <p className="payment-pro-copy">
              Escolha uma opção para reativar sua conta:
            </p>

            <div className="inactive-account-cards">
              <div className="inactive-account-card">
                <CreditCard size={24} />
                <div style={{ width: '100%' }}>
                  <h3>Escolher Plano</h3>
                  <div className="field">
                    <select value={planoSelecionado} onChange={trocarPlano} style={{ width: '100%' }}>
                      <option value="BASICO">Básico</option>
                      <option value="PRO">Pro</option>
                    </select>
                  </div>
                </div>
              </div>

              <div className="inactive-account-card" onClick={gerarPagamento} style={{ cursor: gerando ? 'wait' : 'pointer' }}>
                <LockKeyhole size={24} />
                <h3>Gerar Pagamento</h3>
              </div>

              <div className="inactive-account-card" onClick={sairDaConta}>
                <LogOut size={24} />
                <h3>Sair da Conta</h3>
              </div>
            </div>

            {pagamento && (
              <div className="inactive-account-actions">
                {checkoutAtivoAtual && (
                  <Button type="button" variant="secondary" icon={RefreshCw} onClick={abrirCheckout} style={{ width: '100%' }}>
                    Abrir checkout
                  </Button>
                )}
                {pagamento?.checkoutUrl && checkoutExpiradoAtual && (
                  <small className="plan-checkout-expired-note" style={{ display: 'block', textAlign: 'center' }}>Checkout expirado. Gere um novo pagamento.</small>
                )}
                {pagamento?.id && (
                  <Button type="button" variant="secondary" icon={RefreshCw} onClick={verificarPagamento} disabled={carregando} style={{ width: '100%' }}>
                    {carregando ? 'Verificando...' : 'Já paguei, verificar'}
                  </Button>
                )}
                {(pagamento?.pixCopiaECola || pagamento?.pixQrCodeBase64) && (
                  <div className="payment-pix-card" style={{ width: '100%' }}>
                    <div className="payment-pix-header">
                      <QrCode size={22} />
                      <strong>Pagamento PIX</strong>
                    </div>
                    <div className="payment-pix-body">
                      {pagamento?.pixQrCodeBase64 && (
                        <img className="pix-qr-image large" src={`data:image/png;base64,${pagamento.pixQrCodeBase64}`} alt="QR Code PIX" />
                      )}
                      <div className="payment-pix-copy">
                        <small>{pagamento?.pixCopiaECola || 'O código PIX será exibido aqui quando a cobrança for gerada.'}</small>
                        {pagamento?.pixCopiaECola && (
                          <button type="button" className="btn btn-secondary" onClick={copiarPix} style={{ marginTop: '8px' }}>
                            <Copy size={16} /> {copiado ? 'Código copiado' : 'Copiar código PIX'}
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}

            {mensagem && <div className={`payment-feedback ${tipoMensagem}`}>{mensagem}</div>}
          </>
        )}
      </section>
    </main>
  )
}