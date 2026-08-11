# Checklist de validaÃ§Ã£o no F12

## 1. Login com cookie
1. Abrir o navegador.
2. Fazer login normal.
3. No F12, ir em `Application > Cookies` e confirmar `Gendaz_session` com `HttpOnly`, `Secure` e `SameSite`.
4. Em `Application > Local Storage` e `Session Storage`, confirmar que nÃ£o existe token de login.

## 2. Logout
1. Clicar em sair.
2. No F12, confirmar que o cookie de sessÃ£o foi apagado.
3. Recarregar a pÃ¡gina e ver se volta para login.

## 3. Refresh da pÃ¡gina
1. Estar logado.
2. Dar `F5`.
3. No F12, aba `Network`, confirmar que as requests continuam indo com `Cookie` e nÃ£o com token em storage.

## 4. Acesso sem sessÃ£o
1. Abrir aba anÃ´nima ou limpar cookies.
2. Tentar entrar direto em rota protegida.
3. No F12, `Network`, confirmar resposta `401`.

## 5. Header falso `X-Usuario-Id`
1. No F12, aba `Network`, abrir uma request e tentar reenviar.
2. Adicionar `X-Usuario-Id` manualmente.
3. Confirmar que sozinho isso nÃ£o libera nada sem cookie vÃ¡lido.

## 6. SeparaÃ§Ã£o por empresa
1. Logar em uma empresa.
2. Tentar abrir dados de outra empresa por URL ou request.
3. No F12, `Network`, confirmar `403` ou bloqueio de tenant.

## 7. Insights
1. Tentar acessar `/api/insights/{id}` de outro tenant.
2. No F12, confirmar que nÃ£o retorna dado de outra empresa.

## 8. Pagamentos
1. Criar cobranÃ§a.
2. Ver no `Network` a request de criaÃ§Ã£o e a resposta.
3. Aprovar pagamento e confirmar que a conta libera normalmente.
4. Tentar consultar pagamento de outra empresa e confirmar bloqueio.

## 9. Webhook
1. Enviar webhook vÃ¡lido.
2. Ver no backend/log e no `Network` se o endpoint responde certo.
3. Tentar webhook com segredo no body/query e confirmar rejeiÃ§Ã£o.

## 10. WebSocket
1. No F12, aba `Network > WS`.
2. Conferir conexÃ£o em `/ws/session`.
3. Tentar abrir de origem invÃ¡lida e confirmar bloqueio.

