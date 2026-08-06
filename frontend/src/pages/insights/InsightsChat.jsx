import { useEffect, useRef, useState } from 'react'
import { Send, Sparkles, Target } from 'lucide-react'

const SUGESTOES = [
  'Como aumentar meu faturamento?',
  'Quais clientes devo recuperar?',
  'Qual serviço devo divulgar?',
]

function normalizarTexto(valor) {
  return String(valor ?? '').trim()
}

function criarChaveHistorico(item) {
  return [
    normalizarTexto(item?.pergunta).toLowerCase(),
    normalizarTexto(item?.resposta).toLowerCase(),
  ].join('::')
}

export default function InsightsChat({ onEnviar, historico = [] }) {
  const [mensagens, setMensagens] = useState([
    { id: 'boas-vindas', origem: 'bot', texto: 'Pergunte sobre receita, clientes, serviços, profissionais ou oportunidades do negócio.' },
  ])
  const [entrada, setEntrada] = useState('')
  const [carregando, setCarregando] = useState(false)
  const ref = useRef(null)
  const historicoProcessadoRef = useRef(new Set())
  const envioPendenteRef = useRef(null)

  useEffect(() => {
    ref.current?.scrollTo({ top: ref.current.scrollHeight, behavior: 'smooth' })
  }, [mensagens])

  useEffect(() => {
    function aplicarSugestao(evento) {
      const pergunta = normalizarTexto(evento?.detail?.pergunta)
      if (!pergunta) return
      setEntrada(pergunta)
    }

    window.addEventListener('agendapro:insights-suggestion', aplicarSugestao)
    return () => window.removeEventListener('agendapro:insights-suggestion', aplicarSugestao)
  }, [])

  useEffect(() => {
    const mensagensHistorico = []

    historico
      .slice()
      .reverse()
      .forEach((item, index) => {
        const chave = criarChaveHistorico(item)
        if (historicoProcessadoRef.current.has(chave)) return

        const pergunta = normalizarTexto(item?.pergunta)
        const resposta = normalizarTexto(item?.resposta)
        const envioPendente = envioPendenteRef.current
        const ehEnvioPendente = Boolean(
          envioPendente &&
          pergunta &&
          pergunta.toLowerCase() === envioPendente.pergunta &&
          resposta &&
          resposta.toLowerCase() === envioPendente.resposta
        )

        if (ehEnvioPendente) {
          if (resposta) {
            mensagensHistorico.push({
              id: `hist-assistant-${item?.id ?? `pendente-${index}`}`,
              origem: 'bot',
              texto: resposta,
            })
          }

          historicoProcessadoRef.current.add(chave)
          envioPendenteRef.current = null
          return
        }

        if (pergunta) {
          mensagensHistorico.push({
            id: `hist-user-${item?.id ?? `hist-${index}`}`,
            origem: 'user',
            texto: pergunta,
          })
        }

        if (resposta) {
          mensagensHistorico.push({
            id: `hist-assistant-${item?.id ?? `hist-${index}`}`,
            origem: 'bot',
            texto: resposta,
          })
        }

        historicoProcessadoRef.current.add(chave)
      })

    if (mensagensHistorico.length === 0) return

    setMensagens((current) => {
      const idsExistentes = new Set(current.map((item) => String(item.id)))
      const novas = mensagensHistorico.filter((item) => !idsExistentes.has(String(item.id)))
      return novas.length > 0 ? [...current, ...novas] : current
    })
  }, [historico])

  async function enviar() {
    const pergunta = entrada.trim()
    if (!pergunta || carregando) return

    const historicoParaEnviar = [
      ...mensagens
        .filter((item) => item.origem === 'user' || item.origem === 'bot')
        .map((item) => ({
          role: item.origem === 'bot' ? 'assistant' : 'user',
          content: item.texto,
        })),
      { role: 'user', content: pergunta },
    ]

    setEntrada('')
    setMensagens((current) => [
      ...current,
      { id: `user-${Date.now()}`, origem: 'user', texto: pergunta },
    ])

    envioPendenteRef.current = {
      pergunta: pergunta.toLowerCase(),
      resposta: '',
    }

    setCarregando(true)
    try {
      const resposta = await onEnviar(pergunta, historicoParaEnviar)
      const textoResposta = normalizarTexto(resposta?.resposta || resposta || 'Sem resposta.')
      setMensagens((current) => [
        ...current,
        {
          id: `bot-${Date.now()}`,
          origem: 'bot',
          texto: textoResposta,
        },
      ])
      envioPendenteRef.current = {
        pergunta: pergunta.toLowerCase(),
        resposta: textoResposta.toLowerCase(),
      }
    } catch (error) {
      envioPendenteRef.current = null
      setMensagens((current) => [
        ...current,
        {
          id: `erro-${Date.now()}`,
          origem: 'bot',
          texto: error?.response?.data?.mensagem || 'Não foi possível analisar agora.',
        },
      ])
    } finally {
      setCarregando(false)
    }
  }

  return (
    <section className="insights-chat">
      <header className="insights-chat__head">
        <div className="insights-chat__avatar" aria-hidden="true">
          <Sparkles size={15} />
        </div>
        <div className="insights-chat__head-info">
          <h3>Consultor IA</h3>
          <p>Respostas com base nos dados da sua empresa</p>
        </div>
      </header>

      <div className="insights-chat__messages" ref={ref}>
        {mensagens.map((mensagem) => (
          <div
            key={mensagem.id}
            className={`insights-chat__message insights-chat__message--${mensagem.origem}`}
          >
            <div className="insights-chat__bubble">{mensagem.texto}</div>
          </div>
        ))}
        {carregando && (
          <div className="insights-chat__typing" aria-live="polite">
            <span />
            <span />
            <span />
            <em>Analisando...</em>
          </div>
        )}
      </div>

      <div className="insights-chat__suggestions">
        <div className="insights-suggestions">
          {SUGESTOES.map((texto) => (
            <button
              key={texto}
              type="button"
              className="insights-suggestion"
              onClick={() => setEntrada(texto)}
            >
              <Target size={14} />
              <span>{texto}</span>
            </button>
          ))}
        </div>
      </div>

      <form
        className="insights-chat__form"
        onSubmit={(event) => {
          event.preventDefault()
          enviar()
        }}
      >
        <input
          className="chat-input"
          value={entrada}
          onChange={(e) => setEntrada(e.target.value)}
          placeholder="Faça uma pergunta ao consultor IA..."
          aria-label="Faça uma pergunta ao consultor IA"
        />
        <button
          type="submit"
          className="btn btn-primary btn-send"
          disabled={!entrada.trim() || carregando}
          aria-label="Enviar pergunta"
        >
          <Send size={16} />
        </button>
      </form>
    </section>
  )
}
