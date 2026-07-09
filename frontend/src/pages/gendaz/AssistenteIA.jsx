import { Bot, Send, Sparkles } from 'lucide-react'
import { useState } from 'react'
import { useCliente } from '../../context/ClienteContext.jsx'

export default function AssistenteIA() {
  const { portal, adicionarMensagem } = useCliente()
  const [mensagem, setMensagem] = useState('')

  function handleSubmit(event) {
    event.preventDefault()
    if (!mensagem.trim()) return
    adicionarMensagem(mensagem.trim())
    setMensagem('')
  }

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">Assistente IA</span>
        <h1>Converse naturalmente</h1>
        <p>Peça preços, serviços, profissionais, horários, reagendamentos, promoções e lista de espera.</p>
      </header>

      <div className="gendaz-grid gendaz-grid--two">
        <article className="gendaz-chat">
          <div className="gendaz-panel__head">
            <Bot size={18} />
            <h2>Conversa</h2>
          </div>
          <div className="gendaz-chat__messages">
            {portal.assistente.mensagens.map((item) => (
              <div key={item.id} className={`gendaz-chat__message gendaz-chat__message--${item.origem}`}>
                {item.texto}
              </div>
            ))}
          </div>
          <form className="gendaz-chat__form" onSubmit={handleSubmit}>
            <input
              value={mensagem}
              onChange={(event) => setMensagem(event.target.value)}
              placeholder="Digite sua mensagem"
            />
            <button className="gendaz-btn gendaz-btn--primary" type="submit">
              <Send size={16} />Enviar
            </button>
          </form>
        </article>

        <article className="gendaz-panel">
          <div className="gendaz-panel__head">
            <Sparkles size={18} />
            <h2>Preferências aprendidas</h2>
          </div>
          <div className="gendaz-stack">
            <div className="gendaz-mini-card"><strong>Profissional favorito</strong><span>{portal.assistente.preferencias.profissionalFavorito}</span></div>
            <div className="gendaz-mini-card"><strong>Serviço favorito</strong><span>{portal.assistente.preferencias.servicoFavorito}</span></div>
            <div className="gendaz-mini-card"><strong>Dias preferidos</strong><span>{portal.assistente.preferencias.diasPreferidos}</span></div>
            <div className="gendaz-mini-card"><strong>Horários preferidos</strong><span>{portal.assistente.preferencias.horariosPreferidos}</span></div>
            <div className="gendaz-mini-card"><strong>Frequência</strong><span>{portal.assistente.preferencias.frequencia}</span></div>
          </div>
        </article>
      </div>
    </section>
  )
}
