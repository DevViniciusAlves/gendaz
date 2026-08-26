import StatusBadge from './StatusBadge.jsx'

export default function ConversationList({ conversas, selectedId, onSelect, children, emptyText = 'Nenhuma conversa encontrada.' }) {
  return (
    <aside className="conversation-list">
      <div className="conversation-list-head">
        <h2>Conversas</h2>
        {children}
      </div>
      {conversas.length === 0 ? (
        <p className="conversation-empty">{emptyText}</p>
      ) : (
        conversas.map((conversa) => (
          <button key={conversa.id} className={selectedId === conversa.id ? 'conversation active' : 'conversation'} onClick={() => onSelect(conversa.id)}>
            <strong>{conversa.clienteNome}</strong>
            <span>{conversa.ultimaMensagem}</span>
            <StatusBadge status={conversa.status} />
          </button>
        ))
      )}
    </aside>
  )
}
