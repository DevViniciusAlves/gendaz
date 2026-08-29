import { useEffect, useState, useCallback, useContext } from 'react'
import { useNavigate, useParams, useLocation } from 'react-router-dom'
import { ArrowLeft, Loader, LogOut, Mail } from 'lucide-react'
import clienteApi from '../api/clienteApi.js'
import { ClienteGendazContext, ClienteGendazProvider } from '../contexts/ClienteGendazContext.jsx'
import GendazLayout from '../components/gendaz/GendazLayout.jsx'
import { normalizarParaApi, normalizarParaInput, obterExemploTelefone, validarTelefone } from '../utils/phoneUtils.js'
import InternationalPhoneInput from '../components/InternationalPhoneInput.jsx'
import logoMeuGendaz from '../assets/logos/meugendazpngpreto.png'
import OperationToast from '../components/OperationToast.jsx'

const COOLDOWN_SEGUNDOS = 120
const TOAST_LOGOUT_ID = 'meu-gendaz-logout'

function isIosBrowser() {
  if (typeof navigator === 'undefined') return false
  const ua = navigator.userAgent.toLowerCase()
  return /iphone|ipod|ipad/.test(ua)
}

function GendazAuthGate({ slug, onLogin }) {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [codigo, setCodigo] = useState('')
  const [etapa, setEtapa] = useState('email')
  const [nomeEmpresa, setNomeEmpresa] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState('')
  const [reenviarEm, setReenviarEm] = useState(0)
  const [tentativas, setTentativas] = useState(0)
  const [bloqueado, setBloqueado] = useState(false)
  const [ultimoEmailSolicitado, setUltimoEmailSolicitado] = useState('')
  const [codigoSolicitado, setCodigoSolicitado] = useState(false)

  useEffect(() => {
    let ativo = true
    async function carregarEmpresa() {
      try {
        const { data } = await clienteApi.get(`/meu-gendaz/empresa/${slug}`)
        if (!ativo) return
        setNomeEmpresa(data?.nomeFantasia || data?.nome || data?.empresaNome || '')
      } catch {
        if (!ativo) return
        setNomeEmpresa('')
      }
    }

    if (slug) {
      void carregarEmpresa()
    }

    return () => {
      ativo = false
    }
  }, [slug])

  useEffect(() => {
    if (!reenviarEm) return undefined
    const timer = setInterval(() => setReenviarEm((atual) => (atual <= 1 ? 0 : atual - 1)), 1000)
    return () => clearInterval(timer)
  }, [reenviarEm])

  function emailAtualNormalizado() {
    return email.trim().toLowerCase()
  }

  function solicitarEtapaCodigoSemReenviar() {
    setErro('')
    if (!emailAtualNormalizado()) return
    setCodigo('')
    setEtapa('codigo')
  }

  async function solicitarCodigo() {
    setErro('')
    if (!email.trim()) return
    setCarregando(true)
    try {
      await clienteApi.post('/meu-gendaz/auth/solicitar-codigo', { slug, email: email.trim() }, {
        skipMeuGendazLogout: true,
      })
      setUltimoEmailSolicitado(emailAtualNormalizado())
      setCodigoSolicitado(true)
      setEtapa('codigo')
      setReenviarEm(COOLDOWN_SEGUNDOS)
      setTentativas(0)
      setCodigo('')
    } catch (error) {
      const mensagem = error.response?.data?.mensagem || error.response?.data?.message || error.message || 'Nao foi possivel enviar o codigo.'
      const retryAfter = Number(error.response?.headers?.['retry-after'] || 0)
      if (retryAfter > 0) setReenviarEm(retryAfter)
      if (mensagem.toLowerCase().includes('30')) setReenviarEm(30)
      if (mensagem.toLowerCase().includes('120')) setReenviarEm(120)
      if (mensagem.toLowerCase().includes('bloque')) setBloqueado(true)
      setErro(mensagem)
    } finally {
      setCarregando(false)
    }
  }

  async function confirmarCodigo(event) {
    event.preventDefault()
    setErro('')
    if (!email.trim() || !codigo.trim()) return
    setCarregando(true)
    try {
      const response = await clienteApi.post('/meu-gendaz/auth/validar-codigo', {
        slug,
        email: email.trim(),
        codigo: codigo.trim(),
      }, {
        skipMeuGendazLogout: true,
      })
      if (response.data?.mensagem && response.data?.status === 'PENDING_REGISTRATION') {
        await onLogin()
        return
      }
      if (response.data?.mensagem && response.data?.status === 'ACTIVE') {
        if (isIosBrowser()) {
          await new Promise((resolve) => window.setTimeout(resolve, 700))
          navigate(`/meu-gendaz/${slug}/dashboard`, {
            replace: true,
            state: { mobileIosLogin: true },
          })
          return
        }
        await onLogin()
        navigate(`/meu-gendaz/${slug}/dashboard`, { replace: true })
      } else {
        setErro(response.data?.mensagem || 'Nao foi possivel realizar login.')
      }
    } catch (error) {
      const mensagem = error.response?.data?.mensagem || error.response?.data?.message || error.message || 'Codigo invalido.'
      const retryAfter = Number(error.response?.headers?.['retry-after'] || 0)
      if (retryAfter > 0) setReenviarEm(retryAfter)
      setErro(mensagem)
      setTentativas((atual) => {
        const next = atual + 1
        if (next >= 5 || mensagem.toLowerCase().includes('bloque')) setBloqueado(true)
        return next
      })
    } finally {
      setCarregando(false)
    }
  }

  async function reenviarCodigo() {
    if (reenviarEm > 0 || bloqueado) return
    await solicitarCodigo()
  }

  function entrarComEmail(event) {
    event.preventDefault()
    if (bloqueado) return

    const emailNormalizado = emailAtualNormalizado()
    if (!emailNormalizado) return

    if (codigoSolicitado && ultimoEmailSolicitado === emailNormalizado && reenviarEm > 0) {
      solicitarEtapaCodigoSemReenviar()
      return
    }

    void solicitarCodigo()
  }

  function voltarParaEmail() {
    setEtapa('email')
    setCodigo('')
    setErro('')
  }

  return (
    <main className="gendaz-auth gendaz-auth--login">
      <section className="gendaz-auth__card login-card-v2">
        <div className="login-card-header gendaz-auth__header">
          <div className="login-brand-v2 gendaz-auth__brand">
            <img src={logoMeuGendaz} alt="Meu Gendaz" className="gendaz-auth__logo" />
          </div>
        </div>
        <span className="gendaz-kicker">Meu gendaz</span>
        <h1 className="login-title-v2">{nomeEmpresa ? `Entrar em ${nomeEmpresa}` : 'Entrar sem senha'}</h1>
        <p className="login-helper-v2 gendaz-auth__text">Use qualquer e-mail para receber um codigo de acesso.</p>

        {erro && <p className="login-error-v2">{erro}</p>}

        {etapa === 'email' ? (
          <form className="login-form-v2 gendaz-auth__form" onSubmit={entrarComEmail}>
            <label className="login-field-v2">
              <span className="login-label-v2">E-mail</span>
              <div className="login-input-wrap-v2">
                <Mail size={16} className="login-input-icon-left" />
                <input
                  value={email}
                  onChange={(event) => {
                    const novoEmail = event.target.value
                    setEmail(novoEmail)
                    if (ultimoEmailSolicitado && novoEmail.trim().toLowerCase() !== ultimoEmailSolicitado) {
                      setCodigoSolicitado(false)
                    }
                  }}
                  placeholder="voce@exemplo.com"
                  type="email"
                  autoComplete="email"
                />
              </div>
            </label>
            <button className="login-submit-v2" type="submit" disabled={carregando || bloqueado}>
              {carregando ? <><Loader className="spin" size={16} /> Enviando...</> : 'Continuar'}
            </button>
          </form>
        ) : (
          <form className="login-form-v2 gendaz-auth__form" onSubmit={confirmarCodigo}>
            <label className="login-field-v2">
              <span className="login-label-v2">Digite o codigo enviado para seu e-mail</span>
              <div className="login-input-wrap-v2">
                <Mail size={16} className="login-input-icon-left" />
                <input
                  value={codigo}
                  onChange={(event) => setCodigo(event.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="000000"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                />
              </div>
            </label>
            <button className="login-submit-v2" type="submit" disabled={carregando || bloqueado}>
              {carregando ? <><Loader className="spin" size={16} /> Validando...</> : 'Confirmar'}
            </button>
            <button
              className="gendaz-btn gendaz-btn--voltar"
              type="button"
              onClick={voltarParaEmail}
              disabled={carregando}
            >
              <ArrowLeft size={16} /> Voltar
            </button>
            <button className="gendaz-btn gendaz-btn--ghost" type="button" onClick={() => void reenviarCodigo()} disabled={carregando || reenviarEm > 0 || bloqueado}>
              {carregando ? <><Loader className="spin" size={16} /> Reenviando...</> : reenviarEm > 0 ? `Reenviar em ${reenviarEm}s` : 'Reenviar codigo'}
            </button>
            <small>
              {reenviarEm > 0
                ? `Codigo valido por mais ${reenviarEm}s.`
                : `Tentativas restantes: ${Math.max(0, 5 - tentativas)}`}
            </small>
          </form>
        )}
      </section>
    </main>
  )
}

function GendazCadastroGate({ slug }) {
  const navigate = useNavigate()
  const { perfilAcesso, atualizarPerfil, sincronizarDados, logout } = useContext(ClienteGendazContext)
  const [nome, setNome] = useState('')
  const [telefone, setTelefone] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [saindo, setSaindo] = useState(false)
  const [erro, setErro] = useState('')

  useEffect(() => {
    setNome(perfilAcesso?.nome || '')
    setTelefone(normalizarParaInput(perfilAcesso?.telefone || ''))
  }, [perfilAcesso])

  async function sair() {
    setSaindo(true)
    window.dispatchEvent(new CustomEvent('gendaz:toast', {
      detail: { id: TOAST_LOGOUT_ID, type: 'loading', message: 'Saindo da conta... aguarde' },
    }))
    try {
      await logout()
      navigate(`/meu-gendaz/${slug}`, { replace: true })
    } finally {
      window.dispatchEvent(new CustomEvent('gendaz:toast-dismiss', {
        detail: { id: TOAST_LOGOUT_ID },
      }))
      setSaindo(false)
    }
  }

  async function entrar(event) {
    event.preventDefault()
    setErro('')

    const nomeLimpo = nome.trim()
    const emailLimpo = perfilAcesso?.email?.trim() || ''
    const telefonePadrao = normalizarParaApi(telefone)

    if (!nomeLimpo || nomeLimpo.length < 3) {
      setErro('Nome deve ter pelo menos 3 caracteres.')
      return
    }
    if (/^\d+$/.test(nomeLimpo)) {
      setErro('Nome não pode conter apenas numeros.')
      return
    }

    const erroTelefone = validarTelefone(telefone)
    if (erroTelefone) {
      setErro(erroTelefone)
      return
    }

    if (!emailLimpo || !emailLimpo.includes('@')) {
      setErro('E-mail invalido.')
      return
    }

    setSalvando(true)
    try {
      await atualizarPerfil({
        nome: nomeLimpo,
        email: emailLimpo,
        telefone: telefonePadrao || '',
      })
      await sincronizarDados({ exigirSessao: true })
      navigate(`/meu-gendaz/${slug}/dashboard`, { replace: true })
    } catch (error) {
      setErro(error.response?.data?.mensagem || error.message || 'Nao foi possivel concluir o cadastro.')
    } finally {
      setSalvando(false)
    }
  }

  return (
    <main className="gendaz-auth">
      <section className="gendaz-auth__card">
        <div className="gendaz-auth__brand">
          <img src={logoMeuGendaz} alt="Meu Gendaz" className="gendaz-auth__logo" />
        </div>
        <span className="gendaz-kicker">Meu gendaz</span>
        <h1>Complete seu cadastro</h1>
        <p>Seu acesso foi liberado. Agora complete nome e telefone para continuar.</p>

        {erro && <p className="gendaz-auth__error">{erro}</p>}

        <form className="gendaz-auth__form" onSubmit={entrar}>
          <label>
            <span>E-mail</span>
            <input
              value={perfilAcesso?.email || ''}
              readOnly
              placeholder="voce@exemplo.com"
              type="email"
              autoComplete="email"
            />
          </label>
          <label>
            <span>Nome completo</span>
            <input
              value={nome}
              onChange={(event) => setNome(event.target.value)}
              placeholder="Seu nome completo"
              type="text"
              autoComplete="name"
            />
          </label>
          <InternationalPhoneInput
            label="Telefone"
            value={telefone}
            onChangeValue={(valor) => setTelefone(valor || '')}
            defaultCountry="BR"
            helper={telefone ? (validarTelefone(telefone) || ' Pronto para confirmar') : `Exemplo para o país selecionado: ${obterExemploTelefone('BR') || '+55 (65) 99336-0341'}`}
          />
          <button className="gendaz-btn gendaz-btn--primary" type="submit" disabled={salvando || saindo}>
            {salvando ? <><Loader className="spin" size={16} /> Entrando...</> : 'Entrar'}
          </button>
          <button className="gendaz-btn gendaz-btn--voltar" type="button" onClick={() => void sair()} disabled={salvando || saindo}>
            {saindo ? <><Loader className="spin" size={16} /> Saindo...</> : <><LogOut size={16} /> Sair</>}
          </button>
          <small>O e-mail vem do login. Nome e telefone seguem a regra do sistema.</small>
        </form>
      </section>
    </main>
  )
}

export default function Gendaz() {
  const { slug } = useParams()
  useEffect(() => {
    if (typeof document === 'undefined') return undefined

    const html = document.documentElement
    const temaAnterior = html.dataset.theme || ''
    if (!html.dataset.theme) {
      html.dataset.theme = 'light'
    }

    return () => {
      if (temaAnterior) {
        html.dataset.theme = temaAnterior
      } else {
        delete html.dataset.theme
      }
    }
  }, [])

  if (!slug) return null
  return (
    <ClienteGendazProvider slug={slug}>
      <OperationToast />
      <GendazContent slug={slug} />
    </ClienteGendazProvider>
  )
}

function GendazContent({ slug }) {
  const { cliente, cadastroPendente, carregando, perfilAcesso, sincronizarDados } = useContext(ClienteGendazContext)
  const location = useLocation()

  useEffect(() => {
    const tituloAnterior = document.title
    document.title = 'Meu Gendaz'
    return () => {
      document.title = tituloAnterior
    }
  }, [])

  useEffect(() => {
    if (!slug) return undefined
    return () => {}
  }, [slug])

  useEffect(() => {
    if (!location.state?.mobileIosLogin) return undefined
    const timer = window.setTimeout(() => {
      void sincronizarDados({ exigirSessao: true })
      window.history.replaceState({}, '', window.location.pathname)
    }, 900)
    return () => window.clearTimeout(timer)
  }, [location.state, sincronizarDados])

  const handleLogin = useCallback(async () => {
    await sincronizarDados({ exigirSessao: true })
  }, [sincronizarDados])

  const bloqueiaTela = carregando && !cliente && !perfilAcesso && !cadastroPendente

  if (bloqueiaTela) {
    return (
      <main className="gendaz-loading">
        <p>Carregando sessão...</p>
      </main>
    )
  }

  if (cadastroPendente) {
    return <GendazCadastroGate slug={slug} />
  }

  return cliente ? <GendazLayout /> : <GendazAuthGate slug={slug} onLogin={handleLogin} />
}
