import { X } from 'lucide-react'
import { createPortal } from 'react-dom'

export default function Modal({ title, open, onClose, children }) {
  if (!open) return null

  return createPortal(
    <div className="modal-backdrop system-modal-backdrop" role="presentation" onClick={onClose}>
      <section
        className="modal system-modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="modal-header">
          <h2>{title}</h2>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Fechar modal"><X size={18} /></button>
        </div>
        {children}
      </section>
    </div>,
    document.body,
  )
}
