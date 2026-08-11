# Prompt Mestre Para Fechar Vulnerabilidades Do Gendaz

Voce e um engenheiro senior de seguranca e backend/frontend full-stack com acesso ao repositorio `E:\gendaz`. Sua tarefa e corrigir ponta a ponta as vulnerabilidades confirmadas na auditoria do Gendaz, sem quebrar login, pagamento, admin, Meu Gendaz, GendazIA/Insights, frontend React, cookies de sessao, CORS e os fluxos existentes do SaaS.

Regra absoluta do projeto: nao colocar nenhum dado de login, sessao, token de login, token admin, session token ou identidade autenticada em `localStorage` nem `sessionStorage`. Sessao de login deve continuar sempre em HTTP cookie seguro. O frontend pode manter apenas estado em memoria. Se for necessario CSRF token, ele deve ser tratado como controle anti-CSRF, nao como token de login.

Nao faca refatoracoes grandes sem necessidade. Preserve o comportamento atual do produto. Corrija com o menor escopo seguro possivel, adicionando testes automatizados para provar que os fluxos continuam funcionando.

## 1. Objetivo Final

Fechar todas estas falhas confirmadas:

1. Fallback inseguro de autenticacao por `X-Usuario-Id` sem cookie de sessao valido.
2. Endpoints multi-tenant acessiveis por `empresaId`, `usuarioId`, `clienteId`, `agendamentoId`, `pagamentoId`, `insightId` ou IDs equivalentes sem exigir identidade autenticada e autorizacao da empresa correta.
3. CSRF em rotas que usam cookie de sessao com `SameSite=None`.
4. WebSocket `/ws/session` aceitando origem `*`.
5. IDOR em `GET /api/insights/{id}`.
6. Webhooks de pagamento com validacao fraca ou ambigua, especialmente Cakto aceitando segredo vindo de query/body.
7. Qualquer rota de pagamento, financeiro, membros, convites, empresa, clientes, servicos, profissionais, agenda, dashboard, insights, LGPD, chamados e configuracoes que dependa de ID enviado pelo cliente sem validar empresa dona no backend.

Ao final, o sistema deve continuar funcionando corretamente:

1. Login normal do SaaS.
2. Criacao de conta.
3. Refresh/logout.
4. Admin login.
5. Admin impersonation.
6. Meu Gendaz por slug e codigo de email.
7. Pagamentos e webhooks validos.
8. Fluxos de plano, assinatura e conta inativa.
9. GendazIA/Insights.
10. Frontend React com `withCredentials`.

## 2. Preparacao Obrigatoria

1. Verifique branch, commit e status:

```powershell
git status --short --branch
git rev-parse HEAD
```

2. Leia estes arquivos antes de alterar:

```text
backend/src/main/java/com/minhaempresa/Gendaz/shared/SecurityHeadersConfig.java
backend/src/main/java/com/minhaempresa/Gendaz/shared/WebConfig.java
backend/src/main/java/com/minhaempresa/Gendaz/auth/interceptor/UsuarioSessionInterceptor.java
backend/src/main/java/com/minhaempresa/Gendaz/auth/service/AuthService.java
backend/src/main/java/com/minhaempresa/Gendaz/auth/controller/AuthController.java
backend/src/main/java/com/minhaempresa/Gendaz/auth/controller/MeuGendazAuthController.java
backend/src/main/java/com/minhaempresa/Gendaz/admin/controller/AdminController.java
backend/src/main/java/com/minhaempresa/Gendaz/admin/service/AdminService.java
backend/src/main/java/com/minhaempresa/Gendaz/config/WebSocketConfig.java
backend/src/main/java/com/minhaempresa/Gendaz/auth/websocket/SessionHandshakeInterceptor.java
backend/src/main/java/com/minhaempresa/Gendaz/pagamento/controller/PagamentoController.java
backend/src/main/java/com/minhaempresa/Gendaz/pagamento/service/PagamentoService.java
backend/src/main/java/com/minhaempresa/Gendaz/pagamento/gateway/CaktoPaymentGateway.java
backend/src/main/java/com/minhaempresa/Gendaz/pagamento/gateway/MercadoPagoPaymentGateway.java
backend/src/main/java/com/minhaempresa/Gendaz/insights/controller/InsightsController.java
backend/src/main/java/com/minhaempresa/Gendaz/insights/service/InsightsService.java
backend/src/main/java/com/minhaempresa/Gendaz/insights/service/InsightsAnalyzer.java
frontend/src/api/axiosConfig.js
frontend/src/api/clienteApi.js
frontend/src/contexts/AuthContext.jsx
frontend/src/contexts/ClienteGendazContext.jsx
```

