# gendaz — Matriz de Retenção de Dados

Estado atual verificado no código-fonte. **Não define prazos legais/operacionais finais**:
cada linha marca a regra técnica existente e a pendência de decisão
(**BLOQUEIO DE DECISÃO**). Prazos definitivos exigem decisão jurídica/de negócio.

Legenda:
- **Técnica (verificada)** — regra que existe no código.
- **Decisão** — prazo final formal a ser aprovado (não inventado aqui).
- **Após encerramento de conta** — estado atual do dado após o encerramento lógico.

| Categoria | Tabela(s) | Regra técnica (verificada) | Após encerramento | Decisão pendente |
|-----------|-----------|----------------------------|-------------------|------------------|
| Usuários | `usuarios` | Mantidos; status `INATIVO`; sessões revogadas | Mantidos | Prazo de retenção/exclusão |
| Empresas | `empresas` | Mantidas; sem coluna `documento` (removida por migration V61) | Mantidas | Prazo de retenção/exclusão |
| Clientes | `clientes` | Mantidos enquanto a conta estiver ativa | Mantidos | Prazo de retenção após encerramento |
| Agendamentos | `agendamentos` | Mantidos | Mantidos | Prazo de retenção após encerramento |
| Conversas/mensagens | `conversas`, `mensagens` | Mantidas | Mantidas | Prazo de retenção após encerramento |
| Financeiro da empresa | tabelas de pagamento/financeiro | Mantido | Mantido | Prazo após encerramento |
| Pagamentos/assinaturas | `pagamentos_planos`, `pagamentos_planos_cobrancas`, `stripe_webhook_events` | Mantidos; cobranças vencidas expiram por TTL (checkout) | Mantidos (cobrança/contrato) | Prazo fiscal/contratual |
| Notas fiscais | `notas_fiscais` | Mantidas | Mantidas | Prazo de conservação fiscal |
| Chamados/suporte | `chamados` | Mantidos | Mantidos | Prazo de retenção após encerramento |
| Auditoria admin | `audit_logs` | Retenção configurável `app.audit-logs.retention-days` (padrão técnico atual: 365 dias); limpeza 1x/dia | Mantidos até a rotina remover | Confirmação do prazo padrão |
| Meu Gendaz (acessos) | `meu_gendaz_acessos` | Mantidos; sessões revogadas no encerramento | Mantidos | Prazo de retenção após encerramento |
| Meu Gendaz (OTP) | `meu_gendaz_otp_challenges` | OTP com TTL de 10 min; máx. 5 tentativas; desafio removido quando expirado há mais de 1 dia (cleanup 1h) | — | — |
| Sessions/onboarding Meu Gendaz | `meu_gendaz_otp_challenges` (onboarding hash/expira) | Onboarding TTL 20 min | — | — |
| Rate-limit/abuso | `security_rate_limit_entries` | Remoção ao expirar (cleanup 1h) | — | — |
| Rastreio de IP | `ip_tracking` | Remoção com mais de 30 dias (cleanup 6h) | — | — |
| Insights (IA) | `insights` | `data_expiracao` +24h; cleanup físico de expirados (1h) | — | — |
| Sessões admin/impersonação | `admin_sessions`, `admin_impersonation_sessions` | Impersonação TTL 30 min (configurável) | Revogadas/encerradas | Verificar prazo de `admin_sessions` |
| Backups/snapshots | Infra do provedor (Neon/Render) | Sem comprovação neste repositório | Cópias podem existir | PENDENTE: política de backup |

## Regras técnicas que já garantem minimização (verificadas)

- Todas as rotinas de limpeza são programadas (`@Scheduled`) e/ou configuráveis.
- OTP armazenado como hash HMAC-SHA256; códigos e tokens não são gravados em claro.
- Logs de integrações externas (`OutboundTrafficAuditService`) registram apenas bytes e URLs
  sanitizadas (sem payload/query); nenhum corpo de requisição é persistido.
- `audit_logs` desvincula usuário/admin removido (FK nullada) sem apagar o registro histórico.
- `usuarios` mantém hash de senha; nunca exportado nas rotas LGPD.

## Ações recomendadas (próximos passos fora do escopo de código)

1. Aprovar prazos formais por categoria e implementar exclusão física correspondente.
2. Definir política de backup e prazos de snapshot com os provedores.
3. Confirmar/validar a retenção padrão de `audit_logs` (atualmente um valor técnico).
4. Definir base legal e mecanismo contratual de transferência internacional.