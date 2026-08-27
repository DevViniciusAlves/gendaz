# gendaz — Documentação LGPD (interna)

Documento interno de apoio à conformidade com a Lei Geral de Proteção de Dados Pessoais
(LGPD – Lei nº 13.709/2018). Este documento não substitui parecer jurídico. O controlador está identificado
publicamente (VINICIUS HENRIQUE FERREIRA ALVES, CPF 034.456.831-82). O encarregado
de proteção de dados (DPO) está designado pelo canal contato@gendaz.site (agente de
pequeno porte — Resolução ANPD nº 2/2022). Permanece pendente a definição de prazos
de retenção. A exclusão física definitiva (LGPD Art. 16) está implementada no endpoint
`DELETE /api/lgpd/excluir-dados` (purge por empresa_id + auditoria global em `admin_audit`).

E-mail oficial de privacidade: `contato@gendaz.site`
Marca pública: **gendaz**
Controlador: **VINICIUS HENRIQUE FERREIRA ALVES**, pessoa física, CPF 034.456.831-82 (identificação pública na Política de Privacidade §16 e nos Termos de Uso §2).

---

## 1. Inventário de retenção (Estado atual)

Legenda de regra atual:
- **Mantém enquanto conta ativa** — dado usado na operação e mantido enquanto a conta existir.
- **Mantém após encerramento** — dado conservado após o encerramento lógico da conta.
- **Limpeza programada** — rotina existente que remove registros com base em expiração.

| Categoria | Armazenamento | Finalidade | Obrigação legal/contratual identificada | Estado após encerramento | Regra atual | Lacuna |
|---|---|---|---|---|---|---|
| Usuários | `usuarios` (PostgreSQL) | Autenticação, perfis, aceite de termos | Contrato; segurança | Status `INATIVO`, sessões revogadas, registros mantidos | Mantém enquanto conta ativa → eliminado na exclusão definitiva (Art. 16) | — |
| Clientes | `clientes` | Gestão de clientes da empresa | Operação do tenant | Mantidos | Mantém enquanto conta ativa → eliminado na exclusão definitiva (Art. 16) | — |
| Agendamentos | `agendamentos` | Gestão de agenda e histórico | Operação do tenant | Mantidos | Mantém enquanto conta ativa → eliminado na exclusão definitiva (Art. 16) | — |
| Conversas/mensagens | `conversas`, `mensagens` | Atendimento e comunicação | Operação do tenant | Mantidos | Mantém enquanto conta ativa → eliminado na exclusão definitiva (Art. 16) | — |
| Financeiro | tabelas de pagamento/financeiro | Controle financeiro da empresa | Operação do tenant | Mantidos | Mantém enquanto conta ativa → eliminado na exclusão definitiva (Art. 16) | — |
| Pagamentos de plano/assinatura | `pagamentos_planos`, `pagamentos_planos_cobrancas` | Cobrança e histórico de assinatura | Contrato; cobrança | Mantidos | Mantém enquanto conta ativa → eliminado na exclusão definitiva (Art. 16) | — |
| Notas fiscais/registros fiscais | `notas_fiscais` | Obrigação fiscal | Obrigação legal/fiscal | Mantidos | Mantém por 5 anos após emissão (obrigação fiscal), sobrevive ao encerramento | — |
| Chamados/suporte | `chamados` | Suporte e registro de solicitações | Contrato; suporte | Mantidos | Mantém enquanto conta ativa → eliminado na exclusão definitiva (Art. 16) | — |
| Auditoria | `audit_logs` | Segurança, auditoria e resolução de incidentes | Segurança | Mantidos | Limpeza programada; retenção de 365 dias (`app.audit-logs.retention-days`), mantida após encerramento para segurança | — |
| Meu Gendaz (acessos) | `meu_gendaz_acessos` | Portal do cliente | Operação do tenant | Sessões revogadas | Mantém enquanto conta ativa → eliminado na exclusão definitiva (Art. 16) | — |
| Meu Gendaz (OTP) | `meu_gendaz_otp_challenges` | Autenticação por código | Segurança/autenticação | — | Limpeza programada de desafios expirados (default: expirados há mais de 1 dia). TTL do código: 10 min; máx. 5 tentativas; cooldown e bloqueio configuráveis | Definido em código (`MeuGendazSecurityProperties`) |
| Insights (IA) | `insights` | Dashboard/insights gerados (IA) com `data_expiracao` +24h | Operação do tenant | N/A | Limpeza programada de insights expirados por `data_expiracao` (`InsightsCleanupScheduler`, padrão 1h) | Snapshot de dashboard não mantém histórico indefinido |
| Backups | Infraestrutura do provedor (Render/Neon) | Recuperação de desastres | Operacional | Cópias podem existir em backups por até 7 dias | Definido na seção 1.1 | Exclusão pontual em backup não suportada; removida por rotação de snapshot após janela de retenção |

