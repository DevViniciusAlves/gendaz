import { MoreHorizontal } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

export default function ActionMenu({ actions }) {
  const [open, setOpen] = useState(false)
  const [mobile, setMobile] = useState(() => (typeof window !== 'undefined' ? window.innerWidth <= 768 : false))
  const menuRef = useRef(null)
  const mobileMenuRef = useRef(null)

  useEffect(() => {
    function fecharAoClicarFora(event) {
      const clicouNoBotao = menuRef.current?.contains(event.target)
      const clicouNoMenuMobile = mobileMenuRef.current?.contains(event.target)

      if (!clicouNoBotao && !clicouNoMenuMobile) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', fecharAoClicarFora)
    document.addEventListener('pointerdown', fecharAoClicarFora)
    return () => {
      document.removeEventListener('mousedown', fecharAoClicarFora)
      document.removeEventListener('pointerdown', fecharAoClicarFora)
    }
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

  function toggleOpen(e) {
    e.stopPropagation()
    setOpen((current) => !current)
  }

  const panel = (
    <div
      className={`dropdown-panel action-menu-panel${mobile ? ' action-menu-panel-mobile' : ''}`}
      style={mobile ? undefined : { position: 'absolute', right: 0, top: '100%', zIndex: 10, minWidth: '150px' }}
      onPointerDown={(e) => e.stopPropagation()}
    >
      {actions.map((action, index) => (
        <button
          key={index}
          type="button"
          onPointerDown={(e) => e.stopPropagation()}
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
        onClick={toggleOpen}
        aria-label="Abrir menu de ações"
      >
        <MoreHorizontal size={16} />
      </button>

      {open && !mobile && panel}
      {open && mobile && createPortal(
        <div
          className="action-menu-mobile-layer"
          role="presentation"
          onPointerDown={() => setOpen(false)}
          onClick={() => setOpen(false)}
        >
          <div
            className="action-menu-mobile-sheet"
            ref={mobileMenuRef}
            onPointerDown={(event) => event.stopPropagation()}
            onClick={(event) => event.stopPropagation()}
          >
            {panel}
          </div>
        </div>,
        document.body,
      )}
    </div>
  )
}
