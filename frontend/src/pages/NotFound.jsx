import { Link } from 'react-router-dom'

export default function NotFound() {
  return (
    <main className="admin-login-screen app-dark-screen">
      <section className="admin-login-panel">
        <span className="section-kicker">Erro 404</span>
        <h1>Pagina nao encontrada</h1>
        <p className="admin-login-copy">
          O endereco solicitado nao existe ou foi removido.
        </p>
        <Link to="/" className="btn btn-primary" style={{ marginTop: '1.5rem', display: 'inline-block', textDecoration: 'none' }}>
          Voltar para a pagina inicial
        </Link>
      </section>
    </main>
  )
}