> **PENDENTE DE DEFINIÇÃO** (exige decisão jurídica/de negócio): tratamento de dados legados anteriores a este documento.
> (Política de backup definida na seção 1.1; prazos de retenção por categoria definidos acima.)

---

## 1.1 Política de backup e recuperação (LGPD Art. 16)

A exclusão definitiva de dados (`DELETE /api/lgpd/excluir-dados`) remove os registros do **banco de dados ativo** (PostgreSQL gerenciado pela Neon). A operação é irreversível na base transacional.

No entanto, a infraestrutura de hospedagem (Render + Neon) mantém **backups automáticos de recuperação de desastres**, dos quais a gendaz não tem exclusão pontual por titular.

Regras adotadas:

- **Banco ativo:** os dados do titular são removidos imediatamente e de forma irreversível ao acionar a exclusão definitiva.
- **Backups de recuperação (Neon/Render):** cópias automáticas podem reter os dados por até **7 dias** (janela padrão de retenção de snapshot do provedor). Após esse período, os snapshots são rotacionados e descartados.
- **Exclusão de backup pontual:** não há mecanismo de expurgo seletivo em backup por `empresa_id`; a remoção completa ocorre naturalmente pela rotação dos snapshots após a janela de retenção.
- **Acesso a backups:** restrito à infraestrutura do provedor e à equipe técnica da gendaz; não há acesso de terceiros não autorizados.
- **Restauração:** backups destinam-se exclusivamente a recuperação de desastres da plataforma, não à recomposição de dados de um titular que solicitou exclusão.

> **Limitação conhecida:** durante a janela de retenção de backup (até 7 dias), uma cópia dos dados do titular pode existir em snapshot de recuperação. Isso não anula o direito de exclusão, pois a base ativa já está limpa e o snapshot será descartado por rotação. Caso haja exigência contratual de expurgo imediato em todos os meios, a retenção de snapshot do provedor deve ser reduzida ou o provedor substituído.

> **AÇÃO RECOMENDADA:** confirmar na console da Neon a janela real de retenção de backup (padrão pode variar conforme o plano) e ajustar este prazo se necessário.

---

## 2. Mapa de terceiros e transferência internacional

Somente fornecedores confirmados no código-fonte:

| Terceiro | Função | Dados enviados | Origem da chamada | Persistência local | Região/localização | Comportamento no encerramento |
|---|---|---|---|---|---|---|
| **Stripe** | Processamento de pagamentos de planos e assinaturas | Nome e e-mail do cliente pagador, identificadores de plano, referências de pagamento | `StripePaymentGateway` (backend) | `pagamentos_planos`, `stripe_customer_id` em `empresas` | Fora do Brasil (internacional), conforme infraestrutura da Stripe | Assinatura cancelada no encerramento (`cancelarSubscription`); falha registrada com status `FALHA_AO_CANCELAR` |
| **Render** | Hospedagem do backend e da base de dados | Todo o tráfego da aplicação | Infraestrutura | — | Fora do Brasil, conforme infraestrutura da Render | Fora do escopo da aplicação; sujeito à política do provedor |
| **Neon** | PostgreSQL (banco de dados gerenciado) | Dados persistidos da aplicação | Conexão JDBC | — | Fora do Brasil, conforme infraestrutura da Neon | Dados persistem enquanto existirem; exclusão depende da política do provedor |
| **Resend** | Envio de e-mails transacionais | E-mail do destinatário e conteúdo da mensagem | `ResendEmailService` (backend) | Não persiste localmente | Internacional, conforme infraestrutura da Resend | N/A (mensagens já enviadas) |
| **reCAPTCHA (Google)** | Proteção de formulários | Token de verificação e metadados do navegador | `RecaptchaService` (backend) | Não persiste localmente | Internacional | N/A |
| **Groq** | Geração de insights (IA) | Dados agregados da empresa para análise | `GroqClient` (backend) | `insights` (resultado) | Internacional | N/A |

