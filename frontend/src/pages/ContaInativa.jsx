import { CreditCard, LockKeyhole, RefreshCw, LogOut, AlertCircle, MessageSquare, ExternalLink } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useEffect, useRef, useState } from 'react'
import { appApi } from '../api/appApi.js'
import { useAuth } from '../contexts/AuthContext.jsx'
import logoGendaz from '../assets/logos/gendaz-logo-branco.png'
import { checkoutAtivo, checkoutExpirado } from '../utils/checkoutUtils.js'

const statusView = {
  PAYMENT_PENDING: { label: 'Pagamento pendente', tone: 'pending' },
  PAYMENT_APPROVED: { label: 'Pagamento aprovado', tone: 'approved' },
  PAYMENT_REJECTED: { label: 'Pagamento recusado', tone: 'danger' },
  PAYMENT_CANCELED: { label: 'Pagamento cancelado', tone: 'danger' },
  PAYMENT_EXPIRED: { label: 'Pagamento expirado', tone: 'danger' },
}

const WHATSAPP_URL = 'mailto:contato@gendaz.site?subject=Conta%20inativa&body=Ol%C3%A1%2C%20minha%20conta%20foi%20suspensa%20e%20gostaria%20de%20mais%20informa%C3%A7%C3%B5es.'

export default function ContaInativa() {
  const navigate = useNavigate()
  const { usuario, atualizarUsuario, logout } = useAuth()
  const [assinatura, setAssinatura] = useState(null)
  const [pagamento, setPagamento] = useState(null)
  const [mensagem, setMensagem] = useState('')
  const [tipoMensagem, setTipoMensagem] = useState('')
  const [carregando, setCarregando] = useState(true)
  const [gerando, setGerando] = useState(false)
  const [verificando, setVerificando] = useState(false)
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
          if (error.response?.status !== 404) {
            setMensagem('Não foi possível carregar as informações do plano. Tente novamente.')
            setTipoMensagem('error')
          }
       } finally {
         if (ativo) setCarregando(false)
       }
    }
    carregar()
    return () => {
      ativo = false
    }
  }, [navigate, atualizarUsuario, usuario?.empresaId, isAdminSuspensao])

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
        metodoPagamento: 'CREDIT_CARD',
        plano: planoSelecionado,
      }, { skipUsuarioHeader: true })
      salvarPagamento(novoPagamento)
      setTipoMensagem('success')
      setMensagem('Cobrança gerada com sucesso. Conclua o pagamento para reativar a conta.')
       } catch (error) {
         setTipoMensagem('error')
         setMensagem('Não foi possível iniciar o pagamento. Tente novamente.');
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
     setVerificando(true)
     try {
       const resultado = await appApi.verificarPagamentoPlano(usuario.empresaId, pagamento.id, { skipUsuarioHeader: true })
       salvarPagamento(resultado.pagamento)
       setAssinatura((atual) => resultado.assinatura || atual)
       setTipoMensagem(resultado.statusVerificacao === 'APPROVED' ? 'success' : resultado.statusVerificacao === 'PENDING' ? 'info' : 'error')
       setMensagem(resultado.mensagem || 'Status atualizado com sucesso.')
       if (resultado.statusVerificacao === 'APPROVED') {
         atualizarUsuario({ statusConta: 'ACTIVE', assinatura: resultado.assinatura, plano: resultado.assinatura?.planoNome || planoSelecionado, motivoInatividade: null })
         setTimeout(() => navigate('/sistema/dashboard', { replace: true }), 1800)
       }
        } catch (error) {
          setTipoMensagem('error')
          setMensagem('Não foi possível verificar o pagamento. Tente novamente.');
     } finally {
       setVerificando(false)
     }
  }

  async function abrirCheckout() {
    if (!checkoutAtivoAtual) {
      setTipoMensagem('error')
      setMensagem('Checkout expirado ou indisponível. Gere um novo pagamento para continuar.')
      return
    }
    if (pagamento.checkoutUrl) {
      window.open(pagamento.checkoutUrl, '_blank', 'noopener,noreferrer')
    }
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
      <main className="login-screen-v2 conta-inativa-screen">
        <section className="login-card-v2 conta-inativa-card conta-inativa-empty">
          <div className="conta-inativa-brand">
            <img src={logoGendaz} alt="gendaz" className="login-brand-logo" />
          </div>
          <h1 className="conta-inativa-title">Carregando conta inativa</h1>
          <p className="conta-inativa-copy">Aguarde alguns instantes enquanto verificamos sua assinatura.</p>
        </section>
      </main>
    )
  }

  return (
    <main className="login-screen-v2 conta-inativa-screen">
      <section className="login-card-v2 conta-inativa-card">
        <div className="conta-inativa-brand">
          <img src={logoGendaz} alt="gendaz" className="login-brand-logo" />
        </div>

        {isAdminSuspensao ? (
          <>
            <span className="conta-inativa-badge" style={{ background: 'rgba(220, 38, 38, 0.14)', borderColor: 'rgba(220, 38, 38, 0.26)', color: '#fca5a5' }}>Conta suspensa</span>
            <h1 className="conta-inativa-title">Conta suspensa</h1>
            <p className="conta-inativa-copy">
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
              <MessageSquare size={48} style={{ color: '#ff5e29', marginBottom: '16px' }} />
              <strong style={{ display: 'block', fontSize: '22px', marginBottom: '8px' }}>Fale com o suporte</strong>
              <p style={{ color: 'rgba(255, 255, 255, 0.68)', marginBottom: '24px' }}>
                Nossa equipe está à disposição para ajudar a resolver sua situação.
              </p>
              <a href={WHATSAPP_URL} target="_blank" rel="noopener noreferrer" className="btn btn-primary" style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', textDecoration: 'none' }}>
                <MessageSquare size={20} /> Suporte via e-mail
              </a>
            </div>

            <button type="button" className="inactive-account-card white" onClick={sairDaConta}>
              <LogOut size={18} className="inactive-account-icon" />
              <span className="inactive-account-label">Sair da conta</span>
            </button>
          </>
        ) : (
          <>
            <span className="conta-inativa-badge">Conta inativa</span>
            <h1 className="conta-inativa-title">Conta inativa</h1>
            <p className="conta-inativa-copy">
              Escolha uma opção para reativar sua conta:
            </p>

            <div className="inactive-account-cards">
              <div className="inactive-account-card orange">
                <CreditCard size={18} className="inactive-account-icon" />
                <div className="inactive-account-body">
                  <span className="inactive-account-label">Escolher Plano</span>
                  <div className="inactive-account-select">
                    <select value={planoSelecionado} onChange={trocarPlano} aria-label="Plano">
                      <option value="BASICO">Básico</option>
                      <option value="PRO">Pro</option>
                    </select>
                  </div>
                </div>
              </div>

              <button type="button" className="inactive-account-card white" onClick={gerarPagamento} disabled={gerando}>
                {gerando ? <RefreshCw size={18} className="inactive-account-icon animate-spin" /> : <LockKeyhole size={18} className="inactive-account-icon" />}
                <span className="inactive-account-label">{gerando ? 'Gerando pagamento...' : 'Gerar Pagamento'}</span>
              </button>

              <button type="button" className="inactive-account-card orange" onClick={sairDaConta}>
                <LogOut size={18} className="inactive-account-icon" />
                <span className="inactive-account-label">Sair da Conta</span>
              </button>
            </div>

            {pagamento && (
              <div className="inactive-account-actions">
                {checkoutAtivoAtual && (
                  <button type="button" className="inactive-account-card white" onClick={abrirCheckout}>
                    <ExternalLink size={18} className="inactive-account-icon" />
                    <span className="inactive-account-label">Abrir checkout</span>
                  </button>
                )}
                {pagamento?.checkoutUrl && checkoutExpiradoAtual && (
                  <small className="inactive-account-expired-note">Checkout expirado. Gere um novo pagamento.</small>
                )}
                {pagamento?.id && (
                  <button type="button" className="inactive-account-card orange" onClick={verificarPagamento} disabled={verificando}>
                    <RefreshCw size={18} className={`inactive-account-icon ${verificando ? 'animate-spin' : ''}`} />
                    <span className="inactive-account-label">{verificando ? 'Verificando pagamento...' : 'Já paguei, verificar'}</span>
                  </button>
                )}
              </div>
            )}

            {mensagem && <div className={`conta-inativa-feedback ${tipoMensagem}`}>{mensagem}</div>}
          </>
        )}
      </section>
    </main>
  )
}
