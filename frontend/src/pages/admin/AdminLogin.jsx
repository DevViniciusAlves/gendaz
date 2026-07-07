import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { Link } from 'react-router-dom'
import Button from '../../components/Button.jsx'
import Input from '../../components/Input.jsx'
import { useAuth } from '../../contexts/AuthContext.jsx'
import logoWhite from '../../assets/logos/gendaz-logo-white.png'

export default function AdminLogin() {
  const { usuario, adminUsuario, adminLogin } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)

  if (adminUsuario) return <Navigate to="/admin/dashboard" replace />
  if (usuario) return <Navigate to="/sistema/dashboard" replace />

  async function entrar(event) {
    event.preventDefault()
    setErro('')
    setCarregando(true)
    try {
      await adminLogin(email, senha)
      navigate('/admin/dashboard')
    } catch (error) {
      setErro(error.message || 'Nao foi possivel entrar no painel admin.')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <main className="admin-login-screen app-dark-screen">
      <section className="admin-login-panel">
        <div className="login-brand admin-login-brand">
          <img src={logoWhite} alt="gendaz" className="auth-logo admin-login-logo" />
          <Link to="/" className="secondary-link compact-link">Voltar ao site</Link>
        </div>
        <span className="section-kicker">Acesso restrito</span>
        <h1>Super Admin</h1>
        <p className="admin-login-copy">Use a conta administrativa da plataforma para acessar o painel global.</p>
        <form onSubmit={entrar}>
          <Input label="E-mail admin" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
          <Input label="Senha" type="password" value={senha} onChange={(event) => setSenha(event.target.value)} required />
          {erro && <p className="form-error">{erro}</p>}
          <Button type="submit" disabled={carregando}>{carregando ? 'Entrando...' : 'Entrar no Admin'}</Button>
        </form>
        <p className="admin-login-helper">Acesso exclusivo para administracao da plataforma.</p>
      </section>
    </main>
  )
}
