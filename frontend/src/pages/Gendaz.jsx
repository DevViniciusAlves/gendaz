import { useEffect, useState, useCallback, useContext } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import clienteApi from '../api/clienteApi.js'
import { ClienteGendazContext, ClienteGendazProvider } from '../contexts/ClienteGendazContext.jsx'
import GendazLayout from '../components/gendaz/GendazLayout.jsx'
import logoMeuGendaz from '../assets/logos/meugendazpngpreto.png'

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

  async function solicitarCodigo() {
    setErro('')
    if (!email.trim()) return
    setCarregando(true)
    try {
      await clienteApi.post('/meu-gendaz/auth/solicitar-codigo', { slug, email: email.trim() })
      setEtapa('codigo')
      setReenviarEm(30)
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

  return (
    <main className="gendaz-auth">
      <section className="gendaz-auth__card">
        <div className="gendaz-auth__brand">
          <img src={logoMeuGendaz} alt="Meu Gendaz" className="gendaz-auth__logo" />
        </div>
        <span className="gendaz-kicker">Meu gendaz</span>
        <h1>{nomeEmpresa ? `Entrar em ${nomeEmpresa}` : 'Entrar sem senha'}</h1>
        <p>Use seu e-mail cadastrado para receber um codigo de acesso.</p>

        {erro && <p className="gendaz-auth__error">{erro}</p>}

        {etapa === 'email' ? (
          <form className="gendaz-auth__form" onSubmit={(event) => { event.preventDefault(); void solicitarCodigo(); }}>
            <label>
              <span>E-mail</span>
              <input
                value={email}
                onChange={(event) => setEmail(event.target.value)}
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
              onClick={() => { setEtapa('email'); setCodigo(''); setErro(''); }}
              disabled={carregando}
            >
              <ArrowLeft size={16} /> Voltar
            </button>
            <button className="gendaz-btn gendaz-btn--ghost" type="button" onClick={() => void reenviarCodigo()} disabled={reenviarEm > 0 || bloqueado}>
              {reenviarEm > 0 ? `Reenviar em ${reenviarEm}s` : 'Reenviar codigo'}
            </button>
            <small>Tentativas restantes: {Math.max(0, 5 - tentativas)}</small>
          </form>
        )}
      </section>
    </main>
  )
}

export default function Gendaz() {
  const { slug } = useParams()
  if (!slug) return null
  return (
    <ClienteGendazProvider slug={slug}>
      <GendazContent slug={slug} />
    </ClienteGendazProvider>
  )
}

function GendazContent({ slug }) {
  const { cliente, carregando, sincronizarDados } = useContext(ClienteGendazContext)

  const handleLogin = useCallback(async () => {
    await sincronizarDados({ exigirSessao: true })
  }, [sincronizarDados])

  if (carregando) {
    return (
      <main className="gendaz-loading">
        <p>Carregando sessao...</p>
      </main>
    )
  }

  return cliente ? <GendazLayout /> : <GendazAuthGate slug={slug} onLogin={handleLogin} />
}
