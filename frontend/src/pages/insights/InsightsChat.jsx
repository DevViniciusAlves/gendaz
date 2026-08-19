import { useEffect, useRef, useState } from 'react'
import { Bot, HelpCircle, Loader, Send, Sparkles } from 'lucide-react'
import { getSessionUser } from '../../api/axiosConfig.js'

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

function chaveArmazenamento() {
  const usuario = getSessionUser()
  const empresaId = usuario?.empresaId || 'local'
  const usuarioId = usuario?.id || 'anon'
  return `gendaz_insights_chat_${empresaId}_${usuarioId}`
}

const chatMemory = new Map()

function carregarMensagensSalvas() {
  try {
    const chave = chaveArmazenamento()
    const salvado = chatMemory.get(chave)
    return Array.isArray(salvado?.mensagens) ? salvado.mensagens : []
  } catch {
    return []
  }
}

export default function InsightsChat({ aberto = true, onToggle, onEnviar, historico = [] }) {
  const [mensagens, setMensagens] = useState(() => carregarMensagensSalvas())
  const [entrada, setEntrada] = useState('')
  const [carregando, setCarregando] = useState(false)
  const mensagensRef = useRef(null)
  const historicoProcessadoRef = useRef(new Set())
  const envioPendenteRef = useRef(null)

  useEffect(() => {
    try {
      chatMemory.set(chaveArmazenamento(), { mensagens })
    } catch {
      // Se o storage falhar, o chat continua funcionando sem persistir.
    }
  }, [mensagens])

  useEffect(() => {
    mensagensRef.current?.scrollTo({
      top: mensagensRef.current.scrollHeight,
      behavior: 'smooth',
    })
  }, [mensagens, carregando])

  useEffect(() => {
    function aplicarSugestao(evento) {
      const pergunta = normalizarTexto(evento?.detail?.pergunta)
      if (pergunta) setEntrada(pergunta)
    }

    window.addEventListener('gendaz:insights-suggestion', aplicarSugestao)
    return () => window.removeEventListener('gendaz:insights-suggestion', aplicarSugestao)
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
      const textosExistentes = new Set(current.map((item) => normalizarTexto(item.texto).toLowerCase()))
      const novas = mensagensHistorico.filter((item) => {
        if (idsExistentes.has(String(item.id))) return false
        return !textosExistentes.has(normalizarTexto(item.texto).toLowerCase())
      })

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
    <section className={`insights-ai-chat ${aberto ? 'is-open' : 'is-closed'}`}>
      <header className="insights-ai-chat__header">
        <div>
          <div className="section-kicker">IA Gendaz</div>
          <h2>gendazIA</h2>
        </div>
        <button
          type="button"
          className="icon-btn insights-ai-chat__toggle"
          onClick={onToggle}
          aria-label="Abrir ou fechar chat"
        >
          <HelpCircle size={18} />
        </button>
      </header>

      {aberto && (
        <div className="insights-ai-chat__body">
          <div className="insights-ai-chat__messages" ref={mensagensRef}>
            {mensagens.length === 0 && !carregando && (
              <div className="insights-ai-chat__empty">
                <Bot size={16} />
                <strong>gendazIA</strong>
                <Sparkles size={16} />
                <p>Pergunte sobre receita, clientes, serviços, profissionais ou oportunidades do negócio.</p>
              </div>
            )}

            {mensagens.map((mensagem) => (
              <div
                key={mensagem.id}
                className={`insights-ai-chat__message insights-ai-chat__message--${mensagem.origem}`}
              >
                <div className="insights-ai-chat__bubble">{mensagem.texto}</div>
              </div>
            ))}

            {carregando && (
              <div className="insights-ai-chat__message insights-ai-chat__message--ia">
                <div className="insights-ai-chat__bubble insights-ai-chat__bubble--loading">
                  <Loader size={14} className="gendaz-spinner" />
                  <span>Analisando...</span>
                </div>
              </div>
            )}
          </div>

          <div className="insights-ai-chat__suggestions">
            {SUGESTOES.map((sugestao) => (
              <button
                key={sugestao}
                type="button"
                className="insights-ai-chat__suggestion"
                onClick={() => setEntrada(sugestao)}
              >
                {sugestao}
              </button>
            ))}
          </div>

          <form
            className="insights-ai-chat__form"
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
              className="insights-ai-chat__send"
              disabled={!entrada.trim() || carregando}
              aria-label="Enviar pergunta"
            >
              <Send size={16} />
            </button>
          </form>
        </div>
      )}
    </section>
  )
}

