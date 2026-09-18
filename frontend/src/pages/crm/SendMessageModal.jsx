import { useEffect, useState } from 'react'
import Modal from '../../components/Modal.jsx'
import Button from '../../components/Button.jsx'
import { gerarUuid } from '../../api/appApi.js'
import { enviarMensagemCrm } from '../../api/crmApi.js'
import { buscarResumoWhatsapp } from '../../api/whatsappApi.js'

function emitirToast(type, message) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('gendaz:toast', { detail: { type, message } }))
}

const TEMPLATES = {
  resgate: {
    titulo: 'Enviar "Resgate"',
    assunto: 'Estamos com saudade',
    mensagem: 'Oi {nome}! Ta muito tempo sem nos ver. Que tal agendar um horario essa semana? Estamos de portas abertas pra voce!',
  },
  reconexao: {
    titulo: 'Enviar "Reconexao"',
    assunto: 'Sentimos sua falta',
    mensagem: '{nome}, faz tempo que não aparece por aqui! Queremos saber como voce esta. Que tal um novo atendimento?',
  },
  promocao: {
    titulo: 'Enviar "Promocao"',
    assunto: 'Oferta especial',
    mensagem: '{nome}, preparamos uma oferta especial so pra voce! Aproveite e agende seu proximo atendimento com desconto.',
  },
  lembrete: {
    titulo: 'Enviar "Lembrete"',
    assunto: 'Lembrete do compromisso',
    mensagem: '{nome}, lembrete: voce tem um compromisso agendado. Se precisar remarcar, esta tudo bem!',
  },
}

const TEMPLATES_WHATSAPP = {
  resgate: 'Olá, {cliente}! Tudo bem? Sentimos sua falta na {empresa}. Se quiser agendar um novo atendimento, estamos à disposição.',
  reconexao: 'Olá, {cliente}! Tudo bem? Passando para saber como você está. Quando quiser voltar à {empresa}, estaremos por aqui.',
}

const ERROS_WHATSAPP = {
  WHATSAPP_NAO_DISPONIVEL_NO_PLANO: 'O plano atual não possui ações de WhatsApp.',
  WHATSAPP_TELEFONE_INVALIDO: 'Este cliente não possui um telefone válido para WhatsApp.',
  WHATSAPP_OPT_OUT: 'Este cliente optou por não receber mensagens pelo WhatsApp.',
  WHATSAPP_NOT_CONNECTED: 'WhatsApp desconectado. Conecte sua conta para enviar mensagens.',
  WHATSAPP_COTA_ESGOTADA: 'A cota de ações CRM pelo WhatsApp acabou neste ciclo.',
  WHATSAPP_REQUEST_ID_INVALIDO: 'Não foi possível identificar a ação. Feche e abra novamente.',
  WHATSAPP_TIPO_NAO_SUPORTADO: 'Este tipo de mensagem ainda não possui WhatsApp.',
}

