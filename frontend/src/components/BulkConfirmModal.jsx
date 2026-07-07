import Button from './Button.jsx'
import Modal from './Modal.jsx'

export default function BulkConfirmModal({ open, title, description, confirmLabel = 'Confirmar', danger = false, loading = false, onCancel, onConfirm }) {
  return (
    <Modal title={title} open={open} onClose={onCancel}>
      <div className="confirm-box">
        <p>{description}</p>
        <div className="confirm-actions">
          <Button variant="secondary" type="button" onClick={onCancel} disabled={loading}>
            Cancelar
          </Button>
          <Button
            type="button"
            variant={danger ? 'danger' : 'primary'}
            onClick={onConfirm}
            disabled={loading}
          >
            {loading ? 'Executando...' : confirmLabel}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
