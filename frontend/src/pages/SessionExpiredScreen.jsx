import { LogOut, LockKeyhole } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext.jsx'
import logoGendaz from '../assets/logos/gendaz-logo-branco.png'

export default function SessionExpiredScreen() {
  const navigate = useNavigate()
  const { logout } = useAuth()

  function voltarAoLogin() {
    logout('manual')
    navigate('/login', { replace: true })
  }

  return (
    <main className="session-expired-screen">
      <section className="session-expired-card">
        <img src={logoGendaz} alt="gendaz" className="session-expired-logo" />

        <div className="session-expired-icon">
          <LockKeyhole size={32} />
        </div>

        <span className="session-expired-badge">Segurança</span>

        <h1>Sessão encerrada</h1>

        <p className="session-expired-copy">
          Sua conta foi acessada em outro navegador. Por segurança, esta sessão foi
          encerrada para que apenas o novo acesso continue ativo.
        </p>

        <button type="button" className="session-expired-button" onClick={voltarAoLogin}>
          <LogOut size={18} />
          <span>Voltar ao Login</span>
        </button>

        <small className="session-expired-note">Você pode entrar novamente quando quiser.</small>
      </section>
    </main>
  )
}
