import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Button from '../../components/Button.jsx'
import ConfirmacaoModal from '../../components/ConfirmacaoModal.jsx'
import StatusBadge from '../../components/StatusBadge.jsx'
import { buscarResumoWhatsapp, desconectarWhatsapp, tentarNovamenteWhatsapp } from '../../api/whatsappApi.js'
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
  const [tentandoNovamente, setTentandoNovamente] = useState(false)
  const [ultimoErro, setUltimoErro] = useState(null)
  const [retryState, setRetryState] = useState(null)

  const carregar = useCallback(async () => {
    setCarregando(true)
    setErro('')
    setUltimoErro(null)
    setRetryState(null)
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

  async function tentarNovamente() {
    if (tentandoNovamente || carregando) return
    setTentandoNovamente(true)
    setRetryState(null)
    try {
      // Tentativa explicita Stage -> WPP (cold wake + status da sessao).
      const retorno = await tentarNovamenteWhatsapp()
      // Armazena o estado obtido pelo retry em estado temporario,
      // mesmo se resumo ainda for null (edge case: retry funcionou mas resumo inicial falhou).
      if (retorno && retorno.estado) {
        setRetryState({
          estado: retorno.estado,
          hasQr: retorno.hasQr,
        })
      }
      // UMA sincronizacao do resumo pelo Stage (quotas/configuracao/conexao).
      try {
        const dados = await buscarResumoWhatsapp()
        // Se a sincronizacao funcionar, usa o resumo completo e limpa o estado temporario.
        if (dados && dados.conexao) {
          setResumo(dados)
          setRetryState(null)
          setErro('')
          setUltimoErro(null)
        }
        // Se a sincronizacao falhar, mantem o estado temporario do retry na tela.
        else {
          // NAO limpar retryState aqui - ele sera usado no render
        }
      } catch {
        // Sincronizacao falhou - mantem o estado temporario do retry na tela.
      }
    } catch (err) {
      // Falha transitoria: mantem mensagem de indisponibilidade e reabilita o botao.
      if (!ehErroDefinitivo(err)) {
        setErro(MSG_SERVICO_INDISPONIVEL)
      } else {
        setUltimoErro(err)
        setErro(
          err?.response?.data?.mensagem || err?.response?.data?.message
          || 'Não foi possível consultar a integração agora. Tente novamente em alguns instantes.'
        )
      }
    } finally {
      setTentandoNovamente(false)
    }
  }

  useEffect(() => {
    carregar()
  }, [carregar])

  // Determina o estado a ser exibido: usa resumo se disponivel, senão usa retryState
  const estadoDisplay = resumo?.conexao?.estado || retryState?.estado || 'UNAVAILABLE'
  const indisponivelTemporario = estadoDisplay === 'UNAVAILABLE'
  const conectando = estadoDisplay === 'CONNECTING'
  const reconectandoSessao = estadoDisplay === 'RECONNECTING'
  const conectado = estadoDisplay === 'CONNECTED'
  const operando = conectando || reconectandoSessao || desconectando
  const indisponivelAmbiente = estadoDisplay === 'NOT_CONFIGURED'

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
  const retryLoading = tentandoNovamente || carregando

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
            <Button variant="secondary" type="button" onClick={tentarNovamente} disabled={retryLoading} loading={tentandoNovamente} loadingText="Tentando...">Tentar novamente</Button>
          </div>
        </>
      )}

      {!emLoadingInicial && !erro && indisponivelTemporario && (
        <>
          <div className="integr-card-status">
            <StatusBadge status="UNAVAILABLE" />
            <p className="wpp-muted">{MSG_SERVICO_INDISPONIVEL}</p>
          </div>
          <div className="integr-card-actions">
            <Button variant="secondary" type="button" onClick={tentarNovamente} disabled={retryLoading} loading={tentandoNovamente} loadingText="Tentando...">Tentar novamente</Button>
          </div>
        </>
      )}

      {!emLoadingInicial && !erro && (resumo || retryState) && !indisponivelTemporario && (
        <>
          <div className="integr-card-status">
            <StatusBadge status={retryState?.estado || resumo?.conexao?.estado || estadoDisplay} />
            {helperTexto() && <p className="wpp-muted">{helperTexto()}</p>}
            {!resumo?.disponivelNoPlano && !indisponivelAmbiente && (
              <p className="wpp-muted">Não disponível no seu plano.</p>
            )}
            {!resumo?.disponivelNoPlano && retryState?.hasQr && (
              <p className="wpp-muted">QR disponível</p>
            )}
          </div>
          <div className="integr-card-actions">
{resumo && (
                resumo?.disponivelNoPlano || ['CONNECTED', 'CONNECTING', 'RECONNECTING'].includes(resumo.conexao?.estado)
              ) ? (
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
                    Conectar
                  </Button>
                </div>
              ) : null}
            {retryState?.estado && ['CONNECTED', 'CONNECTING', 'RECONNECTING'].includes(retryState.estado) ? (
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
                >
                  Conectar
                </Button>
              </div>
            ) : null}
          </div>
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