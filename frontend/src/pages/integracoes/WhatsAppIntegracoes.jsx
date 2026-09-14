import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { MessageCircle } from 'lucide-react'
import Button from '../../components/Button.jsx'
import ConfirmacaoModal from '../../components/ConfirmacaoModal.jsx'
import StatusBadge from '../../components/StatusBadge.jsx'
import { buscarResumoWhatsapp, desconectarWhatsapp } from '../../api/whatsappApi.js'
import WhatsAppModal from './WhatsAppModal.jsx'
import './whatsapp.css'

function emitirToast(type, message) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('gendaz:toast', { detail: { type, message } }))
}

export default function WhatsAppIntegracoes() {
  const [resumo, setResumo] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [modalAberto, setModalAberto] = useState(false)
  const [confirmarConectar, setConfirmarConectar] = useState(false)
  const [confirmarDesconectar, setConfirmarDesconectar] = useState(false)
  const [autoConnectToken, setAutoConnectToken] = useState(0)
  const [desconectando, setDesconectando] = useState(false)

  const carregar = useCallback(async () => {
    setCarregando(true)
    setErro('')
    try {
      const dados = await buscarResumoWhatsapp()
      setResumo(dados)
    } catch (err) {
      setErro(err?.response?.data?.mensagem || 'Não foi possível consultar a integração agora. Tente novamente em alguns instantes.')
    } finally {
      setCarregando(false)
    }
  }, [])

  useEffect(() => {
    carregar()
  }, [carregar])

  const estado = resumo?.conexao?.estado || 'UNAVAILABLE'
  const conectando = estado === 'CONNECTING'
  const reconectando = estado === 'RECONNECTING'
  const conectado = estado === 'CONNECTED'
  const operando = conectando || reconectando || desconectando
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
    if (reconectando) return 'Reconectando...'
    return conectado ? 'Desconectar' : 'Conectar'
  }

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

      {carregando && <p className="wpp-muted">Carregando integração...</p>}
      {!carregando && erro && (
        <>
          <p className="wpp-muted">Temporariamente indisponível</p>
          <p className="form-error">{erro}</p>
          <Button variant="secondary" type="button" onClick={carregar}>Tentar novamente</Button>
        </>
      )}
      {!carregando && !erro && resumo && (
        <div className="wpp-card-body">
          <div className="wpp-card-status">
            <StatusBadge status={estado} />
            {resumo.disponivelNoPlano ? (
              <span className="wpp-muted">
                {indisponivelAmbiente ? 'Integração indisponível neste ambiente.' : 'Lembretes automáticos e ações de CRM pelo WhatsApp.'}
              </span>
            ) : (
              <span className="wpp-muted">Não disponível no seu plano</span>
            )}
          </div>
          {resumo.disponivelNoPlano || ['CONNECTED', 'CONNECTING', 'RECONNECTING'].includes(resumo.conexao?.estado) ? (
            <div className="wpp-card-actions">
              <Button variant="secondary" type="button" onClick={() => setModalAberto(true)}>
                Configurar
              </Button>
              <Button
                type="button"
                variant={conectado ? 'secondary' : 'primary'}
                onClick={() => (conectado ? setConfirmarDesconectar(true) : setConfirmarConectar(true))}
                disabled={operando || indisponivelAmbiente}
                loading={desconectando}
              >
                {textoBotaoConexao()}
              </Button>
            </div>
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
