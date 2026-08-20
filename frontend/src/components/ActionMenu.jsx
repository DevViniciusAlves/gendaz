import { MoreHorizontal } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

export default function ActionMenu({ actions }) {
  const [open, setOpen] = useState(false)
  const [mobile, setMobile] = useState(() => (typeof window !== 'undefined' ? window.innerWidth <= 768 : false))
  const menuRef = useRef(null)

  useEffect(() => {
    function fecharAoClicarFora(event) {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', fecharAoClicarFora)
    return () => document.removeEventListener('mousedown', fecharAoClicarFora)
  }, [])

  useEffect(() => {
    function syncViewportMode() {
      setMobile(window.innerWidth <= 768)
    }
    syncViewportMode()
    window.addEventListener('resize', syncViewportMode)
    return () => window.removeEventListener('resize', syncViewportMode)
  }, [])

  function handleClick(action, e) {
    e.stopPropagation()
    setOpen(false)
    if (action.onClick) action.onClick()
  }

  const panel = (
    <div
      className={`dropdown-panel action-menu-panel${mobile ? ' action-menu-panel-mobile' : ''}`}
      style={mobile ? undefined : { position: 'absolute', right: 0, top: '100%', zIndex: 10, minWidth: '150px' }}
    >
      {actions.map((action, index) => (
        <button
          key={index}
          type="button"
          onClick={(e) => handleClick(action, e)}
          className={action.danger ? 'action-danger' : ''}
          disabled={action.disabled}
          style={{ display: 'flex', alignItems: 'center', gap: '8px', width: '100%', textAlign: 'left', padding: '8px 12px' }}
        >
          {action.icon && <action.icon size={14} />}
          {action.label}
        </button>
      ))}
    </div>
  )

  return (
    <div className="action-menu-container" ref={menuRef} style={{ position: 'relative' }}>
      <button
        type="button"
        className="icon-btn action-menu-btn"
        onClick={(e) => {
          e.stopPropagation()
          setOpen(!open)
        }}
        aria-label="Abrir menu de ações"
      >
        <MoreHorizontal size={16} />
      </button>

      {open && !mobile && panel}
      {open && mobile && createPortal(
        <div className="action-menu-mobile-layer" role="presentation" onClick={() => setOpen(false)}>
          <div className="action-menu-mobile-sheet" onClick={(event) => event.stopPropagation()}>
            {panel}
          </div>
        </div>,
        document.body,
      )}
    </div>
  )
}
