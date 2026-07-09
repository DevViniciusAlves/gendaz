import { Bell, LogOut, Mail, Shield, UserRound } from 'lucide-react'
import { useCliente } from '../../context/ClienteContext.jsx'

export default function Configuracoes() {
  const { portal, atualizarConfiguracoes } = useCliente()

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Configurações</span>
        <h1>Meu perfil e preferências</h1>
        <p>Telefone, e-mail, notificações, privacidade e saída da sessão.</p>
      </header>

      <div className="gendaz-grid gendaz-grid--two">
        <article className="gendaz-panel">
          <div className="gendaz-panel__head"><UserRound size={18} /><h2>Meu perfil</h2></div>
          <div className="gendaz-stack">
            <div className="gendaz-mini-card"><strong>Nome</strong><span>{portal.cliente.nome}</span></div>
            <div className="gendaz-mini-card"><strong>Telefone</strong><span>{portal.configuracoes.telefone}</span></div>
            <div className="gendaz-mini-card"><strong>E-mail</strong><span>{portal.configuracoes.email}</span></div>
          </div>
        </article>

        <article className="gendaz-panel">
          <div className="gendaz-panel__head"><Bell size={18} /><h2>Notificações</h2></div>
          <label className="gendaz-toggle">
            <input
              type="checkbox"
              checked={portal.configuracoes.receberNotificacoes}
              onChange={(event) => atualizarConfiguracoes({ receberNotificacoes: event.target.checked })}
            />
            <span>Receber notificações</span>
          </label>
          <label className="gendaz-toggle">
            <input
              type="checkbox"
              checked={portal.configuracoes.privacidadeCompartilhada}
              onChange={(event) => atualizarConfiguracoes({ privacidadeCompartilhada: event.target.checked })}
            />
            <span>Compartilhar preferências com a IA</span>
          </label>
        </article>
      </div>

      <article className="gendaz-panel">
        <div className="gendaz-panel__head"><Shield size={18} /><h2>Privacidade e saída</h2></div>
        <p>Se quiser, sua sessão pode permanecer salva por longo período no mesmo dispositivo.</p>
        <button className="gendaz-btn gendaz-btn--ghost" type="button"><LogOut size={16} />Sair</button>
      </article>
    </section>
  )
}
