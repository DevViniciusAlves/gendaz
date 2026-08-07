import { LogOut } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import Button from '../components/Button.jsx'
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
    <main className="payment-page">
      <section className="payment-pro-card">
        <img src={logoGendaz} alt="gendaz" className="payment-pro-logo" />
        <span className="payment-plan-badge">Sessão encerrada</span>
        <h1>Sessão encerrada</h1>
        <p className="payment-pro-copy">
          Sua conta foi acessada em outro navegador. Por segurança, esta sessão foi
          encerrada para que apenas o novo acesso continue ativo.
        </p>
        <Button type="button" className="payment-logout-button" onClick={voltarAoLogin}>
          <LogOut size={18} /> Voltar ao Login
        </Button>
      </section>
    </main>
  )
}
