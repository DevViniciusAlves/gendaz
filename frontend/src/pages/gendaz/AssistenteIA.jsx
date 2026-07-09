import { Send } from 'lucide-react'
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
        <p>Peça preços, horários, reagendamento, promoções e suporte contextual.</p>
      </header>

      <article className="gendaz-chat">
        <div className="gendaz-chat__messages">
          {portal.assistente.mensagens.map((item) => (
            <div key={item.id} className={`gendaz-chat__message gendaz-chat__message--${item.origem}`}>
              {item.texto}
            </div>
          ))}
        </div>
        <form className="gendaz-chat__form" onSubmit={handleSubmit}>
          <input value={mensagem} onChange={(event) => setMensagem(event.target.value)} placeholder="Digite sua mensagem" />
          <button className="gendaz-btn gendaz-btn--primary" type="submit"><Send size={16} />Enviar</button>
        </form>
        <div className="gendaz-chat__prefs">
          <strong>Preferências aprendidas</strong>
          <p>{portal.assistente.preferencias.profissionalFavorito}</p>
          <p>{portal.assistente.preferencias.servicoFavorito}</p>
          <p>{portal.assistente.preferencias.diasPreferidos}</p>
          <p>{portal.assistente.preferencias.horariosPreferidos}</p>
        </div>
      </article>
    </section>
  )
}
