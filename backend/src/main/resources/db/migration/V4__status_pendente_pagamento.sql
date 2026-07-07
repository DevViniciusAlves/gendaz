update empresas e
set status = 'PENDENTE_PAGAMENTO'
where exists (
    select 1
    from pagamentos_planos pp
    where pp.empresa_id = e.id
      and pp.status = 'PAYMENT_PENDING'
)
and not exists (
    select 1
    from assinaturas a
    join planos p on p.id = a.plano_id
    where a.empresa_id = e.id
      and p.nome = 'PRO'
      and a.status = 'ATIVA'
);

insert into assinaturas (empresa_id, plano_id, status, data_inicio)
select distinct pp.empresa_id, pp.plano_id, 'PENDENTE_PAGAMENTO', current_date
from pagamentos_planos pp
join planos p on p.id = pp.plano_id
where p.nome = 'PRO'
  and pp.status = 'PAYMENT_PENDING'
  and not exists (
      select 1
      from assinaturas a
      where a.empresa_id = pp.empresa_id
        and a.plano_id = pp.plano_id
        and a.status in ('PENDENTE_PAGAMENTO', 'ATIVA')
  );
