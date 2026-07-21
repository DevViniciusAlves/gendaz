# Deploy do backend AgendEasy no Render com Docker

Este guia configura o backend Spring Boot da pasta `backend/` como Web Service no Render usando Runtime Docker e banco PostgreSQL Neon.

## 1. Criar o servico

1. Acesse o Render.
2. Clique em `New`.
3. Escolha `Web Service`.
4. Selecione o repositorio `DevViniciusAlves/AgendEasy`.
5. Configure:

```text
Name: agendeasy-api
Root Directory: backend
Runtime: Docker
Dockerfile Path: ./Dockerfile
```

## 2. Variaveis de ambiente

Cadastre estas variaveis no Render. Nao coloque valores reais no codigo.

```env
SPRING_PROFILES_ACTIVE=prod
PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://example-neon-host.neon.tech:5432/example_database?sslmode=require
DATABASE_USERNAME=example_user
DATABASE_PASSWORD=example_password
JWT_SECRET=replace-with-render-secret-value
FRONTEND_URL=https://example.pages.dev
JPA_DDL_AUTO=update
PAYMENT_PROVIDER=local
PAYMENT_API_KEY=replace-with-provider-api-key
PAYMENT_WEBHOOK_SECRET=replace-with-provider-webhook-secret
PAYMENT_SUCCESS_URL=https://example.pages.dev/sistema/planos
PAYMENT_CANCEL_URL=https://example.pages.dev/sistema/planos
APP_SEED_TEST_DATA=false
```

## 3. Neon PostgreSQL

Use a URL em formato JDBC:

```text
jdbc:postgresql://HOST_DO_NEON:5432/NOME_DO_BANCO?sslmode=require
```

O parametro `sslmode=require` e importante para conexoes com Neon.

## 4. Deploy

1. Confirme as configuracoes.
2. Clique em `Create Web Service`.
3. Aguarde o build do Dockerfile.
4. Quando o Render gerar a URL publica, teste:

```text
https://sua-api.onrender.com/api/planos
```

## 5. Cloudflare Pages

Depois que o backend estiver no ar, configure o frontend com:

```env
VITE_API_URL=https://sua-api.onrender.com/api
VITE_MODO_DEMO=false
```

## 6. Seguranca

- Nao commite `.env` real.
- Nao coloque senha Neon no repositorio.
- Nao coloque tokens sensiveis no codigo.
- Nao use JWT secret real em arquivo versionado.
- Use `backend/.env.example` apenas como modelo seguro.

## 7. Health-check e monitor externo

O endpoint publico `GET /health` deve responder rapido, sem autenticação e sem acesso pesado ao banco.

Ele devolve `200` com `{"status":"ok"}` e envia `Cache-Control: no-store`.

Para evitar hibernacao no Render Free, use um monitor externo apontando para:

`https://api.gendaz.site/health`

Configuração sugerida:

- Metodo: `GET`
- Intervalo: 5 ou 10 minutos
- Resposta esperada: `HTTP 200`

Exemplos de monitor:

- UptimeRobot
- cron-job.org
- GitHub Actions agendado

Nao dependa de scheduler interno para acordar a propria aplicação.

