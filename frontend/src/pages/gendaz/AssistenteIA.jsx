import { useContext, useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bot, Send, Sparkles, Loader, Calendar, ArrowRight } from 'lucide-react'
import { ClienteGendazContext } from '../../contexts/ClienteGendazContext.jsx'
import clienteApi from '../../api/clienteApi.js'

export default function AssistenteIA() {
  const { cliente, agendamentos, dashboard, servicos, profissionais, beneficios } = useContext(ClienteGendazContext)
  const [mensagens, setMensagens] = useState([])
  const [inputValue, setInputValue] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [cardAtivo, setCardAtivo] = useState('Agendar')
  const messagesEndRef = useRef(null)
  const navigate = useNavigate()

  useEffect(() => {
    const nome = cliente?.nome || 'cliente'
    const empresaNome = cliente?.empresaNome || 'nosso estabelecimento'
    setMensagens([{
      id: 1,
      origem: 'ia',
      texto: `Olá, ${nome}! Sou a gendazIA da ${empresaNome}. Como posso ajudá-lo?`,
      sugestoes: ['Quero agendar', 'Ver serviços', 'Ver promoções'],
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
      const historicoParaIA = mensagens
        .slice(-8)
        .map((item) => ({
          role: item.origem === 'ia' ? 'assistant' : 'user',
          content: String(item.texto || '').replace(/\*\*/g, ''),
        }))

      const { data } = await clienteApi.post('/meu-gendaz/ia', {
        pergunta: textoUsuario,
        historico: historicoParaIA,
      })

      setMensagens((prev) => [...prev, {
        id: Date.now() + 1,
        origem: 'ia',
        texto: data?.resposta || 'Desculpe, não consegui processar sua mensagem.',
        acao: data?.acao,
        sugestoes: Array.isArray(data?.sugestoes) ? data.sugestoes : [],
      }])
    } catch (err) {
      setMensagens((prev) => [...prev, {
        id: Date.now() + 1,
        origem: 'ia',
        texto: err?.response?.status === 401
          ? 'Sua sessão do Meu Gendaz expirou. Faça login novamente.'
          : 'Não consegui obter resposta da gendazIA agora. Verifique se a Groq está configurada no backend.',
        sugestoes: ['Quero agendar', 'Ver serviços', 'Ver promoções'],
      }])
    } finally {
      setCarregando(false)
    }
  }, [inputValue, carregando, mensagens, cliente, agendamentos, dashboard, servicos, profissionais, beneficios])

  function handleSugestao(texto) {
    if (texto.startsWith('Ir para ')) {
      const rota = texto.replace('Ir para ', '').toLowerCase()
      const rotas = {
        agenda: 'agenda',
        histórico: 'historico',
        benefícios: 'beneficios',
        beneficios: 'beneficios',
        configurações: 'configuracoes',
        configuracoes: 'configuracoes',
        dashboard: 'dashboard',
      }
      const rotaEncontrada = rotas[rota]
      if (rotaEncontrada) {
        navigate(rotaEncontrada)
        return
      }
    }
    setInputValue(texto)
    setTimeout(() => {
      const fakeEvent = { preventDefault: () => {} }
      handleEnviar(fakeEvent)
    }, 100)
  }

  return (
    <section className="gendaz-page">
      <header className="gendaz-page__header">
        <span className="gendaz-kicker">gendazIA</span>
        <h1>Converse naturalmente</h1>
        <p>Peça preços, serviços, profissionais, horários, reagendamentos, promoções e lista de espera.</p>
      </header>

      <div className="gendaz-grid gendaz-grid--two">
        <article className="gendaz-chat">
          <div className="gendaz-panel__head">
            <Bot size={18} />
            <h2>gendazIA</h2>
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
                    <Calendar size={14} /> Ação disponível
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
            <button type="button" className={`gendaz-mini-card ${cardAtivo === 'Agendar' ? 'is-active' : ''}`} onClick={() => { setCardAtivo('Agendar'); handleSugestao('Quero agendar') }}>
              <strong>Agendar</strong>
              <span>Pe�a para agendar um servi�o e escolha data e hor�rio.</span>
            </button>
            <button type="button" className={`gendaz-mini-card ${cardAtivo === 'Reagendar' ? 'is-active' : ''}`} onClick={() => { setCardAtivo('Reagendar'); handleSugestao('Reagendar') }}>
              <strong>Reagendar</strong>
              <span>Mude a data ou hora de um agendamento existente.</span>
            </button>
            <button type="button" className={`gendaz-mini-card ${cardAtivo === 'Cancelar' ? 'is-active' : ''}`} onClick={() => { setCardAtivo('Cancelar'); handleSugestao('Cancelar') }}>
              <strong>Cancelar</strong>
              <span>Cancele um agendamento que nao pode comparecer.</span>
            </button>
            <button type="button" className={`gendaz-mini-card ${cardAtivo === 'Servi�os e pre�os' ? 'is-active' : ''}`} onClick={() => { setCardAtivo('Servi�os e pre�os'); handleSugestao('Quais servi�os voc�s oferecem?') }}>
              <strong>Servi�os e pre�os</strong>
              <span>Veja a lista completa de servi�os e valores.</span>
            </button>
            <button type="button" className={`gendaz-mini-card ${cardAtivo === 'Promo��es' ? 'is-active' : ''}`} onClick={() => { setCardAtivo('Promo��es'); handleSugestao('Promo��es') }}>
              <strong>Promo��es</strong>
              <span>Confira cupons e descontos dispon�veis.</span>
            </button>
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
  if (/agendar|marcar|reservar|quero.*agendar|gostaria.*agendar|solicitar.*agendamento/.test(t)) return 'agendar'
  if (/listar|quais.*servicos|servicos.*disponiveis|o que.*oferecem|precos|valores|tabela|quais.*serviços/.test(t)) return 'listar_servicos'
  if (/profissionais|quem.*atende|barbeiro|cabeleireiro|equipe|funcionarios|quem.*trabalha/.test(t)) return 'listar_profissionais'
  if (/horarios|horario.*disponivel|funcionamento|aberto|que horas/.test(t)) return 'listar_horarios'
  if (/proximo|meus.*agendamentos|agendamentos.*futuros|quando.*proximo|meus agendamentos/.test(t)) return 'meus_agendamentos'
  if (/historico|passado|anterior|ultimos/.test(t)) return 'historico'
  if (/promo|cupom|desconto|beneficio|oferta/.test(t)) return 'promocoes'
  if (/quem.*voce|o que.*faz|como.*funciona|ajuda|help|qual.*seu.*nome/.test(t)) return 'sobre'
  if (/obrigad|valeu|thanks|agrade/.test(t)) return 'agradecimento'
  if (/oi|ola|bom dia|boa tarde|boa noite|hey|eai|fala/.test(t)) return 'saudacao'
  return 'geral'
}

function gerarRespostaLocal(intencao, texto, contexto) {
  const { cliente, agendamentos, servicos, profissionais, beneficios } = contexto
  const nome = cliente?.nome || 'cliente'
  const empresaNome = cliente?.empresaNome || 'nosso estabelecimento'

  switch (intencao) {
    case 'saudacao': {
      const hora = new Date().getHours()
      const periodo = hora < 12 ? 'Bom dia' : hora < 18 ? 'Boa tarde' : 'Boa noite'
      return {
        resposta: `${periodo}, ${nome}! Como posso ajudá-lo? Posso agendar, reagendar, cancelar, listar serviços ou responder dúvidas.`,
        sugestoes: ['Quero agendar', 'Ver meus agendamentos', 'Quais serviços vocês têm?'],
      }
    }
    case 'sobre': {
      return {
        resposta: `Sou a assistente virtual da ${empresaNome}. Posso ajudar com agendamentos, reagendamentos, cancelamentos, serviços, preços, profissionais e promoções.`,
        sugestoes: ['Quero agendar', 'Ver serviços', 'Ver promoções'],
      }
    }
    case 'agradecimento': {
      return {
        resposta: `Por nada, ${nome}. Estou sempre aqui quando precisar da ${empresaNome}.`,
        sugestoes: ['Quero agendar', 'Ver meus agendamentos'],
      }
    }
    case 'listar_servicos': {
      if (!servicos || servicos.length === 0) {
        return {
          resposta: `${nome}, no momento não consigo listar os serviços. Acesse a aba Agenda para ver todos os serviços disponíveis na ${empresaNome}.`,
          sugestoes: ['Ir para Agenda'],
        }
      }
      const lista = servicos.map((s, i) => `${i + 1}. **${s.nome || s.titulo}** — ${Number(s.valor || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}`).join('\n')
      return {
        resposta: `Serviços da ${empresaNome}:\n\n${lista}\n\nQuer agendar algum?`,
        sugestoes: ['Quero agendar', 'Ir para Agenda', 'Ver profissionais'],
      }
    }
    case 'listar_profissionais': {
      if (!profissionais || profissionais.length === 0) {
        return {
          resposta: `${nome}, não consigo listar os profissionais agora. Ao agendar na aba Agenda, você poderá escolher o profissional.`,
          sugestoes: ['Ir para Agenda'],
        }
      }
      const lista = profissionais.map((p, i) => `${i + 1}. **${p.nome}**`).join('\n')
      return {
        resposta: `Equipe da ${empresaNome}:\n\n${lista}\n\nQuer agendar com algum deles?`,
        sugestoes: profissionais.slice(0, 3).map((p) => `Agendar com ${p.nome}`),
      }
    }
    case 'meus_agendamentos': {
      if (!agendamentos || agendamentos.length === 0) {
        return {
          resposta: `${nome}, você não possui agendamentos futuros na ${empresaNome}. Que tal agendar um novo serviço?`,
          sugestoes: ['Quero agendar', 'Ver serviços'],
        }
      }
      const lista = agendamentos.slice(0, 3).map((a) => `${a.servicoNome || a.servico || 'Serviço'} em ${a.data ? new Date(`${a.data}T12:00:00`).toLocaleDateString('pt-BR') : 'data indefinida'}`).join('\n')
      return {
        resposta: `Seus próximos agendamentos:\n\n${lista}`,
        sugestoes: ['Ir para Agenda', 'Reagendar'],
      }
    }
    case 'historico': {
      return {
        resposta: `Você pode ver seu histórico na aba Histórico. Se quiser, eu também posso te orientar a reagendar ou agendar um novo atendimento.`,
        sugestoes: ['Ir para Histórico', 'Quero agendar'],
      }
    }
    case 'cancelar': {
      return {
        resposta: `Posso te orientar no cancelamento. Abra a aba Agenda, escolha o agendamento e clique em Cancelar.`,
        sugestoes: ['Ir para Agenda', 'Ver meus agendamentos'],
      }
    }
    case 'reagendar': {
      return {
        resposta: `Para reagendar, abra a aba Agenda, selecione o atendimento e escolha uma nova data e horário.`,
        sugestoes: ['Ir para Agenda', 'Ver meus agendamentos'],
      }
    }
    case 'listar_horarios': {
      return {
        resposta: `Os horários disponíveis aparecem na aba Agenda, após selecionar o serviço e, quando necessário, o profissional.`,
        sugestoes: ['Ir para Agenda', 'Quero agendar'],
      }
    }
    case 'promocoes': {
      const promos = beneficios?.promocoes || []
      if (!promos || promos.length === 0) {
        return {
          resposta: `No momento não encontrei promoções cadastradas para ${empresaNome}.`,
          sugestoes: ['Quero agendar', 'Ver serviços'],
        }
      }
      const lista = promos.map((p) => `${p.titulo || 'Promoção'} - ${p.descricao || ''}`).join('\n')
      return {
        resposta: `Promoções disponíveis:\n\n${lista}`,
        sugestoes: ['Quero agendar', 'Ver serviços'],
      }
    }
    default: {
      return {
        resposta: `Posso ajudar com agendamentos, reagendamentos, cancelamentos, serviços, preços, profissionais e promoções. Se quiser, me diga o que precisa.`,
        sugestoes: ['Quero agendar', 'Ver serviços', 'Ver promoções'],
      }
    }
  }
}



