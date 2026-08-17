import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { adminApi } from '../../api/adminApi.js'

export default function AdminAccessGate({ children }) {
  const [status, setStatus] = useState('loading')

  useEffect(() => {
    let ativo = true

    adminApi.access()
      .then(() => {
        if (ativo) setStatus('allowed')
      })
      .catch((error) => {
        if (!ativo) return
        // Rota admin oculta (whitelist de IP) -> pagina nao encontrada.
        // Demais erros (rede, 403, 401, 5xx) -> fail-closed: o login do
        // admin NAO aparece, exibimos uma tela neutra de verificacao.
        if (error.response?.status === 404) {
          setStatus('not-found')
          return
        }
        setStatus('error')
      })

    return () => {
      ativo = false
    }
  }, [])

  if (status === 'loading') {
    return (
      <main className="admin-login-screen app-dark-screen">
        <section className="admin-login-panel">
          <span className="section-kicker">Acesso restrito</span>
          <h1>Verificando acesso</h1>
          <p className="admin-login-copy">Aguarde um instante enquanto confirmamos sua autorização.</p>
        </section>
      </main>
    )
  }

  if (status === 'not-found') {
    return <Navigate to="/not-found" replace />
  }

  if (status === 'error') {
    return (
      <main className="admin-login-screen app-dark-screen">
        <section className="admin-login-panel">
          <span className="section-kicker">Acesso restrito</span>
          <h1>Não foi possível verificar o acesso</h1>
          <p className="admin-login-copy">
            Não foi possível confirmar sua autorização para a área administrativa.
            Tente novamente mais tarde.
          </p>
        </section>
      </main>
    )
  }

  return children
}
