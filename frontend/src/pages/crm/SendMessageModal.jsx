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
    <div className="modal-backdrop system-modal-backdrop" role="presentation" onClick={(e) => { if (e.target === e.currentTarget) handleClose() }}>
      <section className="modal system-modal" style={{ minWidth: 480, maxWidth: 600, background: 'var(--surface-solid, var(--surface-strong, var(--surface)))', color: 'var(--text)' }}>
        <div className="modal-header" style={{ background: 'var(--surface-solid, var(--surface-strong, var(--surface)))', borderBottom: '1px solid var(--line)' }}>
          <h2 style={{ color: 'var(--text)' }}>{tmpl.titulo}</h2>
          <button type="button" className="icon-btn" onClick={handleClose} aria-label="Fechar modal">
            <span style={{ fontSize: 18, lineHeight: 1 }}>×</span>
          </button>
        </div>

        <div style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 16, background: 'var(--surface-solid, var(--surface-strong, var(--surface)))' }}>
          <div>
            <div style={{ color: 'var(--muted)', fontSize: 13, marginBottom: 4 }}>Cliente</div>
            <div style={{ color: 'var(--text)', fontSize: 14 }}>{nomeCliente}</div>
          </div>

          <div>
            <div style={{ color: 'var(--muted)', fontSize: 13, marginBottom: 4 }}>Email</div>
            <div style={{ color: 'var(--text)', fontSize: 14 }}>{cliente.email || 'Nao cadastrado'}</div>
          </div>

          <div>
            <div style={{ color: 'var(--muted)', fontSize: 13, marginBottom: 4 }}>Mensagem padrao</div>
            <div style={{
              padding: 16,
              background: 'var(--surface-soft)',
              border: '1px solid var(--line)',
              borderRadius: 8,
              color: 'var(--text)',
              fontSize: 14,
              lineHeight: 1.5,
              whiteSpace: 'pre-wrap',
            }}>
              {mensagemPadrao}
            </div>
          </div>

          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 14, color: 'var(--text)', lineHeight: 1.3 }}>
            <input
              type="checkbox"
              checked={personalizar}
              onChange={(e) => setPersonalizar(e.target.checked)}
              style={{ accentColor: 'var(--text)', width: 18, height: 18, flexShrink: 0, margin: 0 }}
            />
            Personalizar mensagem
          </label>

          {personalizar && (
            <textarea
              value={mensagemCustom}
              onChange={(e) => setMensagemCustom(e.target.value)}
              placeholder="Digite sua mensagem personalizada..."
              style={{
                width: '100%',
                minHeight: 120,
                maxHeight: 120,
                padding: 12,
                background: 'var(--surface-solid, var(--surface-strong, var(--surface)))',
                border: '1px solid var(--line)',
                borderRadius: 8,
                color: 'var(--text)',
                fontSize: 14,
                resize: 'vertical',
                outline: 'none',
                boxSizing: 'border-box',
              }}
            />
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12, marginTop: 8, alignItems: 'center' }}>
            <Button variant="secondary" onClick={handleClose} style={{ height: 40, padding: '12px 16px', fontSize: 14 }}>Cancelar</Button>
            <Button onClick={handleEnviar} disabled={enviando || !cliente.email} style={{ height: 40, padding: '12px 16px', fontSize: 14 }}>
              {enviando ? 'Enviando...' : 'Enviar agora'}
            </Button>
          </div>
        </div>
      </section>
    </div>
  )
}

