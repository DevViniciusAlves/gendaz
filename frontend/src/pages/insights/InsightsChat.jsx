import { useEffect, useRef, useState } from 'react'
import { Bot, Loader, Send, Sparkles } from 'lucide-react'

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
  const [mensagens, setMensagens] = useState([])
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
              origem: 'ia',
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
            origem: 'cliente',
            texto: pergunta,
          })
        }

        if (resposta) {
          mensagensHistorico.push({
            id: `hist-assistant-${item?.id ?? `hist-${index}`}`,
            origem: 'ia',
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
        .filter((item) => item.origem === 'cliente' || item.origem === 'ia')
        .map((item) => ({
          role: item.origem === 'ia' ? 'assistant' : 'user',
          content: item.texto,
        })),
      { role: 'user', content: pergunta },
    ]

    setEntrada('')
    setMensagens((current) => [
      ...current,
      { id: `cliente-${Date.now()}`, origem: 'cliente', texto: pergunta },
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
          id: `ia-${Date.now()}`,
          origem: 'ia',
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
          origem: 'ia',
          texto: error?.response?.data?.mensagem || 'Não foi possível analisar agora.',
        },
      ])
    } finally {
      setCarregando(false)
    }
  }

  return (
    <section className="gendaz-chat gendaz-chat--insights">
      <div className="gendaz-panel__head">
        <Bot size={18} />
        <h2>gendazIA</h2>
      </div>

      <div className="gendaz-chat__messages" ref={ref}>
        {mensagens.length === 0 && !carregando && (
          <div className="gendaz-chat__empty">
            <Sparkles size={18} />
            <p>Pergunte sobre receita, clientes, serviços, profissionais ou oportunidades do negócio.</p>
          </div>
        )}

        {mensagens.map((mensagem) => (
          <div
            key={mensagem.id}
            className={`gendaz-chat__message gendaz-chat__message--${mensagem.origem}`}
          >
            <div className="gendaz-chat__text">{mensagem.texto}</div>
          </div>
        ))}

        {carregando && (
          <div className="gendaz-chat__message gendaz-chat__message--ia gendaz-chat__message--loading">
            <Loader size={16} className="gendaz-spinner" />
            <span>Analisando...</span>
          </div>
        )}
      </div>

      <div className="gendaz-chat__sugestoes gendaz-chat__sugestoes--base">
        {SUGESTOES.map((sugestao) => (
          <button
            key={sugestao}
            type="button"
            className="gendaz-btn gendaz-btn--small"
            onClick={() => setEntrada(sugestao)}
          >
            {sugestao}
          </button>
        ))}
      </div>

      <form
        className="gendaz-chat__form"
        onSubmit={(event) => {
          event.preventDefault()
          enviar()
        }}
      >
        <input
          value={entrada}
          onChange={(e) => setEntrada(e.target.value)}
          placeholder="Faça uma pergunta à gendazIA..."
          aria-label="Faça uma pergunta à gendazIA"
          disabled={carregando}
        />
        <button
          type="submit"
          className="gendaz-btn gendaz-btn--primary"
          disabled={!entrada.trim() || carregando}
          aria-label="Enviar pergunta"
        >
          <Send size={16} />
        </button>
      </form>
    </section>
  )
}