3. Nao altere DNS, Render, Neon, secrets reais ou producao.

4. Nao use API real da Groq, pagamento real, WhatsApp real ou email real nos testes. Use mocks.

## 3. Corrigir Autenticacao Central

Problema: `UsuarioSessionInterceptor` atualmente permite passar sem sessao e ainda registra `CompanyContext` a partir de `X-Usuario-Id`. `AuthService.buscarUsuarioAutenticado(usuarioId, sessionToken)` tambem aceita `usuarioId` sozinho quando nao ha cookie.

Implementacao obrigatoria:

1. Fazer cookie `Gendaz_session` ser a fonte de verdade para usuario SaaS.
2. Fazer cookie `agendeasy_admin_session` ser a fonte de verdade para admin.
3. Remover qualquer autenticacao por `X-Usuario-Id` sem cookie valido.
4. Permitir `X-Usuario-Id` apenas como informacao auxiliar, nunca como autenticacao.
5. Se `X-Usuario-Id` vier divergente do usuario do cookie, ignorar o header ou retornar `401/403`.
6. Em rotas autenticadas, quando nao houver cookie valido, retornar `401`.
7. Em rotas onde o usuario existe mas nao pertence a empresa/recurso, retornar `404` ou `403` de forma consistente, preferencialmente `404` para IDOR.
8. Manter rotas publicas reais em allowlist explicita.

Crie um modelo central, por exemplo:

```java
public record UsuarioAutenticado(
    Long usuarioId,
    Long empresaId,
    PerfilUsuario perfil,
    boolean admin,
    boolean impersonado
) {}
```

ou use o padrao ja existente do projeto, desde que o resultado seja equivalente.

O `CompanyContext` deve ser preenchido somente a partir de sessao validada por cookie/admin token valido, nunca por header solto.

## 4. Definir Allowlist Publica Exata

Somente estas rotas devem ser publicas sem cookie de usuario SaaS:

1. `POST /api/auth/login`
2. `POST /api/auth/criar-conta`
3. `POST /api/auth/recuperar-senha`
4. `POST /api/auth/redefinir-senha`
5. `POST /api/auth/refresh` somente via cookie quando existir; sem cookie deve retornar erro controlado.
6. `POST /api/auth/logout` pode ser best-effort, mas nao pode autenticar por header.
7. `POST /api/meu-gendaz/auth/solicitar-codigo`
8. `POST /api/meu-gendaz/auth/validar-codigo`
9. Rotas publicas do Meu Gendaz que sao realmente publicas por design, como servicos, profissionais e horarios publicos, desde que validem `slug` e nao exponham dados internos.
10. Webhooks de pagamento, mas somente com assinatura valida.
11. Health checks.
12. Convite publico por token, aceitar convite e recusar convite, desde que usem token criptograficamente forte e hash.

Tudo fora disso deve exigir sessao validada.

## 5. Corrigir Autorizacao Multi-Tenant

Para todo endpoint do SaaS principal, nao confiar em `empresaId` vindo do frontend. O backend deve derivar `empresaId` da sessao autenticada.

Implemente helpers de autorizacao, por exemplo:

```java
Long empresaDaSessao = CompanyContext.getCompanyId();
if (empresaDaSessao == null) throw new SessaoExpiradaException(...);
if (!empresaDaSessao.equals(empresaIdSolicitado)) throw new ResourceNotFoundException(...);
```

Mas o ideal e substituir parametros de `empresaId` por empresa da sessao sempre que possivel.

Corrigir obrigatoriamente:

1. `EmpresaController`
   - `GET /api/empresas/{id}` deve exigir sessao e `id == empresa da sessao`, exceto admin autenticado.
   - `POST /api/empresas` nao deve ficar publico se a criacao correta e via `/api/auth/criar-conta`. Se ainda for necessario, proteger ou remover do fluxo publico.
   - `PUT /api/empresas/{id}` deve validar dono/admin e empresa da sessao.

2. `ClienteController` e `ClienteService`
   - `criar`, `listar`, `buscarPorId`, `buscarPorTelefone`, `atualizar`, `excluir`, `ativar`, `desativar`, acoes em massa devem exigir sessao.
   - `empresaId` do body/query deve bater com empresa da sessao ou ser ignorado/substituido pela empresa da sessao.
   - `buscarPorTelefone` deve filtrar por empresa da sessao.

