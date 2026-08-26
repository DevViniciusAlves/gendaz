const LABELS = {
  ATIVA: 'Ativa',
  INATIVA: 'Inativa',
  BLOQUEADA: 'Bloqueada',
  PENDENTE_PAGAMENTO: 'Pendente de pagamento',
  ATIVA_PAGAMENTO: 'Ativa',
  ATIVO: 'Ativo',
  INATIVO: 'Inativo',
  EXCLUIDO: 'Excluído',
  EXCLUIDA: 'Excluída',
  TESTE: 'Teste',
  EXPIRADA: 'Expirada',
  CANCELADA: 'Cancelada',
  APROVADO: 'Aprovado',
  PAGO: 'Pago',
  PENDENTE: 'Pendente',
  CONFIRMADO: 'Confirmado',
  EM_ATENDIMENTO: 'Em atendimento',
  PAUSADO: 'Pausado',
  ABERTO: 'Aberto',
  EM_ANALISE: 'Em análise',
  EM_ANDAMENTO: 'Em andamento',
  NAO_RESOLVIDO: 'Não resolvido',
  FECHADO: 'Fechado',
  PAYMENT_PENDING: 'Aguardando pagamento',
  PAYMENT_APPROVED: 'Pagamento aprovado',
  PAYMENT_REJECTED: 'Pagamento recusado',
  PAYMENT_CANCELED: 'Pagamento cancelado',
  PAYMENT_EXPIRED: 'Pagamento expirado',
  SITUACAO_CLIENTE: 'Situação do cliente',
  INFO: 'Info',
  WARNING: 'Aviso',
  SECURITY: 'Segurança',
  ERROR: 'Erro',
}

export default function StatusBadge({ status }) {
  const normalized = String(status || '').toLowerCase().replaceAll('_', '-').replaceAll(' ', '-')
  const upper = String(status || '').toUpperCase()
  const label = LABELS[upper] || String(status || '').replaceAll('_', ' ')
  const statusClass = normalized === 'excluido' ? 'excluido' : normalized
  return (
    <span className={`status status-${statusClass}`}>
      <span className="status-dot">●</span>
      {label}
    </span>
  )
}
