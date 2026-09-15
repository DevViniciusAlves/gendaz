import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import Button from '../../components/Button.jsx'
import ConfirmacaoModal from '../../components/ConfirmacaoModal.jsx'
import Modal from '../../components/Modal.jsx'
import StatusBadge from '../../components/StatusBadge.jsx'
import {
  atualizarConfiguracaoWhatsapp,
  buscarResumoWhatsapp,
  conectarWhatsapp,
  desconectarWhatsapp,
  obterQrWhatsapp,
} from '../../api/whatsappApi.js'

const TEMPLATE_PADRAO = 'Olá, {cliente}! Lembrete: seu atendimento na {empresa} está marcado para {data} às {hora}.'
const PREVIEW = {
  cliente: 'Mariana',
  empresa: 'Clínica Bella',
  data: '18/09/2026',
  hora: '14:30',
}

const INTERVALO_POLL_MS = 2000
const JANELA_POLL_MS = 2 * 60 * 1000

function emitirToast(type, message) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('gendaz:toast', { detail: { type, message } }))
}

const ERROS_CONEXAO = {
  WHATSAPP_NOT_CONFIGURED: 'Integração indisponível neste ambiente.',
  WHATSAPP_SERVICE_UNAVAILABLE: 'Serviço WhatsApp indisponível no momento. Tente novamente.',
  WHATSAPP_CONNECT_TIMEOUT: 'Não foi possível concluir a conexão com o WhatsApp a tempo. Tente novamente.',
  WHATSAPP_QR_UNAVAILABLE: 'QR Code ainda não disponível. Aguarde e tente novamente.',
  WHATSAPP_SESSION_ERROR: 'Falha na sessão WhatsApp. Tente conectar novamente.',
  WHATSAPP_SERVICE_AUTH_ERROR: 'Não foi possível autenticar com o serviço do WhatsApp.',
}

function mensagemErroConexao(err, padrao) {
  const dados = err?.response?.data || {}
  const codigo = dados.code || dados.error
  if (codigo && ERROS_CONEXAO[codigo]) return ERROS_CONEXAO[codigo]
  return dados.mensagem || dados.message || padrao
}

