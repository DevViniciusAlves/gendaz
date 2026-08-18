import { AlertTriangle, Download, X } from 'lucide-react'
import Button from './Button.jsx'

export default function ConfirmacaoModal({ open, titulo, mensagem, tipo = 'danger', acaoLabel = 'Confirmar', carregando = false, onConfirmar, onCancelar }) {
  if (!open) return null

  const Icone = tipo === 'danger' ? AlertTriangle : Download

  return (
    <div className="modal-backdrop" role="presentation">
      <section className="modal confirm-modal" role="alertdialog" aria-modal="true" aria-labelledby="confirm-modal-titulo">
        <div className="modal-header">
          <h2 id="confirm-modal-titulo">{titulo}</h2>
          <button type="button" className="icon-btn" onClick={onCancelar} disabled={carregando} aria-label="Fechar confirmação">
            <X size={18} />
          </button>
        </div>
        <div className={`confirm-modal-icon${tipo === 'neutral' ? ' is-neutral' : ''}`}>
          <Icone size={22} />
        </div>
        <p className="confirm-modal-message">{mensagem}</p>
        <div className="confirm-modal-actions">
          <Button variant="secondary" onClick={onCancelar} disabled={carregando}>Cancelar</Button>
          <Button variant={tipo === 'danger' ? 'danger' : 'primary'} onClick={onConfirmar} loading={carregando}>
            {acaoLabel}
          </Button>
        </div>
      </section>
    </div>
  )
}
