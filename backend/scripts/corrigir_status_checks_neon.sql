alter table if exists empresas
    drop constraint if exists empresas_status_check;

alter table if exists empresas
    add constraint empresas_status_check
    check (status in ('ATIVA', 'INATIVA', 'BLOQUEADA', 'PENDENTE_PAGAMENTO'));

alter table if exists assinaturas
    drop constraint if exists assinaturas_status_check;

alter table if exists assinaturas
    add constraint assinaturas_status_check
    check (status in ('ATIVA', 'CANCELADA', 'EXPIRADA', 'TESTE', 'PENDENTE_PAGAMENTO'));

alter table if exists pagamentos_planos
    drop constraint if exists pagamentos_planos_status_check;

alter table if exists pagamentos_planos
    add constraint pagamentos_planos_status_check
    check (status in ('PAYMENT_PENDING', 'PAYMENT_APPROVED', 'PAYMENT_REJECTED', 'PAYMENT_CANCELED', 'PAYMENT_EXPIRED'));
