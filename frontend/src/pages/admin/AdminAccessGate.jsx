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
        // Demais erros (rede, 401, 5xx) mantem o login visivel para o
        // usuario tentar novamente, sem tela preta.
        if (error.response?.status === 404) {
          setStatus('not-found')
          return
        }
        setStatus('allowed')
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

  return children
}
