import { useNavigate } from 'react-router-dom'

export default function NotFound() {
  const navigate = useNavigate()

  return (
    <main className="notfound-screen">
      <section className="notfound-content" aria-label="Página não encontrada">
        <span className="notfound-brand">gendaz</span>
        <h1 className="notfound-code">404</h1>
        <p className="notfound-title">Ops, essa página não existe.</p>
        <p className="notfound-description">
          O endereço pode estar incorreto, ter sido removido ou não estar mais disponível.
        </p>
        <button type="button" className="notfound-button" onClick={() => navigate('/')}>
          Voltar para o início
        </button>
      </section>
    </main>
  )
}
