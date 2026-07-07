export default function PlaceholderPage({ title, kicker, description }) {
  return (
    <section className="page">
      <div className="page-title">
        <span className="section-kicker">{kicker}</span>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      <section className="panel placeholder-panel">
        <h2>Área preparada</h2>
        <p>Esta tela está separada por plano e pronta para receber o fluxo completo na próxima etapa.</p>
      </section>
    </section>
  )
}
