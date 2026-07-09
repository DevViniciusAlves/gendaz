import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { appApi } from '../api/appApi.js'
import { ClienteProvider } from '../context/ClienteContext.jsx'
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
      const response = await appApi.solicitarCodigoMeuGendaz(email.trim())
      setEtapa('codigo')
      setReenviarEm(response?.reenviarDisponivel ? 30 : 30)
      setTentativas(0)
      setCodigo('')
    } catch (error) {
      const mensagem = error.response?.data?.mensagem || error.message || 'Não foi possível enviar o código.'
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
      await appApi.validarCodigoMeuGendaz(email.trim(), codigo.trim())
      localStorage.setItem('meu-gendaz-auth', JSON.stringify({ email: email.trim(), at: Date.now() }))
      onLogin()
      navigate('/meu-gendaz/dashboard', { replace: true })
    } catch (error) {
      const mensagem = error.response?.data?.mensagem || error.message || 'Código inválido.'
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
        <span className="gendaz-kicker">Meu Gendaz</span>
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
            <button className="gendaz-btn" type="button" onClick={() => setEtapa('email')}>
              Alterar e-mail
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

export default function Gendaz() {
  const [logado, setLogado] = useState(() => Boolean(localStorage.getItem('meu-gendaz-auth')))

  useEffect(() => {
    setLogado(Boolean(localStorage.getItem('meu-gendaz-auth')))
  }, [])

  return (
    <ClienteProvider>
      {logado ? <GendazLayout /> : <GendazAuthGate onLogin={() => setLogado(true)} />}
    </ClienteProvider>
  )
}

