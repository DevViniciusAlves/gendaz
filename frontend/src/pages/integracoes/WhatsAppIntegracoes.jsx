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

export default function WhatsAppIntegracoes() {
  const [resumo, setResumo] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const reconectando = false
  const retryEsgotado = false
  const [modalAberto, setModalAberto] = useState(false)
  const [confirmarConectar, setConfirmarConectar] = useState(false)
  const [confirmarDesconectar, setConfirmarDesconectar] = useState(false)
  const [autoConnectToken, setAutoConnectToken] = useState(0)
  const [desconectando, setDesconectando] = useState(false)
  const ultimoErroRef = useRef(null)

  const carregar = useCallback(async () => {
    setCarregando(true)
    setErro('')
    try {
      const dados = await buscarResumoWhatsapp()
      setResumo(dados)
      ultimoErroRef.current = null
      setErro('')
    } catch (err) {
      ultimoErroRef.current = err
      setErro(
        err?.response?.data?.mensagem ||
        'Não foi possível consultar a integração agora. Tente novamente em alguns instantes.'
      )
    } finally {
      setCarregando(false)
    }
  }, [])

  function tentarNovamente() {
    carregar()
  }

  useEffect(() => {
    carregar()
  }, [carregar])

  const estado = resumo?.conexao?.estado || 'UNAVAILABLE'
  const indisponivelTemporario = estado === 'UNAVAILABLE'
  const conectando = estado === 'CONNECTING'
  const reconectandoSessao = estado === 'RECONNECTING'
  const conectado = estado === 'CONNECTED'
  const operando = conectando || reconectandoSessao || desconectando
  const indisponivelAmbiente = estado === 'NOT_CONFIGURED'

  function abrirConexaoConfirmada() {
    if (indisponivelTemporario || indisponivelAmbiente) return
    setConfirmarConectar(false)
    setModalAberto(true)
    setAutoConnectToken((valor) => valor + 1)
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
    if (indisponivelTemporario && !retryEsgotado) return 'Iniciando serviço...'
    if (indisponivelTemporario && retryEsgotado) return MSG_SERVICO_INDISPONIVEL
    if (indisponivelAmbiente) return 'A integração WhatsApp ainda não está disponível neste ambiente.'
    if (conectado) return null
    if (conectando) return 'Aguardando conclusão do pareamento no modal de configuração.'
    if (reconectandoSessao) return 'Tentando restabelecer a sessão automaticamente.'
    return null
  }

  // Polling removido: GET /api/whatsapp/resumo ocorre apenas na carga inicial.
  // Polling temporário durante conexão ativa é responsabilidade exclusiva do WhatsAppModal.

  const mostrarErroAssustador = Boolean(erro) && (!ehErroTransitorio(ultimoErroRef.current) || retryEsgotado)

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

      {!carregando && erro && mostrarErroAssustador && (
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

      {!carregando && erro && !mostrarErroAssustador && (
        <>
          <div className="integr-card-status">
            <StatusBadge status="UNAVAILABLE" />
            <p className="wpp-muted">Iniciando serviço...</p>
          </div>
        </>
      )}

      {!carregando && !erro && resumo && indisponivelTemporario && (
        <>
          <div className="integr-card-status">
            <StatusBadge status="UNAVAILABLE" />
            <p className="wpp-muted">{retryEsgotado ? MSG_SERVICO_INDISPONIVEL : 'Iniciando serviço...'}</p>
          </div>
          {retryEsgotado && (
            <div className="integr-card-actions">
              <Button variant="secondary" type="button" onClick={tentarNovamente}>Tentar novamente</Button>
            </div>
          )}
        </>
      )}

      {!carregando && !erro && resumo && estado !== 'UNAVAILABLE' && (
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
                disabled={operando || indisponivelAmbiente || indisponivelTemporario}
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
