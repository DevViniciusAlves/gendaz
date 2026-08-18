# gendaz — Registro das Operações de Tratamento de Dados (ROPA)

Documento interno de mapeamento do tratamento de dados pessoais na plataforma gendaz,
conforme a LGPD (Lei nº 13.709/2018). Não substitui parecer jurídico. Não designa
DPO/encarregado nem nome civil, CNPJ ou endereço, e não inventa prazos ou bases legais
que não correspondam à operação real.

E-mail oficial de privacidade: `contato@gendaz.site`

## Observação sobre o fluxo de dados

- **Não há coleta de CPF, CNPJ ou documento de identificação** nos fluxos atuais
  (cadastro, login, clientes, Meu Gendaz, pagamentos). Este é um requisito do produto e
  não uma lacuna de tratamento.
- Este ROPA descreve o tratamento **real** verificado no código-fonte deste repositório.

## 1. Operações de tratamento (visão geral)

| # | Operação | Finalidade | Categorias de dados | Controlador (conforme operação) | Operador/processador | Base legal possível | Armazenamento |
|---|----------|-----------|---------------------|--------------------------------|----------------------|---------------------|---------------|
| 1 | Cadastro e autenticação de empresa/usuários | Criar e manter conta, login e controle de acesso | Nome, e-mail, telefone, senha (hash), perfil, status, IP, aceite de Termos/Política | gendaz (conta da plataforma) | gendaz; hospedagem (Render/Neon) | Execução de contrato; legítimo interesse (segurança) | `usuarios`, `empresas` |
| 2 | Gestão de clientes da empresa | Operação do negócio da empresa cliente | Nome, telefone, e-mail, observações, status | Empresa cliente | gendaz (operadora) | Execução de contrato; operação do tenant | `clientes`, `clientes_emails_bloqueados` |
| 3 | Agendamentos | Gestão de agenda e histórico de atendimentos | Cliente, serviço, profissional, data/hora, status | Empresa cliente | gendaz (operadora) | Execução de contrato; operação do tenant | `agendamentos` |
| 4 | Conversas/mensagens | Atendimento e comunicação com clientes | Conteúdo das mensagens, participantes | Empresa cliente | gendaz (operadora) | Execução de contrato; operação do tenant | `conversas`, `mensagens` |
| 5 | Financeiro e pagamentos de plano | Cobrança e histórico de assinatura | Nome, e-mail (pagador), referências e status de pagamento | gendaz | Stripe (pagamentos), gendaz | Execução de contrato | `pagamentos_planos`, `pagamentos_planos_cobrancas`, `strip_e_webhook_events` |
| 6 | Notas fiscais | Obrigação fiscal | Dados de faturamento (módulo interno) | Empresa cliente | gendaz (operadora) | Obrigação legal/fiscal | `notas_fiscais` |
| 7 | Chamados/suporte | Atendimento a solicitações e dúvidas | Conteúdo da solicitação, dados da conta | gendaz | gendaz | Legítimo interesse; contrato | `chamados` |
| 8 | Auditoria de segurança/admin | Segurança, auditoria e resolução de incidentes | Ações do admin, IP, user-agent, descrição | gendaz | gendaz | Legítimo interesse (segurança) | `audit_logs` |
| 9 | Meu Gendaz (acesso de cliente) | Portal do cliente para consulta do próprio cadastro | Nome, e-mail, sessão | Empresa cliente | gendaz (operadora) | Execução de contrato | `meu_gendaz_acessos` |
| 10 | Meu Gendaz (OTP) | Autenticação por código de verificação | E-mail, hash do código (HMAC-SHA256), TTL, tentativas | Empresa cliente | gendaz (operadora); Resend (entrega de e-mail) | Execução de contrato; legítimo interesse (segurança) | `meu_gendaz_otp_challenges` |
| 11 | Insights (IA) | Dashboard gerencial a partir de dados agregados da empresa | Payload agregado da empresa (não inclui documento/CPF/CNPJ) | Empresa cliente / gendaz (conforme caso) | Groq (IA) | Legítimo interesse; execução de contrato | `insights` (com expiração +24h) |
| 12 | Prevenção de fraude/abuso e rate-limit | Proteção contra acesso não autorizado e abuso | IP, tentativas falhas, contadores de limite | gendaz | gendaz | Legítimo interesse (segurança) | `ip_tracking`, `security_rate_limit_entries`, campos de segurança em `usuarios` |

## 2. Dados técnicos tratados

- **IP e user-agent**: coletados em login, tentativas falhas, rotas de segurança e auditoria
  admin. Retenção técnica: `ip_tracking` 30 dias (rotina programada); rate-limit removido ao
  expirar; `audit_logs` conforme retenção configurável.
- **Logs de aplicação**: eventos de integração contam **bytes e URLs sanitizadas** (sem query
  string e sem payload) via `OutboundTrafficAuditService`; não são persistidas mensagens completas.

## 3. Terceiros e transferência internacional

| Terceiro | Função | Dados enviados | Origem da chamada | Região |
|----------|--------|----------------|-------------------|--------|
| Stripe | Pagamentos/assinaturas | Nome, e-mail, metadado `gendazEmpresaId`, referências | `StripePaymentGateway` | Internacional |
| Resend | E-mail transacional | E-mail do destinatário, nome e conteúdo da mensagem | `ResendEmailService` | Internacional |
| Groq | IA para insights | Payload agregado da empresa | `GroqClient` | Internacional |
| reCAPTCHA (Google) | Proteção de formulários | **Sem integração no frontend** — configuração inativa | — | Internacional |
| Neon | PostgreSQL gerenciado | Dados persistidos | Conexão JDBC | Internacional |
| Render | Hospedagem | Tráfego da aplicação | Infraestrutura | Internacional |

Observação: SMS não é utilizado no backend (nenhum provider identificado).

## 4. Comportamento no encerramento de conta

- Encerramento lógico da conta (`encerrarConta`): revoga acessos/sessões e cancela assinatura
  Stripe quando aplicável. A exclusão física segue a matriz de retenção (`docs/lgpd/matriz-retencao.md`)
  e está sujeita a decisão formal de prazos.

## 5. Pendências (BLOQUEIO DE DECISÃO / VERIFICAÇÃO EXTERNA)

- Prazo definitivo e base legal formal de retenção por categoria (decisão jurídica).
- Política de backup e snapshots dos provedores (Render/Neon).
- Mecanismo contratual de transferência internacional (cláusula/termo — a validar).
- Identificação formal do controlador (razão social/DPO) — deliberadamente não preenchida.
- Ativação ou desativação formal do reCAPTCHA (atualmente apenas configurado no backend).