import { Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useEffect } from 'react'
import AnimatedBackground from '../components/AnimatedBackground.jsx'
import Header from '../components/Header.jsx'
import OperationToast from '../components/OperationToast.jsx'
import Sidebar from '../components/Sidebar.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'
import { PendentesProvider } from '../contexts/PendentesContext.jsx'

export default function AppLayout() {
  const { usuario, impersonation, encerrarImpersonacao } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'auto' })
  }, [location.pathname])

  if (usuario?.perfil === 'SUPER_ADMIN' && !impersonation) {
    return <Navigate to="/admin/dashboard" replace />
  }
  if (usuario?.statusConta === 'ACCOUNT_INACTIVE' && !impersonation) {
    return <Navigate to="/conta-inativa" replace />
  }

  async function sairDaContaAcessada() {
    try {
      await encerrarImpersonacao()
    } finally {
      navigate('/admin/dashboard')
    }
  }

  return (
    <PendentesProvider>
      <div className="app-shell">
        <AnimatedBackground />
        <OperationToast />
        <Sidebar />
        <div className="app-main">
          {impersonation && (
            <div className="impersonation-banner">
              <strong>Voce esta acessando a conta de {impersonation.empresa} como Super Admin.</strong>
              <button type="button" onClick={sairDaContaAcessada}>Sair da conta e voltar ao Admin</button>
            </div>
          )}
          <Header />
          <main className="content">
            <Outlet key={location.pathname} />
          </main>
        </div>
      </div>
    </PendentesProvider>
  )
}