export default function SendMessageModal({ open, onClose, cliente, template, onEnviado }) {
  const [personalizar, setPersonalizar] = useState(false)
  const [mensagemCustom, setMensagemCustom] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [canal, setCanal] = useState('email')
  const [requestId, setRequestId] = useState(null)
  const [resumoWhatsapp, setResumoWhatsapp] = useState(null)
  const [statusWhatsapp, setStatusWhatsapp] = useState('UNAVAILABLE')
  const [carregandoWhatsapp, setCarregandoWhatsapp] = useState(false)

  const permiteWhatsapp = template === 'resgate' || template === 'reconexao'

  useEffect(() => {
    if (open) {
      setCanal('email')
      setRequestId(gerarUuid())
      setPersonalizar(false)
      setMensagemCustom('')
      setResumoWhatsapp(null)
      setStatusWhatsapp('UNAVAILABLE')
      setEnviando(false)
    }
  }, [open, cliente?.id, template])

  useEffect(() => {
    if (!open || !(template === 'resgate' || template === 'reconexao')) return
    let ativo = true
    setCarregandoWhatsapp(true)
    buscarResumoWhatsapp()
      .then((resumo) => {
        if (ativo) {
          setResumoWhatsapp(resumo)
          setStatusWhatsapp(resumo?.conexao?.estado || 'UNAVAILABLE')
        }
      })
      .catch(() => {
        if (ativo) {
          setResumoWhatsapp(null)
          setStatusWhatsapp('UNAVAILABLE')
        }
      })
      .finally(() => {
        if (ativo) setCarregandoWhatsapp(false)
      })
    return () => { ativo = false }
  }, [open, template])

  if (!open || !cliente || !template) return null

  const tmpl = TEMPLATES[template] || TEMPLATES.resgate
  const nomeCliente = cliente.nome || 'cliente'
  const nomeEmpresa = cliente.empresaNome || 'nossa equipe'
  const mensagemPadrao = tmpl.mensagem.replace('{nome}', nomeCliente)
  const ehWhatsapp = canal === 'whatsapp'
  const whatsappConectado = statusWhatsapp === 'CONNECTED'
  const disponivelNoPlano = resumoWhatsapp ? Boolean(resumoWhatsapp.disponivelNoPlano) : true
  const crmUso = resumoWhatsapp?.uso?.crm
  const cotaCrmDisponivel = crmUso == null ? null : (crmUso.disponiveis ?? (crmUso.limite - crmUso.enviados - (crmUso.reservados || 0)))
  const semTelefone = !cliente.telefone
  const optOut = cliente.receberWhatsapp === false
  const cotaEsgotada = cotaCrmDisponivel != null && cotaCrmDisponivel <= 0
  const motivoBloqueioWhatsapp = !permiteWhatsapp ? null
    : !disponivelNoPlano ? 'O plano atual não possui ações de WhatsApp.'
    : !whatsappConectado ? 'Conecte o WhatsApp em Integrações para usar este canal.'
    : semTelefone ? 'Cliente sem telefone válido.'
    : optOut ? 'Este cliente optou por não receber mensagens pelo WhatsApp.'
    : cotaEsgotada ? 'Seu limite de ações CRM por WhatsApp foi atingido neste ciclo.'
    : null
  const whatsappBloqueado = permiteWhatsapp && motivoBloqueioWhatsapp != null
  const mensagemWhatsapp = (TEMPLATES_WHATSAPP[template] || '')
    .replaceAll('{cliente}', nomeCliente)
    .replaceAll('{empresa}', nomeEmpresa)

  function textoBotaoEnviar() {
    if (!enviando) return 'Enviar agora'
    if (ehWhatsapp) return 'Conectando ao WhatsApp...'
    return 'Enviando...'
  }

  async function handleEnviar() {
    if (enviando) return
    setEnviando(true)
    try {
      const payload = {
        template,
        canal,
        customMessage: canal === 'whatsapp' ? null : (personalizar ? mensagemCustom : null),
        requestId: canal === 'whatsapp' ? requestId : null,
      }
      let response = null
      let error = null
      try {
        response = await enviarMensagemCrm(cliente.id, payload)
      } catch (err) {
        error = err
      }
      if (error) {
        const statusCode = error?.response?.status
        const code = error?.response?.data?.code
        if (code === 'WHATSAPP_SERVICE_UNAVAILABLE' || code === 'WHATSAPP_CONNECT_TIMEOUT' || statusCode === 503 || statusCode === 504) {
          emitirToast('error', 'Não foi possível iniciar o WhatsApp. Tente novamente.')
        } else if (statusCode === 401 || statusCode === 403) {
          emitirToast('error', 'Não foi possível autenticar com o serviço do WhatsApp.')
        } else {
          const msg = error?.response?.data?.mensagem || 'Erro ao enviar. Tente novamente.'
          emitirToast('error', msg)
        }
        return
      }
      if (response && response.success === false) {
        const amigavel = canal === 'whatsapp' && response.status && ERROS_WHATSAPP[response.status]
        if (response.status === 'WHATSAPP_NOT_CONNECTED') {
          setStatusWhatsapp('UNAVAILABLE')
        }
        emitirToast('error', amigavel || response.mensagem || 'Nao foi possivel enviar.')
      } else if (canal === 'whatsapp') {
        emitirToast('success', 'Mensagem enviada')
        setPersonalizar(false)
        setMensagemCustom('')
        onEnviado?.()
        onClose()
      } else {
        emitirToast('success', `Email enviado pra ${nomeCliente}! `)
        setPersonalizar(false)
        setMensagemCustom('')
        onEnviado?.()
        onClose()
      }
    } catch (err) {
      const msg = err?.response?.data?.mensagem || 'Erro ao enviar. Tente novamente.'
      emitirToast('error', msg)
    } finally {
      setEnviando(false)
    }
  }

  function handleClose() {
    if (enviando) return
    setPersonalizar(false)
    setMensagemCustom('')
    onClose()
  }

  return (
    <Modal title={tmpl.titulo} open={open} onClose={handleClose}>
      <div className="crm-send-form">
          <div className="crm-send-summary">
            <div className="crm-send-detail">
              <span>Cliente</span>
              <strong>{nomeCliente}</strong>
            </div>
            <div className="crm-send-detail">
              <span>E-mail</span>
              <strong>{cliente.email || 'Não cadastrado'}</strong>
            </div>
            {permiteWhatsapp && (
              <div className="crm-send-detail">
                <span>WhatsApp</span>
                <strong>{cliente.telefone || 'Não cadastrado'}</strong>
              </div>
            )}
          </div>
          {permiteWhatsapp && (
            <div className="wpp-canal-row" role="radiogroup" aria-label="Canal de envio">
              <label>
                <input type="radio" name={`canal-${cliente.id}-${template}`} value="email" checked={canal === 'email'} onChange={() => setCanal('email')} disabled={enviando} />
                <span>E-mail</span>
              </label>
              <label>
                <input type="radio" name={`canal-${cliente.id}-${template}`} value="whatsapp" checked={canal === 'whatsapp'} disabled={whatsappBloqueado || enviando} onChange={() => { if (!whatsappBloqueado) setCanal('whatsapp') }} />
                <span>WhatsApp</span>
              </label>
            </div>
          )}
          {permiteWhatsapp && whatsappBloqueado && (
            <p className="wpp-note">
              {carregandoWhatsapp ? 'Consultando conexão do WhatsApp...' : motivoBloqueioWhatsapp}
            </p>
          )}
          <div className="crm-send-message">
            <span>{ehWhatsapp ? 'Mensagem padrão do gendaz' : 'Mensagem padrão'}</span>
            <div className="crm-send-message-preview">
              {ehWhatsapp ? mensagemWhatsapp : mensagemPadrao}
            </div>
          </div>
          {!ehWhatsapp && (
            <label className="crm-send-custom-toggle">
              <input type="checkbox" checked={personalizar} onChange={(e) => setPersonalizar(e.target.checked)} disabled={enviando} />
              <span>Personalizar mensagem</span>
            </label>
          )}
          {!ehWhatsapp && personalizar && (
            <label className="field crm-send-custom-message">
              <span>Mensagem personalizada</span>
              <textarea value={mensagemCustom} onChange={(e) => setMensagemCustom(e.target.value)} placeholder="Digite sua mensagem personalizada..." disabled={enviando} />
            </label>
          )}
          <div className="modal-actions crm-send-actions">
            <Button variant="secondary" onClick={handleClose} disabled={enviando}>Cancelar</Button>
            <Button onClick={handleEnviar} loading={enviando} loadingText={textoBotaoEnviar()} disabled={canal === 'whatsapp' ? whatsappBloqueado : !cliente.email}>
              Enviar agora
            </Button>
          </div>
      </div>
    </Modal>
  )
}
