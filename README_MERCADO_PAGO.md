# Mercado Pago - AgendEasy

Este projeto esta preparado para ativar o Plano Pro somente depois da confirmacao real de pagamento pelo backend.

## Variaveis no Render

Configure no backend:

```env
PAYMENT_PROVIDER=MERCADO_PAGO
MERCADO_PAGO_ACCESS_TOKEN=seu_access_token_do_mercado_pago
MERCADO_PAGO_PUBLIC_KEY=sua_public_key_do_mercado_pago
PAYMENT_WEBHOOK_SECRET=secret_signature_do_webhook
PAYMENT_SUCCESS_URL=https://gendaz.site/sistema/planos
PAYMENT_CANCEL_URL=https://gendaz.site/sistema/planos
```

Nao coloque token, public key ou secret no codigo.

## Webhook

Configure no Mercado Pago:

- Evento: `payment`
- Metodo: `POST`
- URL: `https://SEU_BACKEND/api/pagamentos/planos/webhook`

Exemplo com dominio final:

```text
https://api.gendaz.site/api/pagamentos/planos/webhook
```

O backend valida os headers enviados pelo Mercado Pago:

- `x-signature`
- `x-request-id`

E usa o parametro/corpo `data.id` para consultar o pagamento real no Mercado Pago.

## Fluxo

1. Usuario escolhe Plano Pro.
2. Backend cria pagamento pendente.
3. Backend cria cobranca real no Mercado Pago.
4. Frontend mostra PIX ou link de checkout.
5. Mercado Pago envia webhook quando o pagamento muda de status.
6. Backend consulta o pagamento no Mercado Pago.
7. Se status for `approved`, o backend:
   - marca pagamento como `PAYMENT_APPROVED`;
   - ativa assinatura;
   - altera empresa para ativa;
   - libera Plano Pro.

Status pendente, recusado, cancelado ou expirado nao libera o Plano Pro.

## Valor temporario de teste

O Plano Pro esta temporariamente configurado com valor de teste:

```text
R$ 0,50
```

Para voltar ao valor real depois, altere:

- `backend/src/main/java/com/minhaempresa/agendapro/plano/service/PlanoBootstrap.java`
- crie uma nova migration atualizando `planos.valor_mensal`
- fallback do frontend em `frontend/src/pages/CriarConta.jsx`
- fallback do frontend em `frontend/src/pages/Planos.jsx`

## Teste

1. Suba o backend no Render com as variaveis acima.
2. Crie uma conta escolhendo Plano Pro.
3. Confira se o sistema mostra a tela de pagamento pendente.
4. Para PIX, pague usando QR Code ou copia e cola.
5. Para cartao, abra o checkout seguro do Mercado Pago.
6. Aguarde o webhook ou clique em `Ja paguei, verificar`.

## Confirmacao no banco

Depois de aprovado, confira:

```sql
select id, empresa_id, status, provider, provider_payment_id, external_reference
from pagamentos_planos
order by id desc;

select id, empresa_id, plano_id, status
from assinaturas
order by id desc;

select id, nome_fantasia, status
from empresas
order by id desc;
```

O pagamento deve estar `PAYMENT_APPROVED`, a assinatura deve estar ativa e a empresa deve estar ativa.

