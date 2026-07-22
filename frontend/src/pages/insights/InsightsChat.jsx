import { useEffect, useRef, useState } from 'react'
import { Send } from 'lucide-react'

export default function InsightsChat({ onEnviar, historico = [] }) {
  const [mensagens, setMensagens] = useState([
    { id: 1, origem: 'bot', texto: 'Pergunte sobre receita, clientes, serviços, profissionais ou oportunidades do negócio.' },
  ])
  const [entrada, setEntrada] = useState('')
  const [carregando, setCarregando] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    ref.current?.scrollTo({ top: ref.current.scrollHeight, behavior: 'smooth' })
  }, [mensagens])

  useEffect(() => {
    if (historico.length === 0) return
    const ultimos = historico.slice(0, 3).reverse().map((item, index) => ({
      id: `hist-${item.id || index}`,
      origem: 'bot',
      texto: item.resposta || item.pergunta || 'Análise registrada.',
    }))
    setMensagens((current) => {
      const base = current.length === 1 ? current : current.slice(0, 1)
      return [...base, ...ultimos]
    })
  }, [historico])

  async function enviar() {
    const pergunta = entrada.trim()
    if (!pergunta || carregando) return
    setEntrada('')
    setMensagens((current) => [...current, { id: Date.now(), origem: 'user', texto: pergunta }])
    setCarregando(true)
    try {
      const resposta = await onEnviar(pergunta)
      setMensagens((current) => [
        ...current,
        { id: Date.now() + 1, origem: 'bot', texto: resposta?.resposta || resposta || 'Sem resposta.' },
      ])
    } catch (error) {
      setMensagens((current) => [
        ...current,
        { id: Date.now() + 1, origem: 'bot', texto: error?.response?.data?.mensagem || 'Não foi possível analisar agora.' },
      ])
    } finally {
      setCarregando(false)
    }
  }

  return (
    <section className="panel insights-chat">
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
          value={entrada}
          onChange={(e) => setEntrada(e.target.value)}
          placeholder="Faça uma pergunta ao consultor IA..."
          onKeyDown={(e) => {
            if (e.key === 'Enter') enviar()
          }}
        />
        <button type="button" className="btn btn-primary" onClick={enviar} disabled={!entrada.trim() || carregando}>
          <Send size={16} />
        </button>
      </div>
    </section>
  )
}
