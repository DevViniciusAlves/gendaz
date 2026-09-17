import { useCallback, useEffect, useState } from 'react'
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
  const [modalAberto, setModalAberto] = useState(false)
  const [confirmarConectar, setConfirmarConectar] = useState(false)
  const [confirmarDesconectar, setConfirmarDesconectar] = useState(false)
  const [autoConnectToken, setAutoConnectToken] = useState(0)
  const [desconectando, setDesconectando] = useState(false)
  const [ultimoErro, setUltimoErro] = useState(null)

  const carregar = useCallback(async () => {
    setCarregando(true)
    setErro('')
    setUltimoErro(null)
    try {
      const dados = await buscarResumoWhatsapp()
      setResumo(dados)
      setErro('')
      setUltimoErro(null)
    } catch (err) {
      setUltimoErro(err)
      setResumo(null)
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
    if (indisponivelAmbiente) return
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

  const emLoadingInicial = carregando

  return (
    <section className="panel integr-card" aria-label="Integração WhatsApp">
      <div className="integr-card-head">
        <h2>WhatsApp</h2>
        <span className="integr-card-icon" aria-hidden="true">
          <img src={whatsappLogo} alt="" className="integr-card-logo" />
        </span>
      </div>
      <p className="integr-card-desc">Lembretes automáticos e ações de CRM pelo WhatsApp.</p>

      {emLoadingInicial && (
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

      {!emLoadingInicial && erro && (
        <>
          <div className="integr-card-status">
            <StatusBadge status="UNAVAILABLE" />
            {ehErroDefinitivo(ultimoErro) ? null : <p className="wpp-muted">{MSG_SERVICO_INDISPONIVEL}</p>}
          </div>
          {ehErroDefinitivo(ultimoErro) && <p className="form-error">{erro}</p>}
          {!ehErroDefinitivo(ultimoErro) && <p className="form-error" style={{ display: 'none' }}>{erro}</p>}
          <div className="integr-card-actions">
            <Button variant="secondary" type="button" onClick={tentarNovamente}>Tentar novamente</Button>
          </div>
        </>
      )}

      {!emLoadingInicial && !erro && resumo && indisponivelTemporario && (
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

      {!emLoadingInicial && !erro && resumo && estado !== 'UNAVAILABLE' && (
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
