# Política de Segurança — Gendaz

Este documento define práticas, responsabilidades e orientações de segurança para desenvolvimento, manutenção e operação do SaaS Gendaz.

## Escopo

Esta política cobre:

- Backend Java 17 / Spring Boot;
- Frontend React / Vite;
- APIs REST;
- WebSocket;
- PostgreSQL;
- Autenticação, sessões e cookies;
- Variáveis de ambiente e segredos;
- Logs, auditoria e monitoramento;
- Processo de reporte e correção de vulnerabilidades.

## Princípios obrigatórios

1. **Menor privilégio**: usuários, endpoints, serviços e credenciais devem ter apenas os acessos necessários.
2. **Sessões seguras**: tokens de sessão devem permanecer em cookies `HttpOnly`, `Secure` e com `SameSite` adequado.
3. **Nada sensível no frontend**: não armazenar senha, token, chave de API, cookie de sessão ou segredo em `localStorage`, `sessionStorage`, query string ou código frontend.
4. **Sem segredos versionados**: nunca commitar `.env`, senhas, tokens, chaves privadas, secrets de pagamento, API keys ou credenciais reais.
5. **Validação no backend**: toda entrada do usuário deve ser validada no backend, mesmo que também exista validação no frontend.
6. **Não expor dados sensíveis em logs**: logs não podem conter senha, token, cookie, CSRF, sessão, chave de API ou dados completos desnecessários.
7. **Mudanças mínimas e testadas**: alterações de segurança devem preservar funcionalidades existentes e ter testes/validação.

## Autenticação e sessão

- A sessão principal deve usar cookie `HttpOnly`.
- O cookie de sessão deve usar `Secure` em produção.
- O frontend não deve ler nem manipular diretamente o token de sessão.
- Logout deve invalidar a sessão no backend e limpar cookies relacionados.
- Refresh de sessão deve validar sessão ativa no backend.
- WebSocket deve validar sessão/cookie no handshake antes de aceitar conexão.
- Não enviar tokens em query string.

## CSRF, CORS e Origin

- Requisições mutantes (`POST`, `PUT`, `PATCH`, `DELETE`) devem exigir proteção CSRF quando usam cookies.
- O token CSRF pode ser acessível ao frontend quando necessário para envio no header, mas o cookie de sessão nunca deve ser acessível por JavaScript.
- CORS deve aceitar somente origens conhecidas:
  - domínio oficial do frontend;
  - domínio com `www`, se usado;
  - localhost apenas em desenvolvimento.
- Não usar `*` em CORS com credenciais.
- Validar `Origin`/`Referer` em fluxos sensíveis.

## Senhas

- Senhas devem ser armazenadas somente com hash forte, como BCrypt.
- Nunca armazenar senha em texto puro.
- Nunca logar senha.
- Nunca retornar senha em DTO/API.
- Recuperação de senha deve usar token temporário, aleatório, com expiração e uso único.
- Mensagens públicas de recuperação devem ser genéricas para evitar enumeração de e-mails.

## Rate limiting e brute force

- Endpoints públicos sensíveis devem possuir rate limiting, especialmente:
  - login;
  - cadastro;
  - recuperação de senha;
  - login admin;
  - endpoints públicos de alto custo.
- Tentativas repetidas de login devem ser registradas no monitoramento de segurança.
- Bloqueios devem ser proporcionais e não devem expor detalhes internos ao usuário.

## Logs e monitoramento

Logs de segurança devem usar prefixos claros, por exemplo:

```txt
[SECURITY]
[SECURITY_MONITOR]
```

Eventos recomendados para registro:

- login falhado;
- login bem-sucedido;
- logout;
- troca de senha;
- recuperação de senha solicitada;
- rate limit acionado;
- tentativa com origem inválida;
- sessão inválida;
- handshake WebSocket negado;
- tentativa administrativa falhada.

Os logs devem mascarar identificadores sensíveis. Exemplo:

```txt
vi***@dominio.com
```

Não logar:

- senha;
- token;
- cookie;
- CSRF token;
- chave de API;
- secrets de pagamento;
- conteúdo integral de Authorization header.

## Variáveis de ambiente e segredos

Variáveis sensíveis devem ser configuradas no provedor de deploy e nunca versionadas.

Exemplos de variáveis sensíveis:

- `JWT_SECRET`;
- `SUPER_ADMIN_PASSWORD`;
- `DATABASE_URL`;
- `DATABASE_PASSWORD`;
- `STRIPE_SECRET_KEY`;
- `STRIPE_WEBHOOK_SECRET`;
- `PAYMENT_WEBHOOK_SECRET`;
- `RESEND_API_KEY`;
- `APP_DATA_ENCRYPTION_KEY`;
- tokens internos de integração.

A aplicação deve rejeitar valores padrão inseguros, como:

