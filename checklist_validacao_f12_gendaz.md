# Checklist de validação no F12

## 1. Login com cookie
1. Abrir o navegador.
2. Fazer login normal.
3. No F12, ir em `Application > Cookies` e confirmar `agendapro_session` com `HttpOnly`, `Secure` e `SameSite`.
4. Em `Application > Local Storage` e `Session Storage`, confirmar que não existe token de login.

## 2. Logout
1. Clicar em sair.
2. No F12, confirmar que o cookie de sessão foi apagado.
3. Recarregar a página e ver se volta para login.

## 3. Refresh da página
1. Estar logado.
2. Dar `F5`.
3. No F12, aba `Network`, confirmar que as requests continuam indo com `Cookie` e não com token em storage.

## 4. Acesso sem sessão
1. Abrir aba anônima ou limpar cookies.
2. Tentar entrar direto em rota protegida.
3. No F12, `Network`, confirmar resposta `401`.

## 5. Header falso `X-Usuario-Id`
1. No F12, aba `Network`, abrir uma request e tentar reenviar.
2. Adicionar `X-Usuario-Id` manualmente.
3. Confirmar que sozinho isso não libera nada sem cookie válido.

## 6. Separação por empresa
1. Logar em uma empresa.
2. Tentar abrir dados de outra empresa por URL ou request.
3. No F12, `Network`, confirmar `403` ou bloqueio de tenant.

## 7. Insights
1. Tentar acessar `/api/insights/{id}` de outro tenant.
2. No F12, confirmar que não retorna dado de outra empresa.

## 8. Pagamentos
1. Criar cobrança.
2. Ver no `Network` a request de criação e a resposta.
3. Aprovar pagamento e confirmar que a conta libera normalmente.
4. Tentar consultar pagamento de outra empresa e confirmar bloqueio.

## 9. Webhook
1. Enviar webhook válido.
2. Ver no backend/log e no `Network` se o endpoint responde certo.
3. Tentar webhook com segredo no body/query e confirmar rejeição.

## 10. WebSocket
1. No F12, aba `Network > WS`.
2. Conferir conexão em `/ws/session`.
3. Tentar abrir de origem inválida e confirmar bloqueio.
