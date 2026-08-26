import { useEffect, useState } from 'react'

export default function OperationToast() {
  const [toast, setToast] = useState(null)

  useEffect(() => {
    let timeoutId = null

    function limparTimeout() {
      if (timeoutId) {
        window.clearTimeout(timeoutId)
        timeoutId = null
      }
    }

    function onToast(event) {
      const detail = event?.detail || {}
      const type = detail.type || 'success'
      limparTimeout()
      setToast({
        id: detail.id || null,
        type,
        message: detail.message || '',
      })
      if (type !== 'loading') {
        timeoutId = window.setTimeout(() => {
          setToast(null)
          timeoutId = null
        }, 2600)
      }
    }

    function onDismiss(event) {
      const id = event?.detail?.id || null
      setToast((atual) => {
        if (id && atual?.id !== id) return atual
        limparTimeout()
        return null
      })
    }

    window.addEventListener('gendaz:toast', onToast)
    window.addEventListener('gendaz:toast-dismiss', onDismiss)
    return () => {
      window.removeEventListener('gendaz:toast', onToast)
      window.removeEventListener('gendaz:toast-dismiss', onDismiss)
      limparTimeout()
    }
  }, [])

  if (!toast?.message) return null

  const classe = toast.type === 'error'
    ? 'error'
    : toast.type === 'loading'
      ? 'loading'
      : 'success'

  return (
    <div className={`admin-toast ${classe}`} role="status" aria-live="polite">
      <span>{toast.message}</span>
      {toast.type !== 'loading' && (
        <button type="button" aria-label="Fechar notificação" onClick={() => setToast(null)}>x</button>
      )}
    </div>
  )
}

