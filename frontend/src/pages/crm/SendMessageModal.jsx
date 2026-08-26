import { useState } from 'react'
import Modal from '../../components/Modal.jsx'
import Button from '../../components/Button.jsx'
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
    mensagem: '{nome}, faz tempo que nao aparece por aqui! Queremos saber como voce esta. Que tal um novo atendimento?',
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

export default function SendMessageModal({ open, onClose, cliente, template, onEnviado }) {
  const [personalizar, setPersonalizar] = useState(false)
  const [mensagemCustom, setMensagemCustom] = useState('')
  const [enviando, setEnviando] = useState(false)

  if (!open || !cliente || !template) return null

  const tmpl = TEMPLATES[template] || TEMPLATES.resgate
  const nomeCliente = cliente.nome || 'cliente'
  const mensagemPadrao = tmpl.mensagem.replace('{nome}', nomeCliente)

  async function handleEnviar() {
    if (enviando) return
    setEnviando(true)
    try {
      emitirToast('loading', 'Enviando...')
      const response = await enviarMensagemCrm(cliente.id, {
        template,
        canal: 'email',
        customMessage: personalizar ? mensagemCustom : null,
      })
      if (response && response.success === false) {
        emitirToast('error', response.mensagem || 'Nao foi possivel enviar.')
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
          </div>

          <div className="crm-send-message">
            <span>Mensagem padrão</span>
            <div className="crm-send-message-preview">
              {mensagemPadrao}
            </div>
          </div>

          <label className="crm-send-custom-toggle">
            <input
              type="checkbox"
              checked={personalizar}
              onChange={(e) => setPersonalizar(e.target.checked)}
            />
            <span>Personalizar mensagem</span>
          </label>

          {personalizar && (
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
            <Button onClick={handleEnviar} loading={enviando} loadingText="Enviando..." disabled={!cliente.email}>Enviar agora</Button>
          </div>
      </div>
    </Modal>
  )
}

