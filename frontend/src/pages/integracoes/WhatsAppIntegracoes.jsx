import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { MessageCircle } from 'lucide-react'
import Button from '../../components/Button.jsx'
import StatusBadge from '../../components/StatusBadge.jsx'
import { buscarResumoWhatsapp } from '../../api/whatsappApi.js'
import WhatsAppModal from './WhatsAppModal.jsx'
import './whatsapp.css'

// Retry controlado para indisponibilidade temporária (ex.: cold start do
// Node no Render): cada request acorda o serviço; o estado se recupera
// sozinho sem refresh manual. Limitado (sem keep-alive infinito) e sempre
// com cleanup no unmount.
const BACKOFF_RETRY_MS = [5000, 10000, 15000, 30000]
// Transitório = falha de comunicação com o Node. Demais estados são
// estáveis (precisam de ação do usuário ou de configuração).
const ESTADO_TRANSITORIO = 'UNAVAILABLE'

export default function WhatsAppIntegracoes() {
  const [resumo, setResumo] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [reconectando, setReconectando] = useState(false)
  const [modalAberto, setModalAberto] = useState(false)
  const timerRef = useRef(null)
  const tentativaRef = useRef(0)

  const limparRetry = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }, [])

  const carregar = useCallback(async ({ silencioso = false } = {}) => {
    limparRetry()
    if (!silencioso) {
      setCarregando(true)
      tentativaRef.current = 0
    }
    setErro('')
    try {
      const dados = await buscarResumoWhatsapp()
      setResumo(dados)
      setReconectando(false)
      tentativaRef.current = 0
    } catch (err) {
      setErro(err?.response?.data?.mensagem || 'Não foi possível consultar a integração agora. Tente novamente em alguns instantes.')
    } finally {
      setCarregando(false)
    }
  }, [limparRetry])

  useEffect(() => {
    carregar()
  }, [carregar])

  // Sai sozinho do estado temporário: nova consulta com backoff enquanto
  // houver erro de comunicação ou estado UNAVAILABLE. Para em estado
  // estável, ao esgotar as tentativas ou ao desmontar.
  useEffect(() => {
    if (carregando) return
    const transitorio = Boolean(erro) || resumo?.conexao?.estado === ESTADO_TRANSITORIO
    if (!transitorio) {
      setReconectando(false)
      tentativaRef.current = 0
      return
    }
    const tentativa = tentativaRef.current
    if (tentativa >= BACKOFF_RETRY_MS.length) {
      setReconectando(false)
      return
    }
    setReconectando(true)
    timerRef.current = setTimeout(() => {
      tentativaRef.current += 1
      carregar({ silencioso: true })
    }, BACKOFF_RETRY_MS[tentativa])
    return limparRetry
  }, [carregando, erro, resumo, carregar, limparRetry])

  useEffect(() => () => limparRetry(), [limparRetry])

  return (
    <section className="panel settings-form-panel">
      <div className="panel-head settings-form-head">
        <div>
          <span className="section-kicker">Integrações</span>
          <h2>WhatsApp</h2>
          <p>Lembretes automáticos e ações de CRM pelo WhatsApp.</p>
        </div>
        <MessageCircle size={22} color="var(--primary)" />
      </div>

      {carregando && !reconectando && <p className="wpp-muted">Carregando integração...</p>}
      {!carregando && erro && (
        <>
          <p className="wpp-muted">{reconectando ? 'Reconectando...' : 'Temporariamente indisponível'}</p>
          {!reconectando && <p className="form-error">{erro}</p>}
          {!reconectando && <Button variant="secondary" type="button" onClick={() => carregar()}>Tentar novamente</Button>}
        </>
      )}
      {!carregando && !erro && resumo && (
        <div className="wpp-card-body">
          <div className="wpp-card-status">
            <StatusBadge status={resumo.conexao?.estado || 'UNAVAILABLE'} />
            {resumo.disponivelNoPlano ? (
              <span className="wpp-muted">Lembretes automáticos e ações de CRM pelo WhatsApp.</span>
            ) : (
              <span className="wpp-muted">Não disponível no seu plano</span>
            )}
          </div>
          {resumo.disponivelNoPlano || ['CONNECTED', 'CONNECTING', 'RECONNECTING'].includes(resumo.conexao?.estado) ? (
            <Button type="button" onClick={() => setModalAberto(true)}>Configurar</Button>
          ) : (
            <Link to="/sistema/planos" className="btn btn-secondary">Ver planos</Link>
          )}
        </div>
      )}

      <WhatsAppModal
        open={modalAberto}
        resumo={resumo}
        onClose={() => setModalAberto(false)}
        onResumoAtualizado={setResumo}
      />
    </section>
  )
}
