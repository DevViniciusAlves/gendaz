import { useEffect, useState, useCallback, useContext } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Loader, LogOut } from 'lucide-react'
import clienteApi from '../api/clienteApi.js'
import { ClienteGendazContext, ClienteGendazProvider } from '../contexts/ClienteGendazContext.jsx'
import GendazLayout from '../components/gendaz/GendazLayout.jsx'
import { aplicarMascara, padronizarTelefone, validarTelefone } from '../utils/phoneUtils.js'
import logoMeuGendaz from '../assets/logos/meugendazpngpreto.png'

const COOLDOWN_SEGUNDOS = 120

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
      await clienteApi.post('/meu-gendaz/auth/solicitar-codigo', { slug, email: email.trim() })
      setUltimoEmailSolicitado(emailAtualNormalizado())
      setCodigoSolicitado(true)
      setEtapa('codigo')
      setReenviarEm(COOLDOWN_SEGUNDOS)
      setTentativas(0)
      setCodigo('')
    } catch (error) {
      const mensagem = error.response?.data?.mensagem || error.response?.data?.message || error.message || 'Nao foi possivel enviar o codigo.'
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
      })
      if (response.data?.mensagem && response.data?.status === 'ACTIVE') {
        await onLogin()
        navigate(`/meu-gendaz/${slug}/dashboard`, { replace: true })
      } else {
        setErro(response.data?.mensagem || 'Nao foi possivel realizar login.')
      }
    } catch (error) {
      const mensagem = error.response?.data?.mensagem || error.response?.data?.message || error.message || 'Codigo invalido.'
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
    <main className="gendaz-auth">
      <section className="gendaz-auth__card">
        <div className="gendaz-auth__brand">
          <img src={logoMeuGendaz} alt="Meu Gendaz" className="gendaz-auth__logo" />
        </div>
        <span className="gendaz-kicker">Meu gendaz</span>
        <h1>{nomeEmpresa ? `Entrar em ${nomeEmpresa}` : 'Entrar sem senha'}</h1>
        <p>Use qualquer e-mail para receber um codigo de acesso.</p>

        {erro && <p className="gendaz-auth__error">{erro}</p>}

        {etapa === 'email' ? (
          <form className="gendaz-auth__form" onSubmit={entrarComEmail}>
            <label>
              <span>E-mail</span>
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
            </label>
            <button className="gendaz-btn gendaz-btn--primary" type="submit" disabled={carregando || bloqueado}>
              {carregando ? 'Enviando...' : 'Continuar'}
            </button>
          </form>
      ) : (
        <form className="gendaz-auth__form" onSubmit={confirmarCodigo}>
            <label>
              <span>Digite o codigo enviado para seu e-mail</span>
              <input
                value={codigo}
                onChange={(event) => setCodigo(event.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="000000"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
              />
            </label>
            <button className="gendaz-btn gendaz-btn--primary" type="submit" disabled={carregando || bloqueado}>
              {carregando ? 'Validando...' : 'Confirmar'}
            </button>
            <button
              className="gendaz-btn gendaz-btn--voltar"
              type="button"
              onClick={voltarParaEmail}
              disabled={carregando}
            >
              <ArrowLeft size={16} /> Voltar
            </button>
            <button className="gendaz-btn gendaz-btn--ghost" type="button" onClick={() => void reenviarCodigo()} disabled={reenviarEm > 0 || bloqueado}>
              {reenviarEm > 0 ? `Reenviar em ${reenviarEm}s` : 'Reenviar codigo'}
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
  const [erro, setErro] = useState('')

  useEffect(() => {
    setNome(perfilAcesso?.nome || '')
    setTelefone(aplicarMascara(perfilAcesso?.telefone || ''))
  }, [perfilAcesso])

  async function sair() {
    await logout()
    navigate('/login', { replace: true })
  }

  async function entrar(event) {
    event.preventDefault()
    setErro('')

    const nomeLimpo = nome.trim()
    const emailLimpo = perfilAcesso?.email?.trim() || ''
    const telefonePadrao = padronizarTelefone(telefone)

    if (!nomeLimpo || nomeLimpo.length < 3) {
      setErro('Nome deve ter pelo menos 3 caracteres.')
      return
    }
    if (/^\d+$/.test(nomeLimpo)) {
      setErro('Nome nao pode conter apenas numeros.')
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
          <label>
            <span>Telefone</span>
            <input
              value={telefone}
              onChange={(event) => setTelefone(aplicarMascara(event.target.value))}
              placeholder="XX XXXXXXXXX"
              type="tel"
              inputMode="numeric"
              maxLength={19}
              autoComplete="tel"
            />
          </label>
          <button className="gendaz-btn gendaz-btn--primary" type="submit" disabled={salvando}>
            {salvando ? <><Loader size={16} /> Entrando...</> : 'Entrar'}
          </button>
          <button className="gendaz-btn gendaz-btn--voltar" type="button" onClick={() => void sair()} disabled={salvando}>
            <LogOut size={16} /> Sair
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
    // Verifica se ja esta em algum tema, se nao, tenta respeitar o sistema ou define padrao
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
      <GendazContent slug={slug} />
    </ClienteGendazProvider>
  )
}

function GendazContent({ slug }) {
  const { cliente, cadastroPendente, carregando, perfilAcesso, sincronizarDados } = useContext(ClienteGendazContext)

  useEffect(() => {
    const tituloAnterior = document.title
    document.title = 'Meu Gendaz'
    return () => {
      document.title = tituloAnterior
    }
  }, [])

  useEffect(() => {
    if (!slug) return undefined
    // O backend gerencia a sessão via cookie automaticamente.
    // Não é mais necessário manipular headers manualmente.
    return () => {}
  }, [slug])

  const handleLogin = useCallback(async () => {
    await sincronizarDados({ exigirSessao: true })
  }, [sincronizarDados])

  // Efeito de sincronização ao mudar de rota removido para evitar loop infinito
  // na inicialização ou navegação dentro do Meu Gendaz.
  // A sincronização inicial é feita pelo useEffect no ClienteGendazProvider (linha 160).

  const bloqueiaTela = carregando && !cliente && !perfilAcesso && !cadastroPendente

  if (bloqueiaTela) {
    return (
      <main className="gendaz-loading">
        <p>Carregando sessao...</p>
      </main>
    )
  }

  if (cadastroPendente) {
    return <GendazCadastroGate slug={slug} />
  }

  return cliente ? <GendazLayout /> : <GendazAuthGate slug={slug} onLogin={handleLogin} />
}