3. `ServicoController` e `ServicoService`
   - Todos os endpoints devem exigir sessao.
   - `alterarStatus(id)` deve validar empresa do servico contra empresa da sessao.
   - Criacao/atualizacao devem usar empresa da sessao.

4. `ProfissionalController` e `ProfissionalService`
   - Mesma regra de servicos.

5. `AgendamentoController` e `AgendamentoService`
   - Listagens por empresa/data/cliente devem exigir sessao e filtrar por empresa da sessao.
   - `confirmar`, `cancelar`, `finalizar`, `iniciar`, `pausar`, `remarcar`, `delete` devem validar que o agendamento pertence a empresa da sessao.
   - `horariosDisponiveis` interno do painel deve exigir sessao. A rota publica do Meu Gendaz deve ser separada e validada por slug.

6. `FinanceiroController`
   - `GET /api/financeiro/resumo` deve exigir sessao.
   - `empresaId` deve ser derivado da sessao.

7. `PagamentoController` e `PagamentoService`
   - Rotas de CRUD de pagamentos internos devem exigir sessao e empresa da sessao.
   - `marcar-pago`, `status`, `acoes-em-massa`, `contarPendentes`, listagens e plano atual devem validar empresa.
   - `iniciarPagamentoPro` deve validar que o usuario autenticado pertence a empresa que esta comprando e que nao e `ATENDENTE`.
   - Preco/plano devem continuar definidos no servidor.

8. `UsuarioController` e `MembresiaService`
   - `listarMembros`, `listarConvites`, `resumo`, `buscarPorId` devem exigir sessao e empresa da sessao.
   - Convites publicos devem continuar funcionando apenas por token.
   - Criar, reenviar, cancelar convite, desativar, reativar, remover e transferir propriedade devem exigir owner autenticado por cookie.

9. `InsightsController` e `InsightsService`
   - Todos endpoints de insights do SaaS devem exigir sessao.
   - `empresaId` deve ser resolvido pela sessao.
   - `GET /api/insights/{id}` deve buscar por `id` e `empresaId`, nunca por `id` sozinho.
   - Historico deve filtrar pela empresa da sessao.
   - Recalculo deve ter rate limit por empresa/usuario.

10. `DashboardController`, `ConfiguracaoController`, `HorarioAtendimentoController`, `LgpdController`, `ChamadoController`
    - Verificar e corrigir qualquer uso de `X-Usuario-Id` sem cookie.
    - Garantir empresa da sessao em todas as operacoes.

## 6. Corrigir CSRF Sem Quebrar Cookies

Como o app usa cookies `HttpOnly`, `Secure`, `SameSite=None` e `withCredentials`, implemente protecao CSRF.

Escolha uma destas abordagens seguras:

1. Spring Security CSRF com `CookieCsrfTokenRepository.withHttpOnlyFalse()` para um cookie de CSRF nao-login, e Axios enviando header `X-XSRF-TOKEN`.
2. Filtro customizado de CSRF com token anti-CSRF emitido pelo backend, validado em todos metodos mutantes.
3. Validacao forte de `Origin`/`Referer` para metodos mutantes, combinada com token CSRF para rotas autenticadas sensiveis.

Requisitos:

1. Nao armazenar token de login em localStorage/sessionStorage.
2. CSRF token nao e token de login.
3. Proteger `POST`, `PUT`, `PATCH`, `DELETE`.
4. Isentar somente webhooks assinados, health, login/cadastro quando apropriado, e rotas publicas reais.
5. Garantir que login, refresh, logout, admin e Meu Gendaz continuam funcionando.
6. Garantir frontend React envia CSRF corretamente com Axios.

No frontend:

1. Manter `withCredentials: true`.
2. Configurar Axios para enviar header CSRF conforme backend.
3. Nao persistir usuario/sessao/token de login em storage.

## 7. Corrigir WebSocket

Problema: `/ws/session` usa cookie no handshake e aceita origem `*`.

Implementacao:

1. Trocar `.setAllowedOrigins("*")` por allowlist baseada em config:
   - `https://gendaz.site`
   - `https://www.gendaz.site`
   - URLs de frontend configuradas em ambiente.
   - localhost apenas em dev.
2. Validar `Origin` no handshake.
3. Manter cookie `Gendaz_session` como fonte de verdade.
4. Se sessao invalida, recusar handshake.
5. Testar que notificacao de sessao invalidada continua funcionando.

## 8. Corrigir Webhooks De Pagamento

Pagamento nao pode ser confirmado por payload forjado.

Mercado Pago:

