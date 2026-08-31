import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Eye, EyeOff, Loader, Mail, Lock } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext.jsx'
import logoSvg from '../assets/logos/gendaz-logo-branco.png'

export default function Login() {
  const { usuario, adminUsuario, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [mostrarSenha, setMostrarSenha] = useState(false)
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)
  const mensagemAviso = location.state?.mensagem || ''

  if (adminUsuario) {
    return <Navigate to="/admin/dashboard" replace />
  }

  if (usuario) {
    if (usuario.statusConta === 'ACCOUNT_INACTIVE' && usuario.motivoInatividade === 'CONTA_ENCERRADA') {
      return <Navigate to="/conta-encerrada" replace />
    }
    if (usuario.statusConta === 'ACCOUNT_INACTIVE') {
      return <Navigate to="/conta-inativa" replace />
    }
    if (usuario.perfil === 'SUPER_ADMIN') {
      return <Navigate to="/admin/login" replace />
    }
    return <Navigate to="/sistema/dashboard" replace />
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (carregando) return
    setErro('')
    setCarregando(true)
    try {
      const resultado = await login(email, senha)
<<<<<<< HEAD
      if (resultado?.pendingPayment) {
        navigate('/pagamento-pendente')
        return
      }
=======
>>>>>>> origin/stage
      if (resultado?.statusConta === 'ACCOUNT_INACTIVE') {
        if (resultado?.motivoInatividade === 'CONTA_ENCERRADA') {
          navigate('/conta-encerrada')
          return
        }
        navigate('/conta-inativa')
        return
      }
      navigate('/sistema/dashboard')
    } catch (error) {
      const status = error.response?.status
      const mensagem = status === 400 || status === 401
        ? 'E-mail ou senha incorretos.'
        : error.response?.data?.mensagem
          || error.response?.data?.message
          || (error.code === 'ECONNABORTED' ? 'A conexão demorou demais. Tente novamente em instantes.' : null)
          || 'Nao foi possivel entrar. Verifique e-mail e senha.'
      setErro(mensagem)
    } finally {
      setCarregando(false)
    }
  }

  return (
    <main className="login-screen-v2">
      <section className="login-card-v2">
        <div className="login-card-header">
          <div className="login-brand-v2">
            <img src={logoSvg} alt="gendaz" className="login-brand-logo" />
          </div>
          <Link to="/" className="login-back-btn">Voltar ao site</Link>
        </div>

        <h1 className="login-title-v2">Entrar na conta</h1>

        {mensagemAviso && <p className="login-success-v2">{mensagemAviso}</p>}

        <form onSubmit={handleSubmit} className="login-form-v2">
          <div className="login-field-v2">
            <label className="login-label-v2">E-mail</label>
            <div className="login-input-wrap-v2">
              <Mail size={16} className="login-input-icon-left" />
              <input
                type="email"
                placeholder="seu@email.com"
                maxLength={120}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="login-field-v2">
            <label className="login-label-v2">Senha</label>
            <div className="login-input-wrap-v2">
              <Lock size={16} className="login-input-icon-left" />
              <input
                type={mostrarSenha ? 'text' : 'password'}
                placeholder="Sua senha"
                maxLength={72}
                value={senha}
                onChange={(e) => setSenha(e.target.value)}
                required
              />
              <button
                type="button"
                className="login-eye-btn"
                aria-label={mostrarSenha ? 'Ocultar senha' : 'Mostrar senha'}
                onClick={() => setMostrarSenha((c) => !c)}
              >
                {mostrarSenha ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </div>

          {erro && <p className="login-error-v2">{erro}</p>}

          <button type="submit" className="login-submit-v2" disabled={carregando}>
            {carregando ? <><Loader className="spin" size={16} /> Entrando...</> : 'Entrar'}
          </button>
        </form>

        <div className="login-links-v2">
          <Link to="/recuperar-senha" className="login-link-v2">Esqueci minha senha</Link>
          <p className="login-helper-v2">
            Ainda não tem conta? <Link to="/criar-conta" className="login-link-v2">Teste gratis por 7 dias</Link>
          </p>
        </div>
      </section>
    </main>
  )
}
