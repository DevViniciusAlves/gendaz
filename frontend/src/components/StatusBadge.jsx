const LABELS = {
  ATIVA: 'Ativa',
  INATIVA: 'Inativa',
  BLOQUEADA: 'Bloqueada',
  PENDENTE_PAGAMENTO: 'Pendente de pagamento',
  ATIVA_PAGAMENTO: 'Ativa',
  ATIVO: 'Ativo',
  INATIVO: 'Inativo',
  TESTE: 'Teste',
  EXPIRADA: 'Expirada',
  CANCELADA: 'Cancelada',
  APROVADO: 'Aprovado',
  PAGO: 'Pago',
  PENDENTE: 'Pendente',
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
  INFO: 'Info',
  WARNING: 'Aviso',
  SECURITY: 'Segurança',
  ERROR: 'Erro',
}

export default function StatusBadge({ status }) {
  const normalized = String(status || '').toLowerCase().replaceAll('_', '-').replaceAll(' ', '-')
  const label = LABELS[String(status || '').toUpperCase()] || String(status || '').replaceAll('_', ' ')
  return (
    <span className={`status status-${normalized}`}>
      <span className="status-dot">●</span>
      {label}
    </span>
  )
}
