import { useEffect, useState } from 'react'
import Modal from '../../components/Modal.jsx'
import Button from '../../components/Button.jsx'
import { gerarUuid } from '../../api/appApi.js'
import { enviarMensagemCrm } from '../../api/crmApi.js'

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

// Texto idêntico aos templates fixos do backend (CrmService): somente
// leitura aqui, a mensagem realmente enviada nunca muda por esta tela.
const TEMPLATES_WHATSAPP = {
  resgate: 'Olá, {cliente}! Tudo bem? Sentimos sua falta na {empresa}. Se quiser agendar um novo atendimento, estamos à disposição.',
  reconexao: 'Olá, {cliente}! Tudo bem? Passando para saber como você está. Quando quiser voltar à {empresa}, estaremos por aqui.',
}

const ERROS_WHATSAPP = {
  WHATSAPP_NAO_DISPONIVEL_NO_PLANO: 'O plano atual não possui ações de WhatsApp.',
  WHATSAPP_TELEFONE_INVALIDO: 'Este cliente não possui um telefone válido para WhatsApp.',
  WHATSAPP_OPT_OUT: 'Este cliente optou por não receber mensagens pelo WhatsApp.',
  WHATSAPP_REQUEST_ID_INVALIDO: 'Não foi possível identificar a ação. Feche e abra novamente.',
  WHATSAPP_TIPO_NAO_SUPORTADO: 'Este tipo de mensagem ainda não possui WhatsApp.',
}

export default function SendMessageModal({ open, onClose, cliente, template, onEnviado }) {
  const [personalizar, setPersonalizar] = useState(false)
  const [mensagemCustom, setMensagemCustom] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [canal, setCanal] = useState('email')
  const [requestId, setRequestId] = useState(null)

  const permiteWhatsapp = template === 'resgate' || template === 'reconexao'

  // requestId estável por ação manual: gerado ao abrir, mantido no retry.
  useEffect(() => {
    if (open) {
      setCanal('email')
      setRequestId(gerarUuid())
      setPersonalizar(false)
      setMensagemCustom('')
    }
  }, [open, cliente?.id, template])

  if (!open || !cliente || !template) return null

  const tmpl = TEMPLATES[template] || TEMPLATES.resgate
  const nomeCliente = cliente.nome || 'cliente'
  const mensagemPadrao = tmpl.mensagem.replace('{nome}', nomeCliente)
  const ehWhatsapp = canal === 'whatsapp'
  const mensagemWhatsapp = (TEMPLATES_WHATSAPP[template] || '').replace('{cliente}', nomeCliente)

  async function handleEnviar() {
    if (enviando) return
    setEnviando(true)
    try {
      emitirToast('loading', 'Enviando...')
      const response = await enviarMensagemCrm(cliente.id, {
        template,
        canal,
        customMessage: canal === 'whatsapp' ? null : (personalizar ? mensagemCustom : null),
        requestId: canal === 'whatsapp' ? requestId : null,
      })
      if (response && response.success === false) {
        const amigavel = canal === 'whatsapp' && response.status && ERROS_WHATSAPP[response.status]
        emitirToast('error', amigavel || response.mensagem || 'Nao foi possivel enviar.')
      } else if (canal === 'whatsapp') {
        emitirToast('success', 'Mensagem adicionada à fila do WhatsApp.')
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
                <input
                  type="radio"
                  name={`canal-${cliente.id}-${template}`}
                  value="email"
                  checked={canal === 'email'}
                  onChange={() => setCanal('email')}
                />
                <span>E-mail</span>
              </label>
              <label>
                <input
                  type="radio"
                  name={`canal-${cliente.id}-${template}`}
                  value="whatsapp"
                  checked={canal === 'whatsapp'}
                  onChange={() => setCanal('whatsapp')}
                />
                <span>WhatsApp</span>
              </label>
            </div>
          )}

          <div className="crm-send-message">
            <span>{ehWhatsapp ? 'Mensagem padrão do gendaz' : 'Mensagem padrão'}</span>
            <div className="crm-send-message-preview">
              {ehWhatsapp ? mensagemWhatsapp : mensagemPadrao}
            </div>
          </div>

          {!ehWhatsapp && (
            <label className="crm-send-custom-toggle">
              <input
                type="checkbox"
                checked={personalizar}
                onChange={(e) => setPersonalizar(e.target.checked)}
              />
              <span>Personalizar mensagem</span>
            </label>
          )}

          {!ehWhatsapp && personalizar && (
            <label className="field crm-send-custom-message">
              <span>Mensagem personalizada</span>
              <textarea
                value={mensagemCustom}
                onChange={(e) => setMensagemCustom(e.target.value)}
                placeholder="Digite sua mensagem personalizada..."
              />
            </label>
          )}

          <div className="modal-actions crm-send-actions">
            <Button variant="secondary" onClick={handleClose}>Cancelar</Button>
            <Button
              onClick={handleEnviar}
              loading={enviando}
              loadingText="Enviando..."
              disabled={canal === 'whatsapp' ? !cliente.telefone : !cliente.email}
            >
              Enviar agora
            </Button>
          </div>
      </div>
    </Modal>
  )
}

