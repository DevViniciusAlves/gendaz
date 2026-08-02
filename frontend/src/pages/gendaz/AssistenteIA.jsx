import { useContext, useEffect, useRef, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, Bot, Calendar, Loader, Send, Sparkles } from 'lucide-react'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'
import clienteApi from '../../api/clienteApi.js'

const ATALHOS = [
  { label: 'Quero agendar', prompt: 'Quero agendar um horario.' },
  { label: 'Ver serviços', prompt: 'Quais servicos e precos voces oferecem?' },
  { label: 'Ver promoções', prompt: 'Quais promocoes e descontos estao disponiveis?' },
  { label: 'Cancelar', prompt: 'Quero cancelar um agendamento.' },
  { label: 'Reagendar', prompt: 'Quero reagendar um agendamento.' },
]

export default function AssistenteIA() {
  const { cliente } = useContext(ClienteGendazContext)
  const [mensagens, setMensagens] = useState([])
  const [inputValue, setInputValue] = useState('')
  const [carregando, setCarregando] = useState(false)
  const messagesEndRef = useRef(null)
  const navigate = useNavigate()

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [mensagens])

  const enviarPergunta = useCallback(async (pergunta) => {
    const textoUsuario = String(pergunta || '').trim()
    if (!textoUsuario || carregando) return

    const mensagemUsuario = { id: Date.now(), origem: 'cliente', texto: textoUsuario }
    const historicoAtual = [...mensagens, mensagemUsuario]

    setMensagens((prev) => [...prev, mensagemUsuario])
    setInputValue('')

    try {
      setCarregando(true)
      const historicoParaIA = historicoAtual
        .slice(-8)
        .map((item) => ({
          role: item.origem === 'ia' ? 'assistant' : 'user',
          content: String(item.texto || ''),
        }))

      const { data } = await clienteApi.post('/meu-gendaz/ia', {
        pergunta: textoUsuario,
        historico: historicoParaIA,
      })

      setMensagens((prev) => [...prev, {
        id: Date.now() + 1,
        origem: 'ia',
        texto: data?.resposta || 'Nao consegui obter resposta da gendazIA agora.',
        acao: data?.acao,
        sugestoes: Array.isArray(data?.sugestoes) ? data.sugestoes : [],
      }])
    } catch (err) {
      setMensagens((prev) => [...prev, {
        id: Date.now() + 1,
        origem: 'ia',
        texto: err?.response?.status === 401
          ? 'Sua sessao do Meu Gendaz expirou. Faca login novamente.'
          : 'Nao foi possivel obter resposta da gendazIA no momento.',
      }])
    } finally {
      setCarregando(false)
    }
  }, [carregando, mensagens])

  function handleSubmit(e) {
    e.preventDefault()
    void enviarPergunta(inputValue)
  }

  function handleAtalho(atalho) {
    if (atalho === 'Ir para Agenda') {
      navigate('agenda')
      return
    }
    void enviarPergunta(atalho)
  }

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">gendazIA</span>
        <h1>Converse naturalmente</h1>
        <p>Peca precos, servicos, profissionais, horarios, reagendamentos, promocoes e lista de espera.</p>
      </header>

      <div className="gendaz-grid gendaz-grid--two">
        <article className="gendaz-chat">
          <div className="gendaz-panel__head">
            <Bot size={18} />
            <h2>gendazIA</h2>
          </div>

          <div className="gendaz-chat__messages">
            {mensagens.length === 0 && !carregando && (
              <div className="gendaz-chat__empty">
                <Sparkles size={18} />
                <p>Envie uma mensagem para conversar com a gendazIA da {cliente?.empresaNome || 'sua empresa'}.</p>
              </div>
            )}

            {mensagens.map((item) => (
              <div key={item.id} className={`gendaz-chat__message gendaz-chat__message--${item.origem}`}>
                <div className="gendaz-chat__text">{item.texto}</div>
                {item.sugestoes && item.sugestoes.length > 0 && (
                  <div className="gendaz-chat__sugestoes">
                    {item.sugestoes.map((sug, i) => (
                      <button key={i} className="gendaz-btn gendaz-btn--small" onClick={() => handleAtalho(sug)}>
                        {sug}
                      </button>
                    ))}
                  </div>
                )}
                {item.acao && (
                  <div className="gendaz-chat__acao">
                    <Calendar size={14} /> Acao disponivel
                    <button className="gendaz-btn gendaz-btn--small gendaz-btn--primary" onClick={() => navigate('agenda')}>
                      <ArrowRight size={14} /> Ir para Agenda
                    </button>
                  </div>
                )}
              </div>
            ))}

            {carregando && (
              <div className="gendaz-chat__message gendaz-chat__message--ia gendaz-chat__message--loading">
                <Loader size={16} className="gendaz-spinner" /> <span>Digitando...</span>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          <form className="gendaz-chat__form" onSubmit={handleSubmit}>
            <input
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              placeholder="Digite sua mensagem..."
              disabled={carregando}
              autoFocus
            />
            <button className="gendaz-btn gendaz-btn--primary" type="submit" disabled={carregando || !inputValue.trim()}>
              <Send size={16} />
            </button>
          </form>
        </article>

        <article className="gendaz-panel">
          <div className="gendaz-panel__head">
            <Sparkles size={18} />
            <h2>Como posso ajudar</h2>
          </div>
          <div className="gendaz-stack">
            {ATALHOS.map((item) => (
              <button key={item.label} type="button" className="gendaz-mini-card" onClick={() => handleAtalho(item.prompt)}>
                <strong>{item.label}</strong>
                <span>{item.prompt}</span>
              </button>
            ))}
          </div>
        </article>
      </div>
    </section>
  )
}
