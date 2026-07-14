import { useEffect, useState, useCallback } from 'react'
import { useNavigate, Outlet } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import clienteApi from '../api/clienteApi.js'
import { ClienteGendazProvider } from '../contexts/ClienteGendazContext.jsx'
import GendazLayout from '../components/gendaz/GendazLayout.jsx'

function GendazAuthGate({ onLogin }) {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [codigo, setCodigo] = useState('')
  const [etapa, setEtapa] = useState('email')
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState('')
  const [reenviarEm, setReenviarEm] = useState(0)
  const [tentativas, setTentativas] = useState(0)
  const [bloqueado, setBloqueado] = useState(false)

  useEffect(() => {
    if (!reenviarEm) return undefined
    const timer = setInterval(() => {
      setReenviarEm((atual) => (atual <= 1 ? 0 : atual - 1))
    }, 1000)
    return () => clearInterval(timer)
  }, [reenviarEm])

  async function solicitarCodigo() {
    setErro('')
    if (!email.trim()) return
    setCarregando(true)
    try {
      const response = await clienteApi.post('/meu-gendaz/auth/solicitar-codigo', { email: email.trim() })
      setEtapa('codigo')
      setReenviarEm(30)
      setTentativas(0)
      setCodigo('')
    } catch (error) {
      const mensagem = error.response?.data?.mensagem || error.response?.data?.message || error.message || 'Não foi possível enviar o código.'
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
        email: email.trim(),
        codigo: codigo.trim(),
      })

      if (response.data?.mensagem && response.data?.status === 'ACTIVE') {
        const token = response.data?.sessionToken || ''
        const tokenData = {
          email: email.trim(),
          sessionToken: token,
          savedAt: Date.now(),
          expiresIn: 90 * 24 * 60 * 60 * 1000,
        }
        localStorage.setItem('meu-gendaz-auth', JSON.stringify(tokenData))
        onLogin()
        navigate('/meu-gendaz/dashboard', { replace: true })
      } else {
        setErro(response.data?.mensagem || 'Não foi possível realizar login.')
      }
    } catch (error) {
      const mensagem = error.response?.data?.mensagem || error.response?.data?.message || error.message || 'Código inválido.'
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
        <span className="gendaz-kicker">Meu gendaz</span>
        <h1>Entrar sem senha</h1>
        <p>Use seu e-mail cadastrado para receber um código de acesso.</p>

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
              <span>Digite o código enviado para seu e-mail</span>
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
              {reenviarEm > 0 ? `Reenviar em ${reenviarEm}s` : 'Reenviar código'}
            </button>
            <small>Tentativas restantes: {Math.max(0, 5 - tentativas)}</small>
          </form>
        )}
      </section>
    </main>
  )
}

function isAuthValid() {
  try {
    const raw = localStorage.getItem('meu-gendaz-auth')
    if (!raw) return false
    const data = JSON.parse(raw)
    if (!data?.sessionToken) return false
    return true
  } catch { return false }
}

export default function Gendaz() {
  const [logado, setLogado] = useState(() => isAuthValid())

  const handleLogin = useCallback(() => setLogado(true), [])

  const handleLogout = useCallback(() => {
    localStorage.removeItem('meu-gendaz-auth')
    setLogado(false)
  }, [])

  useEffect(() => {
    setLogado(isAuthValid())
    window.addEventListener('meu-gendaz:logout', handleLogout)
    return () => window.removeEventListener('meu-gendaz:logout', handleLogout)
  }, [handleLogout])

  return (
    <ClienteGendazProvider>
      {logado ? <GendazLayout /> : <GendazAuthGate onLogin={handleLogin} />}
    </ClienteGendazProvider>
  )
}
