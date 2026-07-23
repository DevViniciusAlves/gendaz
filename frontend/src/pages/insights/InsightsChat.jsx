import { useEffect, useRef, useState } from 'react'
import { Send } from 'lucide-react'

function mapearHistorico(historico) {
  if (!Array.isArray(historico) || historico.length === 0) return []

  return historico
    .slice()
    .reverse()
    .flatMap((item, index) => {
      const chave = item?.id ?? `hist-${index}`
      const mensagens = []

      if (item?.pergunta) {
        mensagens.push({
          id: `hist-user-${chave}`,
          origem: 'user',
          texto: item.pergunta,
        })
      }

      if (item?.resposta) {
        mensagens.push({
          id: `hist-assistant-${chave}`,
          origem: 'bot',
          texto: item.resposta,
        })
      }

      return mensagens
    })
}

export default function InsightsChat({ onEnviar, historico = [] }) {
  const [mensagens, setMensagens] = useState([
    { id: 'boas-vindas', origem: 'bot', texto: 'Pergunte sobre receita, clientes, serviços, profissionais ou oportunidades do negócio.' },
  ])
  const [entrada, setEntrada] = useState('')
  const [carregando, setCarregando] = useState(false)
  const ref = useRef(null)
  const historicoProcessadoRef = useRef(new Set())

  useEffect(() => {
    ref.current?.scrollTo({ top: ref.current.scrollHeight, behavior: 'smooth' })
  }, [mensagens])

  useEffect(() => {
    const mensagensHistorico = mapearHistorico(historico).filter((mensagem) => {
      const chave = String(mensagem.id)
      if (historicoProcessadoRef.current.has(chave)) {
        return false
      }
      historicoProcessadoRef.current.add(chave)
      return true
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

    setCarregando(true)
    try {
      const resposta = await onEnviar(pergunta, historicoParaEnviar)
      setMensagens((current) => [
        ...current,
        {
          id: `bot-${Date.now()}`,
          origem: 'bot',
          texto: resposta?.resposta || resposta || 'Sem resposta.',
        },
      ])
    } catch (error) {
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
    <section className="panel insights-chat">
      <div className="insights-chat__head">
        <h3>Chat IA - Insights</h3>
        <p>Pergunte sobre seu negócio</p>
      </div>

      <div className="insights-chat__messages" ref={ref}>
        {mensagens.map((mensagem) => (
          <div key={mensagem.id} className={`insights-chat__message insights-chat__message--${mensagem.origem}`}>
            <div className="insights-chat__bubble">{mensagem.texto}</div>
          </div>
        ))}
        {carregando && <div className="insights-chat__typing">Analisando...</div>}
      </div>

      <div className="insights-chat__form">
        <input
          className="chat-input"
          value={entrada}
          onChange={(e) => setEntrada(e.target.value)}
          placeholder="Faça uma pergunta ao consultor IA..."
          onKeyDown={(e) => {
            if (e.key === 'Enter') enviar()
          }}
        />
        <button type="button" className="btn btn-primary btn-send" onClick={enviar} disabled={!entrada.trim() || carregando}>
          <Send size={16} />
        </button>
      </div>
    </section>
  )
}