```txt
replace-with-
example_
troque-este-
local-dev-
```

## Banco de dados e dados sensíveis

- Usar migrations controladas.
- Não usar `ddl-auto=update` em produção sem avaliação explícita.
- Não criar migrations destrutivas sem backup e plano de rollback.
- E-mails, telefones e documentos são dados sensíveis.
- Criptografia direta de colunas usadas em login/busca/unicidade deve ser feita somente com estratégia segura.

Estratégia recomendada para criptografia futura de e-mail/telefone:

1. Criar coluna criptografada separada, por exemplo `email_criptografado`.
2. Criar hash normalizado para busca, por exemplo `email_normalizado_hash`.
3. Realizar backfill incremental.
4. Trocar queries/repositories gradualmente.
5. Validar unicidade.
6. Só depois remover ou anonimizar colunas antigas.

Nunca criptografar diretamente a coluna atual de login sem migração planejada.

## Frontend

- Não armazenar tokens de sessão no navegador.
- Evitar `localStorage` para dados persistentes desnecessários.
- `sessionStorage` pode ser usado somente para preferências não sensíveis.
- Sanitizar HTML dinâmico com biblioteca apropriada, como DOMPurify.
- Evitar `dangerouslySetInnerHTML`; se necessário, sanitizar antes.
- Não incluir secrets em variáveis `VITE_*`, pois elas ficam públicas no bundle.

## APIs REST

- Endpoints privados devem exigir autenticação.
- Endpoints administrativos devem exigir perfil administrativo.
- DTOs devem usar validação (`@Valid`, `@NotBlank`, `@Email`, limites de tamanho etc.).
- Não retornar stack trace ou mensagens internas ao usuário final.
- Não retornar entidades completas se houver campos sensíveis.
- Sempre validar empresa/tenant antes de acessar dados multi-tenant.

## WebSocket

- Handshake deve validar origem permitida.
- Handshake deve validar cookie/sessão ativa.
- Conexões sem sessão válida devem ser rejeitadas.
- Não passar token em query string.
- Não logar cookie, token ou sessão.

## Dependências

Antes de release:

Backend:

```bash
cd backend
mvn test
```

Frontend:

```bash
cd frontend
npm run build
```

Também é recomendado executar auditorias de dependência periodicamente:

```bash
cd frontend
npm audit
```

Para Maven, usar ferramenta compatível com o pipeline do projeto, como OWASP Dependency-Check, Snyk, GitHub Dependabot ou equivalente.

## Checklist mínimo antes de produção

- [ ] `mvn test` passando.
- [ ] `npm run build` passando.
- [ ] Sem secrets reais versionados.
- [ ] Cookies de sessão com `HttpOnly` e `Secure` em produção.
- [ ] CORS restrito a domínios oficiais.
- [ ] CSRF ativo onde necessário.
- [ ] Rate limiting ativo em endpoints sensíveis.
- [ ] Logs sem senha/token/cookie.
- [ ] WebSocket rejeita conexão sem sessão válida.
- [ ] Recuperação de senha não permite enumeração de e-mail.
- [ ] Variáveis obrigatórias configuradas no ambiente de produção.
- [ ] Banco de produção com SSL quando aplicável.
- [ ] Backup do banco validado.

## Processo para reporte de vulnerabilidade

Ao identificar uma vulnerabilidade:

1. Não explorar além do necessário para confirmar o problema.
2. Não expor dados de usuários.
3. Registrar:
   - descrição;
   - impacto;
   - passos de reprodução;
   - ambiente afetado;
   - evidências sem dados sensíveis.
4. Classificar severidade:
   - Crítica;
   - Alta;
   - Média;
   - Baixa.
5. Corrigir em branch separada.
6. Testar regressão.
7. Fazer revisão de diff para remover alterações fora de escopo.
8. Publicar correção.

## Severidade e prazo recomendado

| Severidade | Exemplos | Prazo recomendado |
|---|---|---|
| Crítica | bypass de autenticação, vazamento de dados, RCE, SQL injection explorável | imediato / até 24h |
| Alta | XSS explorável, CSRF crítico, acesso indevido entre empresas | até 72h |
| Média | enumeração, rate limit ausente, headers incompletos | até 14 dias |
| Baixa | documentação, hardening menor, melhoria preventiva | planejamento normal |

## Contato interno

Definir responsável interno pela segurança do projeto e canal de contato operacional antes do lançamento em produção.

Exemplo:

```txt
Security Owner: <nome/responsavel>
Contato: <email/canal interno>
```

## Observação final

Segurança deve ser tratada como processo contínuo. Toda nova funcionalidade que envolva autenticação, dados pessoais, pagamentos, WebSocket, arquivos, integrações externas ou permissões deve passar por revisão de segurança antes de produção.