1. Manter validacao HMAC.
2. Validar `x-signature`, `x-request-id`, `data.id` e segredo configurado.
3. Nao aceitar assinatura em body/query.
4. Validar valor, provider payment id e referencia.
5. Garantir idempotencia.

Cakto:

1. Nao aceitar `secret`, `webhook_secret` ou `webhookSecret` vindo no body ou query como prova de autenticidade.
2. Aceitar somente header de assinatura/secret definido pelo provedor e comparar com `payment.caktoWebhookSecret` usando comparacao constante.
3. Se a Cakto nao oferecer assinatura HMAC, tratar header secreto como segredo compartilhado e exigir que venha somente em header.
4. Validar `paymentReference`, `externalReference`, produto/plano, valor, status, email/empresa quando disponivel.
5. Garantir que webhook duplicado nao duplica assinatura nem bagunca status.
6. Garantir que pagamento rejeitado/cancelado/expirado nao rebaixa conta indevidamente se houver outra assinatura ativa.
7. Criar testes de webhook aprovado, duplicado, valor errado, assinatura ausente, assinatura errada e referencia inexistente.

## 9. Corrigir GendazIA/Insights

1. `InsightsAnalyzer.coletarDados()` deve receber apenas empresa autorizada.
2. `InsightsService.validarAcessoEmpresa()` deve falhar se nao houver `CompanyContext` em rota SaaS autenticada.
3. Para jobs agendados, usar contexto interno seguro separado, nunca request de usuario.
4. `GET /api/insights/{id}` deve buscar `findByIdAndEmpresaId`.
5. Historico deve ser por empresa autenticada.
6. Nao retornar prompt bruto, segredo, resposta bruta sensivel ou dados de outra empresa.
7. Limitar `periodo`, tamanho da pergunta e tamanho do historico.
8. Para `/recalcular`, aplicar rate limit por empresa/usuario para controlar custo.
9. Validar resposta da Groq antes de transformar em DTO.
10. Usar fallback local quando Groq falhar.

## 10. Corrigir Frontend Sem Quebrar Fluxos

1. Manter `withCredentials: true` em Axios.
2. Remover dependencia de `X-Usuario-Id` como autenticacao.
3. Se o header continuar sendo enviado temporariamente, backend deve ignorar como autenticacao.
4. Preferir endpoints que nao exigem `empresaId` no frontend para rotas autenticadas.
5. Atualizar chamadas para:
   - `/clientes/empresa/{empresaId}` -> idealmente `/clientes`
   - `/servicos/empresa/{empresaId}` -> idealmente `/servicos`
   - `/financeiro/resumo?empresaId=...` -> idealmente `/financeiro/resumo`
   - `/pagamentos/empresa/{empresaId}` -> idealmente `/pagamentos`
   - `/insights?...empresaId=...` -> idealmente sem `empresaId`
6. Se nao der para mudar todas as URLs agora, backend deve validar que o `empresaId` enviado bate com a sessao.
7. Nao colocar login em `localStorage` ou `sessionStorage`.
8. Garantir que Meu Gendaz continua usando `X-Meu-Gendaz-Slug` e cookie `meu_gendaz_session_<slug>`.

## 11. Testes Obrigatorios

Crie ou atualize testes automatizados para provar:

1. Requisicao sem cookie para endpoint SaaS retorna `401`.
2. Requisicao com cookie da Empresa A tentando acessar `empresaId` da Empresa B retorna `403` ou `404`.
3. `X-Usuario-Id` sem cookie nao autentica.
4. `X-Usuario-Id` divergente do cookie nao troca identidade.
5. Usuario autenticado consegue listar seus proprios clientes/servicos/agendamentos/pagamentos.
6. Login cria cookie e nao retorna `sessionToken` no JSON.
7. Refresh funciona por cookie.
8. Logout limpa cookie.
9. Admin login continua funcionando.
10. Admin endpoints exigem admin cookie/token valido.
11. Admin impersonation continua funcionando com auditoria.
12. Meu Gendaz solicitar/validar codigo continua funcionando e nao retorna session token no JSON.
13. Meu Gendaz refresh/perfil funciona com slug + cookie correto.
14. CSRF bloqueia metodo mutante sem token/origin valido.
15. CSRF permite metodo mutante legitimo do frontend.
16. WebSocket aceita origem permitida e recusa origem externa.
17. Webhook Cakto sem assinatura/secret de header valido falha.
18. Webhook Cakto com segredo no body/query falha.
19. Webhook Cakto valido aprova pagamento correto.
20. Webhook com valor errado falha.
21. `GET /api/insights/{id}` nao permite insight de outra empresa.
22. `/api/financeiro/resumo` nao permite empresa de outro tenant.
23. Membros/convites/resumo de usuarios nao vazam entre empresas.

