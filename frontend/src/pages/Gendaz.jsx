import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ClienteProvider } from '../context/ClienteContext.jsx'
import GendazLayout from '../components/gendaz/GendazLayout.jsx'

function GendazAuthGate({ onLogin }) {
  const navigate = useNavigate()
  const [identificador, setIdentificador] = useState('')
  const [codigo, setCodigo] = useState('')

  function handleSubmit(event) {
    event.preventDefault()
    if (!identificador.trim() || !codigo.trim()) return
    localStorage.setItem('meu-gendaz-token', JSON.stringify({
      token: `token-${Date.now()}`,
      identificador: identificador.trim(),
    }))
    onLogin()
    navigate('/meu-gendaz/dashboard', { replace: true })
  }

  return (
    <main className="gendaz-auth">
      <section className="gendaz-auth__card">
        <span className="gendaz-kicker">Meu Gendaz</span>
        <h1>Entre com telefone ou e-mail</h1>
        <p>Digite seus dados e o código recebido para acessar seu portal.</p>
        <form className="gendaz-auth__form" onSubmit={handleSubmit}>
          <label>
            <span>Telefone ou e-mail</span>
            <input value={identificador} onChange={(event) => setIdentificador(event.target.value)} placeholder="(65) 99999-9999 ou email" />
          </label>
          <label>
            <span>Código</span>
            <input value={codigo} onChange={(event) => setCodigo(event.target.value)} placeholder="000000" inputMode="numeric" />
          </label>
          <button className="gendaz-btn gendaz-btn--primary" type="submit">Entrar</button>
        </form>
      </section>
    </main>
  )
}

export default function Gendaz() {
  const [logado, setLogado] = useState(() => Boolean(localStorage.getItem('meu-gendaz-token')))

  useEffect(() => {
    setLogado(Boolean(localStorage.getItem('meu-gendaz-token')))
  }, [])

  return (
    <ClienteProvider>
      {logado ? <GendazLayout /> : <GendazAuthGate onLogin={() => setLogado(true)} />}
    </ClienteProvider>
  )
}

