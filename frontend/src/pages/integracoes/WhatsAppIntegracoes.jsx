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

const MSG_SERVICO_INDISPONIVEL = 'Serviço WhatsApp temporariamente indisponível. Tente novamente.'

function ehErroTransitorio(err) {
  if (!err) return false
  const status = err?.response?.status
  const codigo = err?.response?.data?.code || err?.response?.data?.error
  if (!err.response) return true
  if (status === 502 || status === 503 || status === 504) return true
  if (codigo === 'WHATSAPP_SERVICE_UNAVAILABLE') return true
  return false
}

function ehErroDefinitivo(err) {
  if (!err) return false
  const status = err?.response?.status
  const codigo = err?.response?.data?.code || err?.response?.data?.error
  if (status === 401 || status === 403) return true
  if (codigo === 'WHATSAPP_NOT_CONFIGURED') return true
  return false
}

const WAKING_POLL_INTERVAL_MS = 2500
const WAKING_POLL_MAX = 12

export default function WhatsAppIntegracoes() {
  const [resumo, setResumo] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [acordando, setAcordando] = useState(false)
  const [erro, setErro] = useState('')
  const [modalAberto, setModalAberto] = useState(false)
  const [confirmarConectar, setConfirmarConectar] = useState(false)
  const [confirmarDesconectar, setConfirmarDesconectar] = useState(false)
  const [autoConnectToken, setAutoConnectToken] = useState(0)
  const [desconectando, setDesconectando] = useState(false)
  const ultimoErroRef = useRef(null)
  const timerRef = useRef(null)
  const tentativasRef = useRef(0)

  const limparWaking = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }, [])

  const carregar = useCallback(async ({ isRetry = false } = {}) => {
    if (!isRetry) {
      setCarregando(true)
      setAcordando(false)
      tentativasRef.current = 0
      limparWaking()
    }
    setErro('')
    try {
      const dados = await buscarResumoWhatsapp()
      const estado = dados?.conexao?.estado || 'UNAVAILABLE'
      if (estado === 'UNAVAILABLE' && tentativasRef.current < WAKING_POLL_MAX) {
        setResumo(dados)
        setAcordando(true)
        setCarregando(false)
        tentativasRef.current += 1
        limparWaking()
        timerRef.current = setTimeout(() => carregar({ isRetry: true }), WAKING_POLL_INTERVAL_MS)
        return
      }
      setResumo(dados)
      setAcordando(false)
      setCarregando(false)
      limparWaking()
      ultimoErroRef.current = null
      setErro('')
    } catch (err) {
      if (ehErroTransitorio(err) && tentativasRef.current < WAKING_POLL_MAX) {
        setAcordando(true)
        setCarregando(false)
        tentativasRef.current += 1
        limparWaking()
        timerRef.current = setTimeout(() => carregar({ isRetry: true }), WAKING_POLL_INTERVAL_MS)
        return
      }
      ultimoErroRef.current = err
      setAcordando(false)
      setCarregando(false)
      limparWaking()
      setErro(
        err?.response?.data?.mensagem ||
        'Não foi possível consultar a integração agora. Tente novamente em alguns instantes.'
      )
    }
  }, [limparWaking])

  function tentarNovamente() {
    tentativasRef.current = 0
    limparWaking()
    carregar()
  }

  useEffect(() => {
    carregar()
    return () => limparWaking()
  }, [carregar, limparWaking])

  const estado = resumo?.conexao?.estado || 'UNAVAILABLE'
  const indisponivelTemporario = estado === 'UNAVAILABLE'
  const conectando = estado === 'CONNECTING'
  const reconectandoSessao = estado === 'RECONNECTING'
  const conectado = estado === 'CONNECTED'
  const operando = conectando || reconectandoSessao || desconectando
  const indisponivelAmbiente = estado === 'NOT_CONFIGURED'

  function abrirConexaoConfirmada() {
    if (indisponivelAmbiente) return
    if (indisponivelTemporario && !acordando) return
    setConfirmarConectar(false)
    setModalAberto(true)
    setAutoConnectToken((v) => v + 1)
  }

  async function handleDesconectar() {
    if (desconectando || indisponivelTemporario) return
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

  const mostrarErroAssustador = Boolean(erro) && !ehErroTransitorio(ultimoErroRef.current)
  const emWaking = acordando || (carregando && !erro && !resumo)

  return (
    <section className="panel integr-card" aria-label="Integração WhatsApp">
      <div className="integr-card-head">
        <h2>WhatsApp</h2>
        <span className="integr-card-icon" aria-hidden="true">
          <img src={whatsappLogo} alt="" className="integr-card-logo" />
        </span>
      </div>
      <p className="integr-card-desc">Lembretes automáticos e ações de CRM pelo WhatsApp.</p>

      {emWaking && (
        <>
          <div className="integr-card-status">
            <p className="wpp-muted">Iniciando WhatsApp...</p>
            <p className="wpp-muted">Isso pode levar alguns segundos.</p>
          </div>
          <div className="integr-card-status" aria-live="polite">
            <span className="wpp-muted">Carregando...</span>
          </div>
        </>
      )}

      {!emWaking && carregando && <p className="wpp-muted">Carregando integração...</p>}

      {!emWaking && !carregando && erro && mostrarErroAssustador && (
        <>
          <div className="integr-card-status">
            <StatusBadge status="UNAVAILABLE" />
          </div>
          <p className="form-error">{ehErroDefinitivo(ultimoErroRef.current) ? erro : MSG_SERVICO_INDISPONIVEL}</p>
          <div className="integr-card-actions">
            <Button variant="secondary" type="button" onClick={tentarNovamente}>Tentar novamente</Button>
          </div>
        </>
      )}

      {!emWaking && !carregando && erro && !mostrarErroAssustador && (
        <>
          <div className="integr-card-status">
            <StatusBadge status="UNAVAILABLE" />
            <p className="wpp-muted">{MSG_SERVICO_INDISPONIVEL}</p>
          </div>
          <div className="integr-card-actions">
            <Button variant="secondary" type="button" onClick={tentarNovamente}>Tentar novamente</Button>
          </div>
        </>
      )}

      {!emWaking && !carregando && !erro && resumo && indisponivelTemporario && (
        <>
          <div className="integr-card-status">
            <StatusBadge status="UNAVAILABLE" />
            <p className="wpp-muted">{MSG_SERVICO_INDISPONIVEL}</p>
          </div>
          <div className="integr-card-actions">
            <Button variant="secondary" type="button" onClick={tentarNovamente}>Tentar novamente</Button>
          </div>
        </>
      )}

      {!emWaking && !carregando && !erro && resumo && estado !== 'UNAVAILABLE' && (
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
                disabled={operando || indisponivelAmbiente || (indisponivelTemporario && !acordando)}
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
