import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { adminApi } from '../../api/adminApi.js'

export default function AdminAccessGate({ children }) {
  const [allowed, setAllowed] = useState(null)

  useEffect(() => {
    let ativo = true

    adminApi.access()
      .then(() => {
        if (ativo) setAllowed(true)
      })
      .catch(() => {
        if (ativo) setAllowed(false)
      })

    return () => {
      ativo = false
    }
  }, [])

  if (allowed === null) {
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

  if (!allowed) {
    return <Navigate to="/not-found" replace />
  }

  return children
}
