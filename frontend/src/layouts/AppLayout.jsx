import { Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useEffect, useRef } from 'react'
import AnimatedBackground from '../components/AnimatedBackground.jsx'
import Header from '../components/Header.jsx'
import OperationToast from '../components/OperationToast.jsx'
import Sidebar from '../components/Sidebar.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'
import { PendentesProvider } from '../contexts/PendentesContext.jsx'

export default function AppLayout() {
  const { usuario, impersonation, encerrarImpersonacao, renovarAoRetomarAba } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const renovarSessaoRef = useRef(renovarAoRetomarAba)

  useEffect(() => {
    renovarSessaoRef.current = renovarAoRetomarAba
  }, [renovarAoRetomarAba])

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'auto' })
  }, [location.pathname])

  useEffect(() => {
    if (!usuario?.id || usuario?.perfil === 'SUPER_ADMIN' || impersonation) return
    let ativo = true
    renovarSessaoRef.current({ ignorarThrottle: true })
      .then((valida) => {
        if (ativo && valida === false) {
          navigate('/login', { replace: true })
        }
      })
      .catch(() => {})
    return () => {
      ativo = false
    }
  }, [location.pathname, usuario?.id, usuario?.perfil, impersonation, navigate])

  if (usuario?.perfil === 'SUPER_ADMIN' && !impersonation) {
    return <Navigate to="/admin/dashboard" replace />
  }
  if (usuario?.statusConta === 'ACCOUNT_INACTIVE' && usuario?.motivoInatividade === 'CONTA_ENCERRADA' && !impersonation) {
    return <Navigate to="/conta-encerrada" replace />
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
              <strong>Voce esta visualizando esta conta como administrador.</strong>
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
