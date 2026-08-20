export default function Table({ columns, rows = [], empty = 'Nenhum registro encontrado.', children }) {
  const normalizedColumns = columns.map((column) => typeof column === 'string' ? { key: column, label: column } : column)

  function renderCell(row, column) {
    if (column.render) return column.render(row)
    const value = row[column.key] ?? '-'
    const text = String(value)
    return <span className="cell-truncate" title={text}>{text}</span>
  }

  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>{normalizedColumns.map((column) => <th key={column.key}>{column.label}</th>)}</tr>
        </thead>
        <tbody>
          {children || (rows.length === 0 ? (
            <tr><td colSpan={normalizedColumns.length}>{empty}</td></tr>
          ) : rows.map((row) => (
            <tr key={row.id}>
              {normalizedColumns.map((column) => (
                <td
                  key={column.key}
                  data-label={column.label}
                  style={column.key === 'acao' ? { overflow: 'visible', position: 'relative' } : undefined}
                >
                  {renderCell(row, column)}
                </td>
              ))}
            </tr>
          )))}
        </tbody>
      </table>
    </div>
  )
}
