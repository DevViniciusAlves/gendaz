import { MoreHorizontal } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

export default function ActionMenu({ actions }) {
  const [open, setOpen] = useState(false)
  const [mobile, setMobile] = useState(() => (typeof window !== 'undefined' ? window.innerWidth <= 768 : false))
  const [position, setPosition] = useState({ top: 0, left: 0 })
  const menuRef = useRef(null)
  const panelRef = useRef(null)
  const mobileMenuRef = useRef(null)

  useEffect(() => {
    function fecharAoClicarFora(event) {
      const clicouNoBotao = menuRef.current?.contains(event.target)
      const clicouNoPainel = panelRef.current?.contains(event.target)
      const clicouNoMenuMobile = mobileMenuRef.current?.contains(event.target)

      if (!clicouNoBotao && !clicouNoPainel && !clicouNoMenuMobile) {
        setOpen(false)
      }
    }
    document.addEventListener('pointerdown', fecharAoClicarFora)
    return () => {
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

  useEffect(() => {
    if (!open || mobile) return undefined

    function updatePosition() {
      const rect = menuRef.current?.getBoundingClientRect()
      if (!rect) return

      const width = 180
      const margin = 12
      const left = Math.min(
        Math.max(margin, rect.right - width),
        window.innerWidth - width - margin,
      )
      setPosition({ top: rect.bottom + 6, left })
    }

    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [open, mobile])

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
      ref={panelRef}
      className={`dropdown-panel action-menu-panel${mobile ? ' action-menu-panel-mobile' : ''}`}
      style={mobile ? undefined : { position: 'fixed', left: position.left, top: position.top, zIndex: 1000, minWidth: '180px' }}
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
        onPointerDown={(e) => e.stopPropagation()}
        onClick={toggleOpen}
        aria-label="Abrir menu de ações"
      >
        <MoreHorizontal size={16} />
      </button>

      {open && !mobile && createPortal(panel, document.body)}
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
