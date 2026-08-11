import { useEffect, useState } from 'react'

export default function OperationToast() {
  const [toast, setToast] = useState(null)

  useEffect(() => {
    let timeoutId = null

    function onToast(event) {
      const detail = event?.detail || {}
      const type = detail.type || 'success'
      setToast({
        type,
        message: detail.message || '',
      })
      if (timeoutId) window.clearTimeout(timeoutId)
      if (type !== 'loading') {
        timeoutId = window.setTimeout(() => {
          setToast(null)
        }, 2600)
      }
    }

    window.addEventListener('gendaz:toast', onToast)
    return () => {
      window.removeEventListener('gendaz:toast', onToast)
      if (timeoutId) window.clearTimeout(timeoutId)
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
        <button type="button" aria-label="Fechar notificaÃ§Ã£o" onClick={() => setToast(null)}>x</button>
      )}
    </div>
  )
}

