# Deploy do AgendEasy

## Visao geral

- Frontend: `https://gendaz.site`
- API: `https://api.gendaz.site`
- Banco: PostgreSQL Render DB

## Backend no Render

1. Crie um novo `Web Service` no Render.
2. Aponte o repositorio para a pasta `backend`.
3. Use `Docker` como ambiente de deploy.
4. O `Dockerfile` esta em `backend/Dockerfile`.
5. O backend usa a porta do Render via variavel `PORT`, com fallback para `8080`.

Variaveis no Render:

```env
SPRING_PROFILES_ACTIVE=prod
PORT=8080
DATABASE_URL=postgres://usuario:senha@hostname-do-render-db:5432/nome_do_banco?sslmode=require
DATABASE_USERNAME=usuario
DATABASE_PASSWORD=sua_senha_do_render_db
JWT_SECRET=troque-este-segredo
FRONTEND_URL=https://gendaz.site
JPA_DDL_AUTO=update
PAYMENT_PROVIDER=CAKTO
CAKTO_CLIENT_ID=troque-pelo-client-id-da-cakto
CAKTO_CLIENT_SECRET=troque-pelo-client-secret-da-cakto
CAKTO_WEBHOOK_SECRET=troque-pelo-segredo-do-webhook-cakto
CAKTO_PRODUCT_BASICO_ID=id-do-produto-basico
CAKTO_PRODUCT_PRO_ID=id-do-produto-pro
CAKTO_CHECKOUT_BASICO_URL=https://checkout.cakto.com.br/seu-checkout-basico
CAKTO_CHECKOUT_PRO_URL=https://checkout.cakto.com.br/seu-checkout-pro
PAYMENT_WEBHOOK_SECRET=troque-pelo-segredo-do-webhook
PAYMENT_SUCCESS_URL=https://gendaz.site/sistema/planos
PAYMENT_CANCEL_URL=https://gendaz.site/sistema/planos
SUPER_ADMIN_BOOTSTRAP_ENABLED=false
SUPER_ADMIN_EMAIL=admin@seudominio.com
SUPER_ADMIN_PASSWORD=senha-forte-apenas-no-render
SUPER_ADMIN_FORCE_PASSWORD_RESET=false
APP_SEED_TEST_DATA=false
```

Webhook da Cakto:

```text
https://api.gendaz.site/api/pagamentos/planos/webhook/cakto
```

O Plano Pro esta temporariamente em `R$ 0,10` para teste. Para voltar ao valor real, altere `PlanoBootstrap.VALOR_PRO_TESTE` e crie uma nova migration atualizando `planos.valor_mensal` para `110.00`.

## Banco Render DB

1. Crie um banco PostgreSQL no Render.
2. Copie a URL interna ou externa fornecida pelo Render.
3. Prefira usar `DATABASE_URL` no formato nativo do Render.
4. Se necessario, tambem funciona com `SPRING_DATASOURCE_URL` em formato JDBC.
5. Nao coloque credenciais reais no codigo.

Exemplo:

```env
DATABASE_URL=postgres://usuario:senha@hostname-do-render-db:5432/nome_do_banco?sslmode=require
```

## Frontend na Cloudflare Pages

1. Aponte o projeto para a pasta `frontend`.
2. Build command: `npm run build`
3. Output directory: `dist`
4. O arquivo `frontend/public/_redirects` garante rotas SPA no refresh.

Variaveis no Cloudflare Pages:

```env
VITE_API_URL=https://api.gendaz.site/api
VITE_MODO_DEMO=false
```

## Como testar

1. Abra `https://gendaz.site`.
2. Tente entrar e criar uma conta.
3. Confirme no backend se a resposta vem de `https://api.gendaz.site/api`.
4. Verifique se a conta nova cria empresa, usuario, plano e assinatura teste somente em ambiente local/de desenvolvimento.
5. Confirme se login, agenda, clientes, servicos, profissionais e pagamentos carregam sem erro.

## Primeiro Super Admin

Para criar o primeiro Super Admin com seguranÃ§a:

1. No Render, defina `SUPER_ADMIN_BOOTSTRAP_ENABLED=true`.
2. Defina `SUPER_ADMIN_EMAIL` com o e-mail do administrador.
3. Defina `SUPER_ADMIN_PASSWORD` com uma senha forte.
4. Suba o backend uma vez.
5. Acesse `/admin/login` com esse e-mail e senha.
6. Depois da criaÃ§Ã£o, altere `SUPER_ADMIN_BOOTSTRAP_ENABLED=false` e redeploy.

Nao existe rota publica para criar `SUPER_ADMIN`. O bootstrap so roda por variavel de ambiente.

Para trocar a senha do Super Admin depois que ele ja existe:

1. Atualize `SUPER_ADMIN_PASSWORD` no Render.
2. Defina `SUPER_ADMIN_FORCE_PASSWORD_RESET=true`.
3. Faca um redeploy do backend.
4. Entre com a nova senha.
5. Volte `SUPER_ADMIN_FORCE_PASSWORD_RESET=false` e faca novo redeploy.

A senha sempre e salva com BCrypt e nunca deve ser colocada no codigo.

## Observacoes

- `backend/.env.example` e `frontend/.env.example` trazem apenas exemplos seguros.
- O seed de demo fica bloqueado por `APP_SEED_TEST_DATA=false` e so roda quando essa flag estiver ligada manualmente em desenvolvimento.
- O profile `prod` pode apontar para `DATABASE_URL` ou `SPRING_DATASOURCE_URL`; o backend normaliza ambos.

