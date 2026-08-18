# gendaz — Documentação LGPD (interna)

Documento interno de apoio à conformidade com a Lei Geral de Proteção de Dados Pessoais
(LGPD – Lei nº 13.709/2018). Este documento não substitui parecer jurídico e não inventa
prazos, responsáveis formais (DPO/encarregado), CNPJ ou razão social.

E-mail oficial de privacidade: `contato@gendaz.site`
Marca pública: **gendaz**

---

## 1. Inventário de retenção (Estado atual)

Legenda de regra atual:
- **Mantém enquanto conta ativa** — dado usado na operação e mantido enquanto a conta existir.
- **Mantém após encerramento** — dado conservado após o encerramento lógico da conta.
- **Limpeza programada** — rotina existente que remove registros com base em expiração.

| Categoria | Armazenamento | Finalidade | Obrigação legal/contratual identificada | Estado após encerramento | Regra atual | Lacuna |
|---|---|---|---|---|---|---|
| Usuários | `usuarios` (PostgreSQL) | Autenticação, perfis, aceite de termos | Contrato; segurança | Status `INATIVO`, sessões revogadas, registros mantidos | Mantém após encerramento | Prazo de retenção não definido formalmente |
| Clientes | `clientes` | Gestão de clientes da empresa | Operação do tenant | Mantidos | Mantém enquanto conta ativa | Prazo de retenção não definido formalmente |
| Agendamentos | `agendamentos` | Gestão de agenda e histórico | Operação do tenant | Mantidos | Mantém enquanto conta ativa | Prazo de retenção não definido formalmente |
| Conversas/mensagens | `conversas`, `mensagens` | Atendimento e comunicação | Operação do tenant | Mantidos | Mantém enquanto conta ativa | Prazo de retenção não definido formalmente |
| Financeiro | tabelas de pagamento/financeiro | Controle financeiro da empresa | Operação do tenant | Mantidos | Mantém enquanto conta ativa | Prazo de retenção não definido formalmente |
| Pagamentos de plano/assinatura | `pagamentos_planos`, `pagamentos_planos_cobrancas` | Cobrança e histórico de assinatura | Contrato; cobrança | Mantidos | Mantém após encerramento | Prazo fiscal/contratual não definido formalmente |
| Notas fiscais/registros fiscais | `notas_fiscais` | Obrigação fiscal | Obrigação legal/fiscal | Mantidos | Mantém após encerramento | Prazo de conservação fiscal não definido formalmente |
| Chamados/suporte | `chamados` | Suporte e registro de solicitações | Contrato; suporte | Mantidos | Mantém após encerramento | Prazo de retenção não definido formalmente |
| Auditoria | `audit_logs` | Segurança, auditoria e resolução de incidentes | Segurança | Mantidos | Mantém após encerramento | Prazo de retenção não definido formalmente |
| Meu Gendaz (acessos) | `meu_gendaz_acessos` | Portal do cliente | Operação do tenant | Sessões revogadas | Mantém após encerramento | Prazo de retenção não definido formalmente |
| Meu Gendaz (OTP) | `meu_gendaz_otp_challenges` | Autenticação por código | Segurança/autenticação | — | Limpeza programada de desafios expirados (default: expirados há mais de 1 dia) | TTL exato do OTP precisa ser validado |
| Backups | Infraestrutura do provedor (Render/Neon) | Recuperação de desastres | Operacional | Cópias podem existir em backups | PENDENTE DE DEFINIÇÃO | Comportamento real de backup/retenção de snapshots não comprovado neste repositório |

> **PENDENTE DE DEFINIÇÃO** (exige decisão jurídica/de negócio): prazos definitivos de retenção
> e exclusão física de cada categoria; política de backup; tratamento de dados legados.

---

## 2. Mapa de terceiros e transferência internacional

Somente fornecedores confirmados no código-fonte:

| Terceiro | Função | Dados enviados | Origem da chamada | Persistência local | Região/localização | Comportamento no encerramento |
|---|---|---|---|---|---|---|
| **Stripe** | Processamento de pagamentos de planos e assinaturas | Nome/e-mail/telefone do cliente pagador, identificadores de plano, referências de pagamento | `StripePaymentGateway` (backend) | `pagamentos_planos`, `stripe_customer_id` em `empresas` | Fora do Brasil (internacional), conforme infraestrutura da Stripe | Assinatura cancelada no encerramento (`cancelarSubscription`); falha registrada com status `FALHA_AO_CANCELAR` |
| **Render** | Hospedagem do backend e da base de dados | Todo o tráfego da aplicação | Infraestrutura | — | Fora do Brasil, conforme infraestrutura da Render | Fora do escopo da aplicação; sujeito à política do provedor |
| **Neon** | PostgreSQL (banco de dados gerenciado) | Dados persistidos da aplicação | Conexão JDBC | — | Fora do Brasil, conforme infraestrutura da Neon | Dados persistem enquanto existirem; exclusão depende da política do provedor |
| **Resend** | Envio de e-mails transacionais | E-mail do destinatário e conteúdo da mensagem | `ResendEmailService` (backend) | Não persiste localmente | Internacional, conforme infraestrutura da Resend | N/A (mensagens já enviadas) |
| **reCAPTCHA (Google)** | Proteção de formulários | Token de verificação e metadados do navegador | `RecaptchaService` (backend) | Não persiste localmente | Internacional | N/A |
| **Groq** | Geração de insights (IA) | Dados agregados da empresa para análise | `GroqClient` (backend) | `insights` (resultado) | Internacional | N/A |