export default function WhatsAppModal({ open, resumo, onClose, onResumoAtualizado, autoConnectToken = 0 }) {
  const [dados, setDados] = useState(resumo || null)
  const [carregandoResumo, setCarregandoResumo] = useState(false)
  const [conectando, setConectando] = useState(false)
  const [qr, setQr] = useState(null)
  const [gerandoQr, setGerandoQr] = useState(false)
  const [desconectando, setDesconectando] = useState(false)
  const [confirmarSaida, setConfirmarSaida] = useState(false)
  const [confirmarConectar, setConfirmarConectar] = useState(false)
  const [pareamentoExpirado, setPareamentoExpirado] = useState(false)
  const [salvandoToggle, setSalvandoToggle] = useState(false)
  const [toggleOn, setToggleOn] = useState(false)
  const [template, setTemplate] = useState('')
  const [salvandoTemplate, setSalvandoTemplate] = useState(false)
  const pollRef = useRef(null)
  const abortRef = useRef(null)
  const emPollRef = useRef(false)
  const ultimoAutoConnectRef = useRef(0)
  const foiAbertoRef = useRef(false)
  const qrRef = useRef(null)

  const atualizarResumo = useCallback((novo) => {
    setDados(novo)
    onResumoAtualizado?.(novo)
  }, [onResumoAtualizado])

  // Guarda o último QR válido exibido: o polling nunca limpa o QR
  // durante a atualização — só substitui por outro QR válido.
  const definirQr = useCallback((novoQr) => {
    if (!novoQr || qrRef.current === novoQr) return
    qrRef.current = novoQr
    setQr(novoQr)
  }, [])

  const limparQr = useCallback(() => {
    qrRef.current = null
    setQr(null)
  }, [])

  const limparPolling = useCallback(() => {
    if (pollRef.current) {
      clearInterval(pollRef.current)
      pollRef.current = null
    }
    if (abortRef.current) {
      abortRef.current.abort()
      abortRef.current = null
    }
    emPollRef.current = false
  }, [])

  const recarregarResumo = useCallback(async (options) => {
    setCarregandoResumo(true)
    try {
      const novo = await buscarResumoWhatsapp(options)
      atualizarResumo(novo)
      return novo
    } catch (err) {
      return null
    } finally {
      setCarregandoResumo(false)
    }
  }, [atualizarResumo])

  // Reset completo apenas na transição fechado → aberto. Se este efeito
  // rodasse a cada troca da prop `resumo` (que muda a cada ciclo do
  // polling no pai), o QR seria apagado e recriado a cada ~2s (flicker).
  useEffect(() => {
    if (open && !foiAbertoRef.current) {
      foiAbertoRef.current = true
      setDados(resumo || null)
      setToggleOn(Boolean(resumo?.configuracao?.lembretesAtivos))
      setTemplate(resumo?.configuracao?.lembreteTemplate || resumo?.configuracao?.lembreteTemplatePadrao || TEMPLATE_PADRAO)
      limparQr()
      setGerandoQr(false)
      setConectando(false)
      setConfirmarSaida(false)
      setConfirmarConectar(false)
      setPareamentoExpirado(false)
    } else if (!open && foiAbertoRef.current) {
      foiAbertoRef.current = false
      limparQr()
      setPareamentoExpirado(false)
      limparPolling()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  // Espelha o resumo mais recente (status da conexão, quotas) enquanto o
  // modal está aberto, sem tocar no QR, no loading ou no texto em edição.
  useEffect(() => {
    if (open) {
      setDados(resumo || null)
    }
  }, [open, resumo])

  useEffect(() => () => limparPolling(), [limparPolling])

  const iniciarPolling = useCallback((inicioEm) => {
    limparPolling()
    const inicio = inicioEm || Date.now()
    const controlador = new AbortController()
    abortRef.current = controlador
    pollRef.current = setInterval(async () => {
      if (Date.now() - inicio > JANELA_POLL_MS) {
        limparPolling()
        setConectando(false)
        setGerandoQr(false)
        limparQr()
        setPareamentoExpirado(true)
        return
      }
      if (emPollRef.current) return
      emPollRef.current = true
      try {
        const novo = await buscarResumoWhatsapp({ signal: controlador.signal })
        atualizarResumo(novo)
        const estado = novo?.conexao?.estado
        if (estado === 'CONNECTED') {
          limparPolling()
          limparQr()
          setGerandoQr(false)
          setConectando(false)
          setPareamentoExpirado(false)
          emitirToast('success', 'WhatsApp conectado!')
          return
        }
        // Erro terminal: ambiente sem configuracao nunca vai conectar.
        if (estado === 'NOT_CONFIGURED') {
          limparPolling()
          limparQr()
          setGerandoQr(false)
          setConectando(false)
          emitirToast('error', 'Integração indisponível neste ambiente.')
          return
        }
        if (novo?.conexao?.hasQr) {
          try {
            const qrAtual = await obterQrWhatsapp({ signal: controlador.signal })
            // Mantém o último QR válido na tela: só substitui por outro QR
            // válido (mesmo valor não gera atualização de estado).
            definirQr(qrAtual?.qr)
            if (qrAtual?.qr) {
              setGerandoQr(false)
            } else if (qrRef.current == null) {
              setGerandoQr(true)
            }
          } catch (err) {
            if (err?.response?.status !== 404) {
              throw err
            }
            // QR ainda não criado: loading só até o primeiro QR válido;
            // nunca apaga um QR já exibido por falha transitória.
            if (qrRef.current == null) {
              setGerandoQr(true)
            }
          }
        }
      } catch (err) {
        // Erro terminal (auth invalida / ambiente sem config): para o polling
        // e mostra o erro real. Falha transitória de rede/status: tolera.
        const status = err?.response?.status
        const codigo = err?.response?.data?.code || err?.response?.data?.error
        if (status === 401 || status === 403 || codigo === 'WHATSAPP_NOT_CONFIGURED') {
          limparPolling()
          setConectando(false)
          setGerandoQr(false)
          emitirToast('error', mensagemErroConexao(err, 'Não foi possível acompanhar a conexão.'))
        }
      } finally {
        emPollRef.current = false
      }
    }, INTERVALO_POLL_MS)
  }, [atualizarResumo, limparPolling, definirQr, limparQr])

  // Se abrir já em pareamento (ex.: conexão iniciada em outra sessão),
  // acompanha até conectar ou expirar a janela.
  useEffect(() => {
    if (!open || !dados || pollRef.current || pareamentoExpirado) return
    const estadoAtual = dados?.conexao?.estado
    if (estadoAtual === 'CONNECTING' || (dados?.conexao?.hasQr && estadoAtual !== 'CONNECTED')) {
      setConectando(true)
      setGerandoQr(true)
      iniciarPolling(Date.now())
    }
  }, [open, dados, pareamentoExpirado, iniciarPolling])

  async function handleConectar() {
    setConfirmarConectar(false)
    if (conectando) return
    setPareamentoExpirado(false)
    setConectando(true)
    setGerandoQr(true)
    try {
      await conectarWhatsapp()
      await recarregarResumo()
      iniciarPolling(Date.now())
    } catch (err) {
      setConectando(false)
      setGerandoQr(false)
      emitirToast('error', mensagemErroConexao(err, 'Não foi possível iniciar a conexão. Tente novamente.'))
    }
  }

  useEffect(() => {
    if (!open || !autoConnectToken || ultimoAutoConnectRef.current === autoConnectToken) return
    ultimoAutoConnectRef.current = autoConnectToken
    handleConectar()
  }, [open, autoConnectToken])

  async function handleSalvarTemplate() {
    setSalvandoTemplate(true)
    try {
      await atualizarConfiguracaoWhatsapp({ lembreteTemplate: template })
      emitirToast('success', 'Mensagem de lembrete salva.')
      await recarregarResumo()
    } catch (err) {
      emitirToast('error', err?.response?.data?.mensagem || 'Erro ao salvar a mensagem.')
    } finally {
      setSalvandoTemplate(false)
    }
  }

  function handleRestaurarTemplate() {
    setTemplate(dados?.configuracao?.lembreteTemplatePadrao || TEMPLATE_PADRAO)
  }

  async function handleDesconectar() {
    if (desconectando) return
    setDesconectando(true)
    try {
      await desconectarWhatsapp()
      setConfirmarSaida(false)
      limparQr()
      await recarregarResumo()
      emitirToast('success', 'WhatsApp desconectado.')
    } catch (err) {
      emitirToast('error', err?.response?.data?.mensagem || 'Não foi possível desconectar. Tente novamente.')
    } finally {
      setDesconectando(false)
    }
  }

  async function handleToggle(event) {
    const desejado = event.target.checked
    if (salvandoToggle) return
    setSalvandoToggle(true)
    try {
      const resposta = await atualizarConfiguracaoWhatsapp({ lembretesAtivos: desejado })
      const salvo = resposta?.lembretesAtivos ?? desejado
      setToggleOn(salvo)
      setDados((atual) => (atual ? { ...atual, configuracao: { ...atual.configuracao, lembretesAtivos: salvo } } : atual))
      await recarregarResumo()
    } catch (err) {
      setToggleOn(dados?.configuracao?.lembretesAtivos ?? false)
      emitirToast('error', err?.response?.data?.mensagem || 'Não foi possível salvar. Tente novamente.')
    } finally {
      setSalvandoToggle(false)
    }
  }

  function handleClose() {
    limparPolling()
    limparQr()
    setPareamentoExpirado(false)
    onClose()
  }

  if (!open) return null

  if (!dados) {
    return (
      <Modal title="WhatsApp" open={open} onClose={handleClose}>
        <p className="wpp-muted">Não foi possível consultar a integração agora. Tente novamente em alguns instantes.</p>
      </Modal>
    )
  }

  const estado = dados?.conexao?.estado || 'UNAVAILABLE'
  const disponivelNoPlano = dados ? Boolean(dados.disponivelNoPlano) : true
  const indisponivelAmbiente = estado === 'NOT_CONFIGURED'
  const conectado = estado === 'CONNECTED'
  const sessaoAtiva = estado === 'CONNECTED' || estado === 'CONNECTING' || estado === 'RECONNECTING'
  const emPareamento = conectando || estado === 'CONNECTING' || (dados?.conexao?.hasQr && !conectado)
  const lembretes = dados?.uso?.lembretes
  const crm = dados?.uso?.crm
  const templateAtual = template || dados?.configuracao?.lembreteTemplatePadrao || TEMPLATE_PADRAO
  const previewMensagem = templateAtual
    .replaceAll('{cliente}', PREVIEW.cliente)
    .replaceAll('{empresa}', PREVIEW.empresa)
    .replaceAll('{data}', PREVIEW.data)
    .replaceAll('{hora}', PREVIEW.hora)

  function barra(uso) {
    if (!uso || !uso.limite) return 0
    return Math.min(100, Math.round((uso.enviados / uso.limite) * 100))
  }

  function emProcessamento(uso) {
    if (!uso || !uso.reservados) return null
    return uso.reservados === 1 ? '1 em processamento' : `${uso.reservados} em processamento`
  }

  return (
    <Modal title="WhatsApp" open={open} onClose={handleClose} portalClassName="wpp-modal-portal">
      <p className="wpp-modal-subtitle">Configure lembretes automáticos e ações de CRM pelo WhatsApp.</p>
      <div className="wpp-modal-body">
      {!disponivelNoPlano && !sessaoAtiva && (
        <section className="wpp-section">
          <p className="wpp-muted">Não disponível no seu plano</p>
          <p className="wpp-muted">Os lembretes e ações de CRM pelo WhatsApp exigem um plano com a integração.</p>
          <div className="modal-actions">
            <Link to="/sistema/planos" className="btn btn-secondary">Ver planos</Link>
          </div>
        </section>
      )}

      {!disponivelNoPlano && sessaoAtiva && (
        <section className="wpp-section" aria-label="Conexão">
          <h3 className="wpp-section-title">Conexão</h3>
          <div className="wpp-row">
            <div className="wpp-status-block">
              <StatusBadge status={estado} />
              <p className="wpp-muted">Sessão ainda ativa de um plano anterior. Desconecte para encerrar.</p>
            </div>
            <Button variant="secondary" type="button" onClick={() => setConfirmarSaida(true)}>
              Desconectar WhatsApp
            </Button>
          </div>
        </section>
      )}

      {disponivelNoPlano && (
        <>
          <section className="wpp-section" aria-label="Conexão">
          <h3 className="wpp-section-title">Conexão</h3>
          <div className="wpp-row">
            <div className="wpp-status-block">
              <StatusBadge status={estado} />
              <p className="wpp-muted">
                {indisponivelAmbiente && 'A integração WhatsApp ainda não está disponível neste ambiente.'}
                {!indisponivelAmbiente && conectado && 'Pronto para lembretes automáticos, Resgate e Reconexão.'}
                {!indisponivelAmbiente && !conectado && estado !== 'CONNECTING' && estado !== 'RECONNECTING'
                  && 'Conecte o WhatsApp da empresa para utilizar lembretes automáticos, Resgate e Reconexão.'}
                {estado === 'CONNECTING' && 'Aguardando leitura do QR Code...'}
                {estado === 'RECONNECTING' && 'Tentando restabelecer a sessão. Não é preciso escanear novamente.'}
              </p>
            </div>
            {!conectado && !emPareamento && estado !== 'RECONNECTING' && !indisponivelAmbiente && (
              <Button type="button" onClick={() => setConfirmarConectar(true)} loading={conectando} loadingText="Conectando...">
                Conectar WhatsApp
              </Button>
            )}
            {conectado && (
              <Button variant="secondary" type="button" onClick={() => setConfirmarSaida(true)}>
                Desconectar WhatsApp
              </Button>
            )}
          </div>

          {emPareamento && !conectado && !pareamentoExpirado && (
            <div className="wpp-qr-box">
              {qr ? (
                <>
                  <QRCodeSVG value={qr} size={200} />
                  <p className="wpp-muted">Abra o WhatsApp no celular, acesse Aparelhos conectados, toque em Conectar aparelho e escaneie o QR Code.</p>
                </>
              ) : (
                <p className="wpp-muted">{gerandoQr || estado === 'CONNECTING' ? 'Gerando QR Code...' : 'Conectando...'}</p>
              )}
            </div>
          )}

          {pareamentoExpirado && !conectado && (
            <div className="wpp-qr-box">
              <p className="wpp-muted">O tempo para conexão expirou. Tente gerar um novo QR Code.</p>
              <Button type="button" onClick={() => setConfirmarConectar(true)} loading={conectando} loadingText="Conectando...">
                Tentar novamente
              </Button>
            </div>
          )}
          </section>

          <section className="wpp-section" aria-label="Lembretes automáticos">
          <div className="wpp-toggle-head">
            <div>
              <h3 className="wpp-section-title">Lembretes automáticos</h3>
              <p className="wpp-muted">Quando ativado, o gendaz envia automaticamente uma mensagem pelo WhatsApp 2 horas antes do atendimento.</p>
            </div>
            <label className="wpp-toggle">
              <input
                type="checkbox"
                checked={toggleOn}
                disabled={salvandoToggle || !conectado}
                onChange={handleToggle}
                aria-label="Enviar lembretes de agendamento pelo WhatsApp"
              />
              <span className="wpp-switch" aria-hidden="true" />
            </label>
          </div>
          {indisponivelAmbiente && <p className="wpp-helper">A integração precisa estar disponível para ativar os lembretes.</p>}
          {!indisponivelAmbiente && !conectado && <p className="wpp-helper">Conecte o WhatsApp para ativar os lembretes automáticos.</p>}
          </section>

          <section className="wpp-section" aria-label="Mensagem do lembrete">
            <div className="wpp-section-head">
              <div>
                <h3 className="wpp-section-title">Mensagem do lembrete</h3>
                <p className="wpp-muted">Personalize o texto que seu cliente receberá.</p>
              </div>
              <Button variant="secondary" type="button" className="wpp-restore-btn" onClick={handleRestaurarTemplate}>Restaurar padrão</Button>
            </div>
            <label className="field wpp-template-field">
              <textarea
                value={template}
                onChange={(e) => setTemplate(e.target.value)}
                placeholder={dados?.configuracao?.lembreteTemplatePadrao || TEMPLATE_PADRAO}
                rows={4}
                maxLength={500}
              />
              <span className="wpp-counter">{(template || '').length} / 500</span>
            </label>
            <div className="wpp-variables" aria-label="Variáveis disponíveis">
              <strong className="wpp-variables-title">Variáveis disponíveis</strong>
              <div className="wpp-variables-grid">
                <span className="wpp-var"><code>{'{cliente}'}</code><span>Substituído automaticamente pelo nome do cliente.</span></span>
                <span className="wpp-var"><code>{'{empresa}'}</code><span>Substituído pelo nome da empresa.</span></span>
                <span className="wpp-var"><code>{'{data}'}</code><span>Data real do atendimento.</span></span>
                <span className="wpp-var"><code>{'{hora}'}</code><span>Horário real do atendimento.</span></span>
              </div>
            </div>
            <div className="wpp-message-preview">
              <strong>Pré-visualização</strong>
              <p>{previewMensagem}</p>
              <small>Prévia com dados fictícios.</small>
            </div>
            <div className="wpp-template-actions">
              <Button type="button" onClick={handleSalvarTemplate} loading={salvandoTemplate}>Salvar mensagem</Button>
            </div>
          </section>

          <section className="wpp-section" aria-label="Uso do plano">
          <h3 className="wpp-section-title">Uso do plano</h3>
          <div className="wpp-quota-grid">
          {lembretes && (
            <div className="wpp-quota-card">
              <div className="wpp-progress-label">
                <span>Lembretes</span>
                <strong>{lembretes.enviados} / {lembretes.limite}</strong>
              </div>
              <div className="wpp-progress" role="progressbar" aria-valuenow={barra(lembretes)} aria-valuemin="0" aria-valuemax="100">
                <i style={{ width: `${barra(lembretes)}%` }} />
              </div>
              {emProcessamento(lembretes) && <p className="wpp-note">{emProcessamento(lembretes)}</p>}
            </div>
          )}
          {crm && (
            <div className="wpp-quota-card">
              <div className="wpp-progress-label">
                <span>Ações CRM</span>
                <strong>{crm.enviados} / {crm.limite}</strong>
              </div>
              <div className="wpp-progress" role="progressbar" aria-valuenow={barra(crm)} aria-valuemin="0" aria-valuemax="100">
                <i style={{ width: `${barra(crm)}%` }} />
              </div>
              {emProcessamento(crm) && <p className="wpp-note">{emProcessamento(crm)}</p>}
              <p className="wpp-note">Resgate e Reconexão compartilham esse limite.</p>
            </div>
          )}
          </div>
          {carregandoResumo && <p className="wpp-muted">Atualizando...</p>}
          </section>
        </>
      )}
      </div>

      <ConfirmacaoModal
        open={confirmarConectar}
        titulo="Conectar WhatsApp?"
        mensagem="Vamos iniciar a conexão do WhatsApp da empresa. Um QR Code será exibido para concluir o pareamento."
        tipo="neutral"
        acaoLabel="Continuar"
        onConfirmar={handleConectar}
        onCancelar={() => setConfirmarConectar(false)}
      />

      <ConfirmacaoModal
        open={confirmarSaida}
        titulo="Desconectar WhatsApp?"
        mensagem="Ao desconectar, será necessário conectar o WhatsApp novamente para voltar a utilizar a integração. Lembretes e ações de CRM pelo WhatsApp ficarão indisponíveis até uma nova conexão."
        tipo="danger"
        acaoLabel="Desconectar"
        carregando={desconectando}
        onConfirmar={handleDesconectar}
        onCancelar={() => setConfirmarSaida(false)}
      />
    </Modal>
  )
}
