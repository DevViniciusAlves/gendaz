import { MoreHorizontal } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

export default function ActionMenu({ actions }) {
  const [open, setOpen] = useState(false)
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

  function handleClick(action, e) {
    e.stopPropagation()
    setOpen(false)
    if (action.onClick) action.onClick()
  }

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

      {open && (
        <div className="dropdown-panel action-menu-panel" style={{ position: 'absolute', right: 0, top: '100%', zIndex: 10, minWidth: '150px' }}>
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
      )}
    </div>
  )
}
