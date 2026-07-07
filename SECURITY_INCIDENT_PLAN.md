# Plano de Resposta a Incidentes - AgendaFacil

## Objetivo
Definir a resposta imediata em caso de vazamento, invasao, perda de dados ou acesso indevido.

## 1. Isolar
- Revogar sessões ativas da conta afetada.
- Bloquear o acesso do usuario comprometido.
- Suspender integrações e webhooks relacionados.
- Trocar credenciais expostas, se houver.

## 2. Investigar
- Identificar origem, horario e vetor do incidente.
- Revisar logs de auditoria e acessos recentes.
- Verificar quais dados foram acessados, alterados ou exportados.
- Conferir se o problema afeta uma empresa ou toda a plataforma.

## 3. Conter
- Corrigir a falha explorada.
- Invalidar tokens, chaves e sessoes comprometidas.
- Aplicar restricoes temporarias em endpoints sensiveis.
- Monitorar novas tentativas de acesso indevido.

## 4. Notificar
- Avisar o cliente afetado com clareza.
- Explicar o que aconteceu, quais dados foram impactados e o que foi feito.
- Quando aplicavel, notificar dentro do prazo legal da LGPD.

## 5. Recuperar
- Restaurar dados validos de backup, se necessario.
- Validar a integridade do banco e das APIs.
- Reativar acessos apenas apos a correcao.

## 6. Corrigir
- Registrar a causa raiz.
- Corrigir o codigo, configuracao ou processo que permitiu o incidente.
- Criar testes para evitar regressao.
- Atualizar o plano com o que foi aprendido.

## Contatos
- Suporte: suporte@agendafacil.com.br
- Privacidade: privacidade@agendafacil.com.br
- Seguranca: seguranca@agendafacil.com.br
