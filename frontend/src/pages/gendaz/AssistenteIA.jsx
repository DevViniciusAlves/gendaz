import { useContext, useState, useEffect, useRef, useCallback } from 'react'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'
import { Bot, Send, Sparkles, Loader, Calendar } from 'lucide-react'

export default function AssistenteIA() {
  const { cliente, agendamentos, dashboard, servicos, profissionais, beneficios } = useContext(ClienteGendazContext)
  const [mensagens, setMensagens] = useState([])
  const [inputValue, setInputValue] = useState('')
  const [carregando, setCarregando] = useState(false)
  const messagesEndRef = useRef(null)

  useEffect(() => {
    const nome = cliente?.nome || 'cliente'
    setMensagens([{
      id: 1,
      origem: 'ia',
      texto: `Olá, ${nome}! 👋 Sou sua assistente virtual. Posso ajudar com agendamentos, preços, serviços, horários e promoções. Como posso ajudá-lo?`,
    }])
  }, [cliente])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [mensagens])

  const handleEnviar = useCallback(async (e) => {
    if (e) e.preventDefault()
    if (!inputValue.trim() || carregando) return

    const textoUsuario = inputValue.trim()
    const novaMensagem = { id: Date.now(), origem: 'cliente', texto: textoUsuario }
    setMensagens((prev) => [...prev, novaMensagem])
    setInputValue('')

    try {
      setCarregando(true)

      const intencao = detectarIntencao(textoUsuario)
      const resposta = gerarRespostaLocal(intencao, textoUsuario, {
        cliente,
        agendamentos,
        dashboard,
        servicos: servicos || [],
        profissionais: profissionais || [],
        promos: beneficios?.promocoes || dashboard?.promocoes || [],
      })

      await new Promise((r) => setTimeout(r, 400))

      setMensagens((prev) => [...prev, {
        id: Date.now() + 1,
        origem: 'ia',
        texto: resposta.resposta || 'Desculpe, não consegui processar sua mensagem.',
        acao: resposta.acao,
        sugestoes: resposta.sugestoes,
      }])
    } catch {
      setMensagens((prev) => [...prev, {
        id: Date.now() + 1,
        origem: 'ia',
        texto: 'Desculpe, ocorreu um erro ao processar sua mensagem. Tente novamente.',
      }])
    } finally {
      setCarregando(false)
    }
  }, [inputValue, carregando, mensagens, cliente, agendamentos, dashboard, servicos, profissionais, beneficios])

  function handleSugestao(texto) {
    setInputValue(texto)
    setTimeout(() => {
      const fakeEvent = { preventDefault: () => {} }
      handleEnviar(fakeEvent)
    }, 100)
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
            {mensagens.map((item) => (
              <div key={item.id} className={`gendaz-chat__message gendaz-chat__message--${item.origem}`}>
                <div className="gendaz-chat__text">{item.texto}</div>
                {item.sugestoes && item.sugestoes.length > 0 && (
                  <div className="gendaz-chat__sugestoes">
                    {item.sugestoes.map((sug, i) => (
                      <button key={i} className="gendaz-btn gendaz-btn--small" onClick={() => handleSugestao(sug)}>
                        {sug}
                      </button>
                    ))}
                  </div>
                )}
                {item.acao && (
                  <div className="gendaz-chat__acao">
                    <Calendar size={14} /> Ação: {item.acao.tipo}
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
          <form className="gendaz-chat__form" onSubmit={handleEnviar}>
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
            <div className="gendaz-mini-card">
              <strong>Agendar</strong>
              <span>Peça para agendar um serviço e escolha data e horário.</span>
            </div>
            <div className="gendaz-mini-card">
              <strong>Reagendar</strong>
              <span>Mude a data ou hora de um agendamento existente.</span>
            </div>
            <div className="gendaz-mini-card">
              <strong>Cancelar</strong>
              <span>Cancele um agendamento que não pode comparecer.</span>
            </div>
            <div className="gendaz-mini-card">
              <strong>Serviços e preços</strong>
              <span>Veja a lista completa de serviços e valores.</span>
            </div>
            <div className="gendaz-mini-card">
              <strong>Promoções</strong>
              <span>Confira cupons e descontos disponíveis.</span>
            </div>
          </div>
        </article>
      </div>
    </section>
  )
}

function detectarIntencao(texto) {
  const t = texto.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  if (/cancelar|remover|desmarcar|desistir/.test(t)) return 'cancelar'
  if (/reagendar|remarcar|mudar.*agendamento|trocar.*horario|trocar.*data/.test(t)) return 'reagendar'
  if (/agendar|marcar|reservar|quero|gostaria|solicitar/.test(t)) return 'agendar'
  if (/listar|quais.*servicos|servicos.*disponiveis|o que.*oferecem|precos|valores|tabela/.test(t)) return 'listar_servicos'
  if (/profissionais|quem.*atende|barbeiro|cabeleireiro|equipe|funcionarios/.test(t)) return 'listar_profissionais'
  if (/horarios|horario.*disponivel|funcionamento|aberto/.test(t)) return 'listar_horarios'
  if (/proximo|meus.*agendamentos|agendamentos.*futuros|quando.*proximo/.test(t)) return 'meus_agendamentos'
  if (/historico|passado|anterior|ultimos/.test(t)) return 'historico'
  if (/promo|cupom|desconto|beneficio|oferta/.test(t)) return 'promocoes'
  if (/quem.*voce|o que.*faz|como.*funciona|ajuda|help/.test(t)) return 'sobre'
  if (/obrigad|valeu|thanks/.test(t)) return 'agradecimento'
  if (/oi|ola|bom dia|boa tarde|boa noite|hey/.test(t)) return 'saudacao'
  return 'geral'
}

function gerarRespostaLocal(intencao, texto, contexto) {
  const { cliente, agendamentos, servicos, profissionais, promos } = contexto
  const nome = cliente?.nome || 'cliente'

  switch (intencao) {
    case 'saudacao': {
      const hora = new Date().getHours()
      const periodo = hora < 12 ? 'Bom dia' : hora < 18 ? 'Boa tarde' : 'Boa noite'
      return {
        resposta: `${periodo}, ${nome}! 😊 Como posso ajudá-lo? Posso agendar, reagendar, cancelar, listar serviços ou responder dúvidas.`,
        sugestoes: ['Quero agendar', 'Ver meus agendamentos', 'Quais serviços vocês têm?'],
      }
    }
    case 'sobre': {
      return {
        resposta: `Sou a assistente virtual do estabelecimento! Posso ajudar com:\n\n• Agendar serviços\n• Reagendar compromissos\n• Cancelar agendamentos\n• Listar serviços e preços\n• Consultar promoções\n\nBasta me dizer o que precisa!`,
        sugestoes: ['Quero agendar', 'Ver serviços', 'Ver promoções'],
      }
    }
    case 'agradecimento': {
      return { resposta: `Por nada, ${nome}! 😊 Estou sempre aqui quando precisar.` }
    }
    case 'listar_servicos': {
      if (!servicos || servicos.length === 0) {
        return {
          resposta: `${nome}, no momento não consigo listar os serviços. Acesse a aba **Agenda** para ver todos os serviços disponíveis.`,
          sugestoes: ['Ir para Agenda'],
        }
      }
      const lista = servicos.map((s, i) => `${i + 1}. ${s.nome || s.titulo} — R$ ${Number(s.valor || 0).toFixed(2)}`).join('\n')
      return {
        resposta: `Serviços disponíveis:\n\n${lista}\n\nQuer agendar algum?`,
        sugestoes: servicos.slice(0, 3).map(s => `Agendar ${s.nome || s.titulo}`),
      }
    }
    case 'listar_profissionais': {
      if (!profissionais || profissionais.length === 0) {
        return {
          resposta: `${nome}, não consigo listar os profissionais agora. Ao agendar, você poderá escolher o profissional.`,
          sugestoes: ['Ir para Agenda'],
        }
      }
      const lista = profissionais.map((p, i) => `${i + 1}. ${p.nome}`).join('\n')
      return {
        resposta: `Nossa equipe:\n\n${lista}\n\nQuer agendar com algum deles?`,
        sugestoes: profissionais.slice(0, 3).map(p => `Agendar com ${p.nome}`),
      }
    }
    case 'meus_agendamentos': {
      if (!agendamentos || agendamentos.length === 0) {
        return {
          resposta: `${nome}, você não possui agendamentos futuros. Que tal agendar um novo serviço?`,
          sugestoes: ['Quero agendar', 'Ver serviços'],
        }
      }
      const lista = agendamentos.map((a, i) =>
        `${i + 1}. ${a.servicoNome || a.servico || 'Serviço'} — ${a.data ? new Date(a.data + 'T12:00:00').toLocaleDateString('pt-BR') : '?'} às ${a.horaInicio || a.hora || '?'} com ${a.profissionalNome || a.profissional || '?'} [${a.status}]`
      ).join('\n')
      return {
        resposta: `Seus próximos agendamentos:\n\n${lista}\n\nPrecisa reagendar ou cancelar algum?`,
        sugestoes: ['Reagendar', 'Cancelar'],
      }
    }
    case 'cancelar': {
      if (!agendamentos || agendamentos.length === 0) {
        return { resposta: `${nome}, você não possui agendamentos para cancelar.` }
      }
      return {
        resposta: `Para cancelar, acesse a aba **Agenda**, clique em "Cancelar" no agendamento desejado e confirme.`,
        sugestoes: ['Ir para Agenda'],
      }
    }
    case 'reagendar': {
      if (!agendamentos || agendamentos.length === 0) {
        return { resposta: `${nome}, você não possui agendamentos para reagendar.` }
      }
      return {
        resposta: `Para reagendar, acesse a aba **Agenda**, clique em "Reagendar" e escolha nova data/horário.`,
        sugestoes: ['Ir para Agenda'],
      }
    }
    case 'promocoes': {
      if (!promos || promos.length === 0) {
        return {
          resposta: `${nome}, no momento não há promoções ativas. Acesse a aba **Benefícios** para ficar por dentro!`,
          sugestoes: ['Ir para Benefícios'],
        }
      }
      const lista = promos.map((p, i) =>
        `${i + 1}. ${p.titulo} — ${p.desconto}% OFF${p.cupom ? ` (Cupom: ${p.cupom})` : ''}\n   ${p.descricao}`
      ).join('\n\n')
      return {
        resposta: `Promoções disponíveis:\n\n${lista}\n\nQuer agendar aproveitando alguma promoção?`,
        sugestoes: ['Quero agendar', 'Ir para Benefícios'],
      }
    }
    case 'agendar': {
      return {
        resposta: `${nome}, vou te ajudar a agendar! 🗓️\n\nPara criar um novo agendamento:\n1. Acesse a aba **Agenda**\n2. Clique em **"Novo agendamento"**\n3. Escolha serviço, profissional, data e horário\n4. Confirme!\n\nQuer que eu te leve para lá?`,
        sugestoes: ['Ir para Agenda', 'Ver serviços'],
      }
    }
    case 'historico': {
      return {
        resposta: `${nome}, para ver seu histórico, acesse a aba **Histórico** na sidebar.`,
        sugestoes: ['Ir para Histórico'],
      }
    }
    case 'listar_horarios': {
      return {
        resposta: `${nome}, os horários dependem do serviço e profissional. Vá na aba **Agenda**, clique em "Novo agendamento" e selecione serviço, profissional e data para ver os horários disponíveis.`,
        sugestoes: ['Ir para Agenda', 'Ver meus agendamentos'],
      }
    }
    default: {
      return {
        resposta: `${nome}, posso ajudar com:\n\n• **Agendar** um serviço\n• **Reagendar** compromisso\n• **Cancelar** agendamento\n• **Serviços** e preços\n• **Promoções** e cupons\n\nÉ só me dizer o que precisa!`,
        sugestoes: ['Quero agendar', 'Ver meus agendamentos', 'Serviços e preços'],
      }
    }
  }
}
