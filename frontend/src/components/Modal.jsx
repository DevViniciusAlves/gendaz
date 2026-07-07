import { X } from 'lucide-react'

export default function Modal({ title, open, onClose, children }) {
  if (!open) return null
  return (
    <div className="modal-backdrop" role="presentation">
      <section className="modal">
        <div className="modal-header">
          <h2>{title}</h2>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Fechar modal"><X size={18} /></button>
        </div>
        {children}
      </section>
    </div>
  )
}