Observações:
- WhatsApp service (`WHATSAPP_SERVICE_URL`) existe apenas como configuração de exemplo no `.env.example`; **não há uso no código backend**.
- CAKTO aparece apenas em migrations e `.env.example` como gateway legado; o provedor ativo é definido por `PAYMENT_PROVIDER` (default `STRIPE`).
- **reCAPTCHA**: `RecaptchaService` e as propriedades `recaptcha.*` existem no backend, mas **não há integração no frontend** (nenhum widget/script `g-recaptcha` carregado). O uso em produção não está comprovado; tratado como configuração inativa.
- A Política de Privacidade (§9) informa os fornecedores internacionais e o mecanismo utilizado (termos/DPA de cada fornecedor; CPCs da ANPD em formalização).
- **STATUS REAL**: os fornecedores (Stripe, Render, Neon, Resend, Groq, Google) foram contratados apenas por conta + API, **sem DPA específico assinado/arquivado** até o momento. O mecanismo legalmente esperado (DPA com CPCs da ANPD ou equivalente) ainda não está formalizado.

### Registro de transferência internacional (ROPA de transferência)

| Fornecedor | País/Sede | Dados transferidos | Mecanismo legal (LGPD Art. 33) | DPA arquivado? |
|---|---|---|---|---|
| **Stripe** | EUA | Nome e e-mail do cliente pagador, identificadores de plano, referências de pagamento | Termos da Stripe + CPCs da ANPD (em formalização) | NÃO |
| **Render** | EUA | Tráfego da aplicação e base de dados | Termos de serviço da Render | NÃO |
| **Neon** | EUA | Dados persistidos da aplicação (PostgreSQL) | Termos de serviço da Neon | NÃO |
| **Resend** | Internacional (EUA) | E-mail do destinatário e conteúdo da mensagem | Termos de serviço da Resend | NÃO |
| **Google reCAPTCHA** | EUA | Token de verificação e metadados do navegador | Política de Privacidade do Google | NÃO |
| **Groq** | EUA | Dados agregados da empresa para análise (IA) | Termos de serviço da Groq | NÃO |

> **Prazo de adequação (Res. ANPD nº 19/2024):** encerrou em 23/08/2025. Os CPCs da ANPD ou DPAs equivalentes **não estão formalizados**; risco de desconformidade mantido por decisão do controlador (item recusado em 27/08/2026). Ação recomendada: baixar/aceitar o DPA padrão de cada fornecedor (painel → Config → Legal) e marcar "DPA arquivado?" como SIM.

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

> O encarregado de proteção de dados (DPO) é designado pelo canal `contato@gendaz.site` (agente de pequeno porte — Resolução ANPD nº 2/2022). O controlador está identificado publicamente; não há designação de nome civil distinto para o DPO, nem CNPJ (ainda inexistente).

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
| `/api/lgpd/exportar` | GET | `DONO` | Exporta dados completos do tenant (portabilidade — ver conteúdo abaixo) |
| `/api/lgpd/excluir-conta` | DELETE | `DONO` | Encerramento lógico da conta + cancelamento Stripe |
| `/api/lgpd/excluir-dados` | DELETE | `DONO` | Exclusão física definitiva (purge por `empresa_id` + auditoria global em `admin_audit`) |

Controles aplicados:
- `ATENDENTE` e "Meu Gendaz": **403** (negado no backend, independente da UI).
- O tenant é derivado da sessão autenticada; **nenhum `empresaId` arbitrário é aceito**.
- Auditoria e mensagens da exportação são consultadas **tenant-scoped** no banco.

### Conteúdo da exportação (`GET /api/lgpd/exportar`)
- Empresa (dados cadastrais do tenant).
- Usuários do tenant (identificação, contato, perfil, status, aceite de Termos/Política — **sem senha/hash**).
- Clientes, serviços, profissionais, agendamentos, conversas e mensagens do tenant.
- Financeiro, pagamentos, pagamentos de plano/assinatura, notas fiscais, entregas e notificações do tenant.
- Chamados/suporte do tenant e auditoria do tenant.

### Explicitamente NÃO exportado
- Senha/hash, cookies, tokens, sessão ativa, OTP, secrets, chaves de API, webhook secrets.
- Dados completos de cartão.
- Dados de outro tenant.
- Logs globais sem relação com o tenant.