Observações:
- WhatsApp service (`WHATSAPP_SERVICE_URL`) existe apenas como configuração de exemplo no `.env.example`; **não há uso no código backend**.
- CAKTO aparece apenas em migrations e `.env.example` como gateway legado; o provedor ativo é definido por `PAYMENT_PROVIDER` (default `STRIPE`).
- A Política de Privacidade informa a possibilidade de processamento internacional **sem afirmar mecanismo contratual não comprovado**.

---

## 3. Papéis controlador x operador

- **Dados que a empresa cliente decide coletar de seus próprios clientes** (clientes, agendamentos, conversas):
  a empresa pode atuar como **controladora** e a gendaz como **operadora**, processando conforme instruções da empresa.
- **Dados da conta gendaz, segurança, fraude, suporte, cobrança e obrigações próprias da plataforma:**
  a gendaz pode possuir **responsabilidades próprias** como controladora ou operadora.
- Os papéis **dependem da operação real** e não são determinados de forma absoluta por este documento.

---

## 4. Procedimento mínimo de incidente de segurança/privacidade

Fluxo interno a ser seguido diante de suspeita ou confirmação de incidente:

1. **Detectar** — registrar a suspeita (quando, como, onde) e os primeiros indícios.
2. **Conter** — limitar o alcance: revogar sessões, bloquear acessos, isolar dados afetados quando possível.
3. **Preservar evidências** — conservar logs, audit trails e registros relevantes sem alterá-los.
4. **Identificar dados e titulares afetados** — mapear categorias de dados e possíveis titulares envolvidos.
5. **Avaliar risco** — gravidade, probabilidade de dano e impacto para os titulares.
6. **Registrar decisão** — documentar as ações tomadas e a justificativa.
7. **Escalar** — comunicar à equipe técnica e à direção, e ao canal de privacidade (`contato@gendaz.site`).
8. **Avaliar comunicação** — avaliar a necessidade de comunicação à ANPD e aos titulares conforme a regra vigente
   e a avaliação de risco. Não inventar prazos: a decisão deve considerar o contexto real do incidente.

> Este documento não designa DPO/encarregado nem nome civil, CNPJ ou endereço.

---

## 5. Direitos do titular e canal de privacidade

- Canal oficial de privacidade: `contato@gendaz.site`.
- O suporte da plataforma é canal complementar.
- A Política de Privacidade descreve como solicitar: confirmação, acesso, correção, atualização,
  anonimização, bloqueio, eliminação, portabilidade e revogação de consentimento.
- Solicitações que envolvam dados controlados por uma empresa cliente podem exigir a atuação da
  empresa controladora com o suporte da gendaz.
- **Não** há endpoint público de dados pessoais sem autenticação forte; as rotas `/api/lgpd/**`
  exigem autenticação de sessão e perfil `DONO`.

---

## 6. Resumo técnico das rotas LGPD

| Rota | Método | Perfil exigido | Comportamento |
|---|---|---|---|
| `/api/lgpd/exportar` | GET | `DONO` | Exporta dados do tenant (ver conteúdo abaixo) |
| `/api/lgpd/excluir-conta` | DELETE | `DONO` | Encerramento lógico da conta + cancelamento Stripe |

Controles aplicados:
- `ATENDENTE` e "Meu Gendaz": **403** (negado no backend, independente da UI).
- O tenant é derivado da sessão autenticada; **nenhum `empresaId` arbitrário é aceito**.
- Auditoria e mensagens da exportação são consultadas **tenant-scoped** no banco.

### Conteúdo da exportação (`GET /api/lgpd/exportar`)
- Empresa (dados cadastrais do tenant).
- Usuários do tenant (identificação, contato, perfil, status, aceite de Termos/Política — **sem senha/hash**).
- Clientes, serviços, profissionais, agendamentos, conversas e mensagens do tenant.
- Financeiro, pagamentos, pagamentos de plano/assinatura, notas fiscais, entregas e notificações do tenant.
- Chamados/suporte do tenant e auditoria estritamente do tenant (limite de 200 registros).

### Explicitamente NÃO exportado
- Senha/hash, cookies, tokens, sessão ativa, OTP, secrets, chaves de API, webhook secrets.
- Dados completos de cartão.
- Dados de outro tenant.
- Logs globais sem relação com o tenant.
