import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Button from '../../components/Button.jsx'
import ConfirmacaoModal from '../../components/ConfirmacaoModal.jsx'
import StatusBadge from '../../components/StatusBadge.jsx'
import { buscarResumoWhatsapp, desconectarWhatsapp } from '../../api/whatsappApi.js'
import WhatsAppModal from './WhatsAppModal.jsx'
import whatsappLogo from '../../assets/logos/whatsapp-logo.png'
import './whatsapp.css'

function emitirToast(type, message) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('gendaz:toast', { detail: { type, message } }))
}
// Retry controlado para indisponibilidade temporária (ex.: cold start do
// Node no Render): cada request acorda o serviço; o estado se recupera
// sozinho sem refresh manual. Limitado (sem keep-alive infinito) e sempre
// com cleanup no unmount.
const BACKOFF_RETRY_MS = [5000, 10000, 15000, 30000]
// Transitório = falha de comunicação com o Node. Demais estados são
// estáveis (precisam de ação do usuário ou de configuração).
const ESTADO_TRANSITORIO = 'UNAVAILABLE'
// Estados em que o servidor pode estar tentando reconectar automaticamente
const ESTADOS_RECONECTANDO = ['CONNECTING', 'RECONNECTING']

export default function WhatsAppIntegracoes() {
  const [resumo, setResumo] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [reconectando, setReconectando] = useState(false)
  const [modalAberto, setModalAberto] = useState(false)
  const [confirmarConectar, setConfirmarConectar] = useState(false)
  const [confirmarDesconectar, setConfirmarDesconectar] = useState(false)
  const [autoConnectToken, setAutoConnectToken] = useState(0)
  const [desconectando, setDesconectando] = useState(false)
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

  const estado = resumo?.conexao?.estado || 'UNAVAILABLE'
  const conectando = estado === 'CONNECTING'
  // Sessao em RECONNECTING (servidor) x retry de UI (estado `reconectando`):
  // nomes distintos de proposito.
  const reconectandoSessao = estado === 'RECONNECTING'
  const conectado = estado === 'CONNECTED'
  // Estados transitorios do servidor: CONNECTING e RECONNECTING devem
  // continuar consultando até estabilizar em CONNECTED ou estado final
  const operando = conectando || reconectandoSessao || desconectando
  const indisponivelAmbiente = estado === 'NOT_CONFIGURED'

  function abrirConexaoConfirmada() {
    setConfirmarConectar(false)
    setModalAberto(true)
    setAutoConnectToken((valor) => valor + 1)
  }

  async function handleDesconectar() {
    if (desconectando) return
    setDesconectando(true)
    try {
      await desconectarWhatsapp()
      setConfirmarDesconectar(false)
      await carregar()
      emitirToast('success', 'WhatsApp desconectado.')
    } catch (err) {
      emitirToast('error', err?.response?.data?.mensagem || 'Não foi possível desconectar. Tente novamente.')
    } finally {
      setDesconectando(false)
    }
  }

  function textoBotaoConexao() {
    if (conectando) return 'Conectando...'
    if (reconectandoSessao) return 'Reconectando...'
    return conectado ? 'Desconectar' : 'Conectar'
  }

  function helperTexto() {
    if (indisponivelAmbiente) return 'A integração WhatsApp ainda não está disponível neste ambiente.'
    if (conectado) return null
    if (conectando) return 'Aguardando conclusão do pareamento no modal de configuração.'
    if (reconectandoSessao) return 'Tentando restabelecer a sessão automaticamente.'
    return null
  }
  // Sai sozinho do estado temporário: nova consulta com backoff enquanto
  // houver erro de comunicação ou estado UNAVAILABLE. Para em estado
  // estável, ao esgotar as tentativas ou ao desmontar.
  useEffect(() => {
    if (carregando) return
    const estadoAtual = resumo?.conexao?.estado
    const transitorio = Boolean(erro) || estadoAtual === ESTADO_TRANSITORIO
    const emRecuperacaoServidor = ESTADOS_RECONECTANDO.includes(estadoAtual)
    if (!transitorio && !emRecuperacaoServidor) {
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
    <section className="panel integr-card" aria-label="Integração WhatsApp">
      <div className="integr-card-head">
        <h2>WhatsApp</h2>
        <span className="integr-card-icon" aria-hidden="true">
          <img src={whatsappLogo} alt="" className="integr-card-logo" />
        </span>
      </div>
      <p className="integr-card-desc">Lembretes automáticos e ações de CRM pelo WhatsApp.</p>

      {carregando && !reconectando && <p className="wpp-muted">Carregando integração...</p>}
      {!carregando && erro && (
        <>
          <div className="integr-card-status">
            <StatusBadge status="UNAVAILABLE" />
          </div>
          {reconectando
            ? <p className="wpp-muted">Reconectando...</p>
            : (
              <>
                <p className="form-error">{erro}</p>
                <div className="integr-card-actions">
                  <Button variant="secondary" type="button" onClick={() => carregar()}>Tentar novamente</Button>
                </div>
              </>
            )}
        </>
      )}
      {!carregando && !erro && resumo && (
        <>
          <div className="integr-card-status">
            <StatusBadge status={estado} />
            {helperTexto() && <p className="wpp-muted">{helperTexto()}</p>}
            {!resumo.disponivelNoPlano && !indisponivelAmbiente && (
              <p className="wpp-muted">Não disponível no seu plano.</p>
            )}
          </div>
          {resumo.disponivelNoPlano || ['CONNECTED', 'CONNECTING', 'RECONNECTING'].includes(resumo.conexao?.estado) ? (
            <div className="integr-card-actions">
              <Button variant="secondary" type="button" onClick={() => setModalAberto(true)}>
                Configurar
              </Button>
              <Button
                type="button"
                variant={conectado ? 'secondary' : 'primary'}
                onClick={() => (conectado ? setConfirmarDesconectar(true) : setConfirmarConectar(true))}
                disabled={operando || indisponivelAmbiente}
                loading={desconectando}
                title={indisponivelAmbiente ? 'A integração WhatsApp ainda não está disponível neste ambiente.' : undefined}
              >
                {textoBotaoConexao()}
              </Button>
            </div>
          ) : (
            <div className="integr-card-actions">
              <Link to="/sistema/planos" className="btn btn-secondary">Ver planos</Link>
            </div>
          )}
        </>
      )}

      <WhatsAppModal
        open={modalAberto}
        resumo={resumo}
        onClose={() => setModalAberto(false)}
        onResumoAtualizado={setResumo}
        autoConnectToken={autoConnectToken}
      />
      <ConfirmacaoModal
        open={confirmarConectar}
        titulo="Conectar WhatsApp?"
        mensagem="Vamos iniciar a conexão do WhatsApp da empresa. Um QR Code será exibido para concluir o pareamento."
        tipo="neutral"
        acaoLabel="Continuar"
        onConfirmar={abrirConexaoConfirmada}
        onCancelar={() => setConfirmarConectar(false)}
      />
      <ConfirmacaoModal
        open={confirmarDesconectar}
        titulo="Desconectar WhatsApp?"
        mensagem="Lembretes e ações de CRM pelo WhatsApp ficarão indisponíveis até uma nova conexão."
        tipo="danger"
        acaoLabel="Desconectar"
        carregando={desconectando}
        onConfirmar={handleDesconectar}
        onCancelar={() => setConfirmarDesconectar(false)}
      />
    </section>
  )
}