Use `MockMvc` ou o padrao ja existente nos testes Java. Use mocks para gateway, email e Groq.

## 12. Validacao Manual Minima

Depois dos testes:

1. Rodar backend tests:

```powershell
cd backend
mvn test
```

2. Rodar frontend build:

```powershell
cd frontend
npm run build
```

3. Fazer teste manual local/staging com dados falsos:

1. Conta A login.
2. Conta B login.
3. Conta A nao acessa dados da Conta B.
4. Login/logout/refresh ok.
5. Pagamento pendente e retorno ok.
6. Webhook mock aprovado ok.
7. Admin login ok.
8. Meu Gendaz OTP ok.

## 13. Checklist De Nao Regressao

Antes de finalizar, confirme:

1. Nenhum login/session token foi para `localStorage`.
2. Nenhum login/session token foi para `sessionStorage`.
3. Cookies de sessao continuam `HttpOnly`.
4. Cookies em producao continuam `Secure`.
5. CORS continua permitindo somente frontend esperado.
6. CSRF nao bloqueia o frontend legitimo.
7. Webhooks assinados continuam funcionando.
8. Rotas publicas do Meu Gendaz continuam funcionando.
9. Rotas internas do SaaS exigem sessao.
10. Nenhum endpoint aceita empresa de outro tenant.
11. Nenhum endpoint admin funciona sem admin autenticado.
12. Insights nao vaza prompt/dados de outra empresa.
13. Build frontend passa.
14. Testes backend passam.

## 14. Formato Da Entrega

Ao terminar, entregue:

1. Resumo das correcoes feitas.
2. Lista de arquivos alterados.
3. Lista de endpoints protegidos.
4. Lista de testes criados/alterados.
5. Comandos executados e resultado.
6. Riscos residuais.
7. Itens que dependem de verificacao externa em Render/Neon/GitHub.

Nao diga que esta seguro sem evidencias. Use:

1. `CORRIGIDO POR CODIGO`
2. `COBERTO POR TESTE`
3. `PENDENTE DE CONFIGURACAO EXTERNA`
4. `PENDENTE DE TESTE DINAMICO`

## 15. Prioridade De Implementacao

Execute nesta ordem:

1. Travar autenticacao central e remover fallback por `X-Usuario-Id`.
2. Proteger rotas SaaS por allowlist publica estrita.
3. Corrigir autorizacao multi-tenant nos services/controllers.
4. Corrigir IDOR de Insights.
5. Corrigir CSRF.
6. Corrigir WebSocket origin.
7. Corrigir webhooks de pagamento.
8. Ajustar frontend para CSRF e rotas protegidas.
9. Criar testes de regressao.
10. Rodar testes/build.
11. Gerar relatorio final.

## 16. Cuidado Especial Com Pagamento

Nao quebrar:

1. Criacao de pagamento de plano Pro.
2. Conta pendente de pagamento.
3. Liberacao da conta apos pagamento aprovado.
4. Consulta de plano atual.
5. Verificacao de pagamento.
6. Webhook Mercado Pago.
7. Webhook Cakto.
8. Idempotencia.
9. Fila de ate dois planos ativos.
10. Admin aprovacao/reversao manual.

Toda mudanca em pagamento precisa de teste.

## 17. Cuidado Especial Com Login

Nao quebrar:

1. Login SaaS por email/senha.
2. Criar conta.
3. Refresh.
4. Logout manual.
5. Sessao substituida por login mais recente.
6. Tela de sessao encerrada para invalidacao remota.
7. Admin login.
8. Meu Gendaz OTP.
9. Safari/iPhone Meu Gendaz.

Nunca mover sessao para storage do navegador.

## 18. Criterio De Conclusao

So considere concluido quando:

1. Nao existir endpoint SaaS mutante sem sessao.
2. Nao existir endpoint SaaS de leitura sensivel sem sessao.
3. `X-Usuario-Id` sozinho nao autenticar nada.
4. Empresa da sessao for a fonte de autorizacao.
5. CSRF estiver ativo e testado.
6. WebSocket tiver allowlist de origem.
7. Webhook nao aceitar segredo vindo do body/query.
8. Insights por ID validar empresa.
9. Backend tests passarem.
10. Frontend build passar.
11. Login, pagamento, admin e Meu Gendaz continuarem funcionando.


