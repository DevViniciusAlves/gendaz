import Button from './Button.jsx'

export default function Pagination({ page, totalPages, totalItems, pageSize, onPageChange }) {
  if (totalPages <= 1) return null

  const inicio = (page - 1) * pageSize + 1
  const fim = Math.min(page * pageSize, totalItems)

  return (
    <div className="pagination-bar">
      <span>{inicio}-{fim} de {totalItems}</span>
      <div className="pagination-actions">
        <Button variant="secondary" disabled={page <= 1} onClick={() => onPageChange(page - 1)}>
          Anterior
        </Button>
        <span>Página {page} de {totalPages}</span>
        <Button variant="secondary" disabled={page >= totalPages} onClick={() => onPageChange(page + 1)}>
          Próxima
        </Button>
      </div>
    </div>
  )
}
