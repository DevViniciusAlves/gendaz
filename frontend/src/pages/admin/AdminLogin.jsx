import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { Link } from 'react-router-dom'
import { Eye, EyeOff, Mail, Lock } from 'lucide-react'
import { useAuth } from '../../contexts/AuthContext.jsx'
import logoWhite from '../../assets/logos/gendaz-logo-branco.png'

export default function AdminLogin() {
  const { usuario, adminUsuario, adminLogin } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [mostrarSenha, setMostrarSenha] = useState(false)
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
    <main className="admin-login-screen">
      <section className="admin-login-card">
        <div className="admin-login-header">
          <div className="admin-login-brand">
            <img src={logoWhite} alt="gendaz" className="admin-login-logo" />
          </div>
          <Link to="/" className="admin-login-back-btn">Voltar ao site</Link>
        </div>

        <span className="admin-login-kicker">Acesso restrito</span>
        <h1 className="admin-login-title">Super Admin</h1>
        <p className="admin-login-copy">Use a conta administrativa da plataforma para acessar o painel global.</p>

        <form onSubmit={entrar} className="admin-login-form">
          <div className="admin-login-field">
            <label className="admin-login-label">E-mail admin</label>
            <div className="admin-login-input-wrap">
              <Mail size={16} className="admin-login-icon" />
              <input
                type="email"
                placeholder="seu@email.com"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                required
              />
            </div>
          </div>

          <div className="admin-login-field">
            <label className="admin-login-label">Senha</label>
            <div className="admin-login-input-wrap">
              <Lock size={16} className="admin-login-icon" />
              <input
                type={mostrarSenha ? 'text' : 'password'}
                placeholder="Sua senha"
                value={senha}
                onChange={(event) => setSenha(event.target.value)}
                required
              />
              <button
                type="button"
                className="admin-login-eye-btn"
                aria-label={mostrarSenha ? 'Ocultar senha' : 'Mostrar senha'}
                onClick={() => setMostrarSenha((current) => !current)}
              >
                {mostrarSenha ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </div>

          {erro && <p className="admin-login-error">{erro}</p>}

          <button type="submit" className="admin-login-submit" disabled={carregando}>
            {carregando ? 'Entrando...' : 'Entrar'}
          </button>
        </form>

        <p className="admin-login-helper">Acesso exclusivo para administracao da plataforma.</p>
      </section>
    </main>
  )
}
