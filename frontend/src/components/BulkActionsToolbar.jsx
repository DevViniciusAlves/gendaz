import { MoreHorizontal, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import Button from './Button.jsx'

export default function BulkActionsToolbar({
  selectionMode,
  selectedCount,
  onToggleSelection,
  onClearSelection,
  actions = [],
  disabled = false,
  className = '',
}) {
  const [open, setOpen] = useState(false)
  const menuRef = useRef(null)

  useEffect(() => {
    function fecharAoClicarFora(event) {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
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
    if (!selectionMode) setOpen(false)
  }, [selectionMode])

  return (
    <div className={`mass-action-toolbar ${className}`.trim()} ref={menuRef}>
      <Button variant="secondary" onClick={() => {
        if (selectionMode) {
          onClearSelection?.()
        } else {
          onToggleSelection?.()
        }
      }}>
        {selectionMode ? 'Cancelar seleção' : 'Selecionar'}
      </Button>
      {selectionMode && <strong className="bulk-selection-count">{selectedCount} selecionados</strong>}
      <div style={{ position: 'relative' }}>
        <Button
          variant="secondary"
          icon={MoreHorizontal}
          onPointerDown={(e) => {
            e.stopPropagation()
            setOpen((value) => !value)
          }}
          onClick={(e) => {
            e.stopPropagation()
            setOpen((value) => !value)
          }}
          className="mass-action-icon-button"
          disabled={disabled || selectedCount === 0}
          aria-label="Abrir ações em massa"
        >
          ...
        </Button>
        {open && (
          <>
            <div className="action-menu-mobile-layer" role="presentation" onPointerDown={() => setOpen(false)} onClick={() => setOpen(false)}>
              <div className="action-menu-mobile-sheet mass-action-mobile-sheet" onPointerDown={(e) => e.stopPropagation()} onClick={(e) => e.stopPropagation()}>
                <div className="dropdown-panel action-menu-panel action-menu-panel-mobile mass-action-panel-mobile">
                  {selectedCount === 0 ? (
                    <button type="button" disabled style={{ width: '100%', textAlign: 'center', padding: '10px 12px' }}>
                      Selecione pelo menos um item.
                    </button>
                  ) : actions.map((action) => (
                    <button
                      key={action.label}
                      type="button"
                      className={action.danger ? 'action-danger' : ''}
                      onPointerDown={(e) => e.stopPropagation()}
                      onClick={() => {
                        setOpen(false)
                        action.onClick?.()
                      }}
                      style={{ width: '100%', textAlign: 'center', padding: '10px 12px' }}
                    >
                      {action.label}
                    </button>
                    ))}
                </div>
              </div>
            </div>
          </>
        )}
      </div>
      {selectionMode && (
        <Button variant="ghost" icon={X} onClick={onClearSelection}>
          Limpar
        </Button>
      )}
    </div>
  )
}
